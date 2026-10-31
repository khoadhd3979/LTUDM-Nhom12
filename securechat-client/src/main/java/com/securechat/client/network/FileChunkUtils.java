package com.securechat.client.network;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiện ích cắt file thành các chunk phục vụ truyền file qua mạng (Phase 4).
 * Kích thước chunk mặc định 8192 bytes, có thể cấu hình lên 256 KiB theo Protocol v0.3.
 */
public final class FileChunkUtils {
    private FileChunkUtils() {}

    public static final int DEFAULT_CHUNK_SIZE = 8192;
    public static final int MAX_CHUNK_SIZE = 262_144; // 256 KiB theo Protocol v0.3 mục 2

    public static List<byte[]> splitFileIntoChunks(File file) throws IOException {
        return splitFileIntoChunks(file, DEFAULT_CHUNK_SIZE);
    }

    public static List<byte[]> splitFileIntoChunks(File file, int chunkSize) throws IOException {
        // --- ĐOẠN CODE BỔ SUNG THEO MISSION.MD ---
        if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("chunkSize must be in (0, " + MAX_CHUNK_SIZE + "], got: " + chunkSize);
        }

        List<byte[]> chunks = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                if (bytesRead == chunkSize) {
                    chunks.add(buffer.clone());
                } else {
                    byte[] lastChunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, lastChunk, 0, bytesRead);
                    chunks.add(lastChunk);
                }
            }
        }
        return chunks;
    }
}