package com.securechat.client.file;

import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * [M4-C] Nhận, ghép, và giới hạn tài nguyên cho packet FILE_CHUNK relay từ Server M4-B.
 *
 * Nguyên tắc (RC-1..RC-8, C-1..C-6):
 *  - Transfer key: (senderId, fileId) — hai sender cùng fileId là hai transfer độc lập.
 *  - Metadata fileName/totalChunks lấy từ chunk hợp lệ ĐẦU TIÊN đến service
 *    (không mặc định chunkIndex=0). Metadata mismatch → hủy transfer.
 *  - Duplicate chunkIndex: giữ bản đầu, drop bản sau.
 *  - Limits: totalChunks ∈ [1,128]; chunk decoded ≤ 262144 bytes; tổng file ≤ 1 MiB;
 *    tối đa 8 transfer chưa hoàn tất đồng thời; Base64 pre-check length ≤ 349528.
 *  - TTL 60s lazy expiration (không timer thread), configurable qua constructor.
 *  - Base64 dùng java.util.Base64.getDecoder() (basic) — reject whitespace + ký tự
 *    ngoài alphabet, ném IllegalArgumentException → drop chunk.
 *
 * Pure Java — không phụ thuộc Swing. Thread-safe: mọi method public đều synchronized.
 */
public final class FileReceiveService {

    public static final int DEFAULT_TTL_MILLIS = 60_000;
    public static final int DEFAULT_MAX_CONCURRENT_TRANSFERS = 8;
    public static final int MAX_TOTAL_CHUNKS = 128;
    public static final int MAX_CHUNK_DECODED_BYTES = 262_144;
    public static final int MAX_FILE_TOTAL_BYTES = 1_048_576;
    public static final int MAX_BASE64_LENGTH = 349_528;
    public static final String DEFAULT_SAFE_FILENAME = "received-file";

    private final long ttlMillis;
    private final int maxConcurrentTransfers;
    private final Map<TransferKey, TransferState> transfers = new HashMap<>();

    public FileReceiveService() {
        this(DEFAULT_TTL_MILLIS, DEFAULT_MAX_CONCURRENT_TRANSFERS);
    }

    public FileReceiveService(long ttlMillis, int maxConcurrentTransfers) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis phải > 0, nhận: " + ttlMillis);
        }
        if (maxConcurrentTransfers <= 0) {
            throw new IllegalArgumentException("maxConcurrentTransfers phải > 0, nhận: "
                    + maxConcurrentTransfers);
        }
        this.ttlMillis = ttlMillis;
        this.maxConcurrentTransfers = maxConcurrentTransfers;
    }

    /**
     * Xử lý một chunk. Trả Optional.empty() khi:
     *  - Bất kỳ field nào invalid / vi phạm limits → drop chunk.
     *  - Chunk hợp lệ nhưng transfer chưa đủ index [0, totalChunks).
     *  - Transfer bị hủy do metadata mismatch hoặc vượt tổng 1 MiB.
     *  - Transfer mới bị reject do đạt max concurrent.
     * Trả Optional<CompletedFile> đúng MỘT lần khi chunk vừa hoàn tất đủ index.
     */
    public synchronized Optional<CompletedFile> onChunk(
            long senderId, String fileId, String fileName,
            int chunkIndex, int totalChunks, String dataBase64) {

        // ---- Defensive field checks (A5/A6) ----
        if (senderId <= 0) return Optional.empty();
        if (fileId == null || fileId.isBlank()) return Optional.empty();
        if (fileName == null || fileName.isBlank()) return Optional.empty();
        if (dataBase64 == null || dataBase64.isEmpty()) return Optional.empty();

        // ---- Limits (RC-5 / C-3) ----
        if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS) return Optional.empty();
        if (chunkIndex < 0 || chunkIndex >= totalChunks) return Optional.empty();

        // Base64 pre-check length trước khi decode
        if (dataBase64.length() > MAX_BASE64_LENGTH) return Optional.empty();

        // Decode (basic decoder — reject whitespace và ký tự ngoài alphabet)
        byte[] chunkBytes;
        try {
            chunkBytes = Base64.getDecoder().decode(dataBase64);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (chunkBytes.length < 1 || chunkBytes.length > MAX_CHUNK_DECODED_BYTES) {
            return Optional.empty();
        }

        // ---- Lazy expiry (C-2) ----
        long now = System.currentTimeMillis();
        evictExpiredLocked(now);

        TransferKey key = new TransferKey(senderId, fileId);
        TransferState state = transfers.get(key);
        String sanitizedName = sanitizeFileName(fileName);

        if (state == null) {
            // Tạo transfer mới — check max concurrent
            if (transfers.size() >= maxConcurrentTransfers) {
                return Optional.empty();
            }
            state = new TransferState(sanitizedName, totalChunks, now);
            state.chunks.put(chunkIndex, chunkBytes);
            state.totalBytes = chunkBytes.length;
            transfers.put(key, state);
        } else {
            // Transfer đã tồn tại
            if (!state.fileName.equals(sanitizedName) || state.totalChunks != totalChunks) {
                // Metadata mismatch → hủy toàn bộ transfer
                transfers.remove(key);
                return Optional.empty();
            }
            // Duplicate chunkIndex → giữ bản đầu
            if (state.chunks.containsKey(chunkIndex)) {
                return Optional.empty();
            }
            // Tổng file vượt 1 MiB → hủy transfer
            if ((long) state.totalBytes + chunkBytes.length > MAX_FILE_TOTAL_BYTES) {
                transfers.remove(key);
                return Optional.empty();
            }
            state.chunks.put(chunkIndex, chunkBytes);
            state.totalBytes += chunkBytes.length;
            state.lastActivity = now;
        }

        // ---- Completion (RC-6) ----
        if (state.chunks.size() == state.totalChunks) {
            byte[] data = new byte[(int) state.totalBytes];
            int offset = 0;
            for (int i = 0; i < state.totalChunks; i++) {
                byte[] c = state.chunks.get(i);
                if (c == null) {
                    // Không nên xảy ra: size == totalChunks đảm bảo đủ index.
                    transfers.remove(key);
                    return Optional.empty();
                }
                System.arraycopy(c, 0, data, offset, c.length);
                offset += c.length;
            }
            transfers.remove(key);
            return Optional.of(new CompletedFile(senderId, state.fileName, data));
        }
        return Optional.empty();
    }

    /** Lazy-expire: transfer không có chunk hợp lệ mới trong ttlMillis bị xóa. */
    public synchronized void evictExpired(long nowMillis) {
        evictExpiredLocked(nowMillis);
    }

    /** Xóa toàn bộ transfer chưa hoàn tất (dùng cho logout/disconnect theo C-4). */
    public synchronized void clearAll() {
        transfers.clear();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void evictExpiredLocked(long nowMillis) {
        Iterator<Map.Entry<TransferKey, TransferState>> it = transfers.entrySet().iterator();
        while (it.hasNext()) {
            if (nowMillis - it.next().getValue().lastActivity > ttlMillis) {
                it.remove();
            }
        }
    }

    /**
     * Chuẩn hóa tên file theo C-5:
     *  - Bỏ path component trước dấu '/' hoặc '\' cuối cùng.
     *  - Thay các ký tự \ / : * ? " < > | và control chars bằng '_'.
     *  - Nếu rỗng, dùng "received-file".
     */
    private static String sanitizeFileName(String raw) {
        if (raw == null) return DEFAULT_SAFE_FILENAME;
        int idx = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        String name = (idx >= 0) ? raw.substring(idx + 1) : raw;
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                sb.append('_');
                continue;
            }
            switch (c) {
                case '\\': case '/': case ':': case '*': case '?':
                case '"': case '<': case '>': case '|':
                    sb.append('_');
                    break;
                default:
                    sb.append(c);
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? DEFAULT_SAFE_FILENAME : result;
    }

    private record TransferKey(long senderId, String fileId) {}

    private static final class TransferState {
        final String fileName;
        final int totalChunks;
        final Map<Integer, byte[]> chunks = new HashMap<>();
        long totalBytes = 0;
        long lastActivity;

        TransferState(String fileName, int totalChunks, long now) {
            this.fileName = fileName;
            this.totalChunks = totalChunks;
            this.lastActivity = now;
        }
    }
}