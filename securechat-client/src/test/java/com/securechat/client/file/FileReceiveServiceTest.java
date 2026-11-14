package com.securechat.client.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [M4-C] 13 unit tests cho FileReceiveService (T01..T13).
 *
 * Một số topic được gộp trong cùng test theo chỉ đạo của Muse:
 *  - single-chunk completion: gộp vào T01
 *  - metadata-first-chunk-not-index-0: T02 tự nhiên cover (chunk 2 đến trước)
 *  - clearAll: gộp vào T03
 *  - invalid chunkIndex / totalChunks / senderId: gộp vào T07
 *  - base64 whitespace: gộp vào T09
 *  - senderId isolation: T06
 */
class FileReceiveServiceTest {

    // ---------- helpers ----------

    private static String enc(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    // ==================================================================
    // T01 — happyPath_orderedChunks_completes
    //  Cover: single-chunk completion + multi-chunk ordered.
    // ==================================================================
    @Test
    void happyPath_orderedChunks_completes() {
        // Case A: single chunk (totalChunks = 1)
        FileReceiveService svcSingle = new FileReceiveService();
        Optional<CompletedFile> single = svcSingle.onChunk(
                1L, "s1", "single.txt", 0, 1, enc("HELLO"));
        assertTrue(single.isPresent(), "single-chunk phải hoàn tất ngay");
        assertEquals(1L, single.get().getSenderId());
        assertEquals("single.txt", single.get().getFileName());
        assertArrayEquals("HELLO".getBytes(StandardCharsets.UTF_8), single.get().getData());

        // Case B: multi-chunk ordered 0,1,2
        FileReceiveService svc = new FileReceiveService();
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 0, 3, enc("AAA")).isEmpty());
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 1, 3, enc("BBB")).isEmpty());
        Optional<CompletedFile> res = svc.onChunk(1L, "f1", "a.txt", 2, 3, enc("CCC"));
        assertTrue(res.isPresent(), "multi-chunk phải hoàn tất khi đủ 3 chunk");
        assertEquals("a.txt", res.get().getFileName());
        assertArrayEquals("AAABBBCCC".getBytes(StandardCharsets.UTF_8), res.get().getData());
    }

    // ==================================================================
    // T02 — happyPath_outOfOrderChunks_completes
    //  Cover: out-of-order; metadata lấy từ chunk đầu tiên (không phải index 0).
    // ==================================================================
    @Test
    void happyPath_outOfOrderChunks_completes() {
        FileReceiveService svc = new FileReceiveService();
        // Chunk index 2 đến trước — metadata (fileName, totalChunks) lấy từ đây.
        assertTrue(svc.onChunk(1L, "f1", "from-chunk2.txt", 2, 3, enc("CCC")).isEmpty());
        assertTrue(svc.onChunk(1L, "f1", "from-chunk2.txt", 0, 3, enc("AAA")).isEmpty());
        Optional<CompletedFile> res = svc.onChunk(1L, "f1", "from-chunk2.txt", 1, 3, enc("BBB"));
        assertTrue(res.isPresent());
        assertEquals("from-chunk2.txt", res.get().getFileName());
        assertArrayEquals("AAABBBCCC".getBytes(StandardCharsets.UTF_8), res.get().getData());
    }

    // ==================================================================
    // T03 — missingChunk_notCompleted (+ clearAll)
    //  Cover: incomplete transfer, clearAll behavior.
    // ==================================================================
    @Test
    void missingChunk_notCompleted_andClearAllWipesState() {
        FileReceiveService svc = new FileReceiveService();
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 0, 3, enc("A")).isEmpty());
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 2, 3, enc("C")).isEmpty());
        // Thiếu index 1 → chưa hoàn tất
        // clearAll → state bị xóa
        svc.clearAll();
        // Gửi lại index 1 → transfer MỚI, không ghép với 0,2 cũ → empty
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 1, 3, enc("B")).isEmpty());
        // Verify: transfer hiện tại chỉ có 1 chunk (index 1); gửi 0 → 2 chunks; thiếu 2
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 0, 3, enc("A")).isEmpty());
        // Gửi 2 → đủ 0,1,2 → complete
        Optional<CompletedFile> res = svc.onChunk(1L, "f1", "a.txt", 2, 3, enc("C"));
        assertTrue(res.isPresent());
        assertArrayEquals("ABC".getBytes(StandardCharsets.UTF_8), res.get().getData());
    }

    // ==================================================================
    // T04 — duplicateIndex_firstWins
    // ==================================================================
    @Test
    void duplicateIndex_firstWins() {
        FileReceiveService svc = new FileReceiveService();
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 0, 2, enc("FIRST")).isEmpty());
        // Duplicate index 0 với bytes khác → phải bị drop, giữ bản đầu
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 0, 2, enc("SECOND")).isEmpty());
        Optional<CompletedFile> res = svc.onChunk(1L, "f1", "a.txt", 1, 2, enc("END"));
        assertTrue(res.isPresent());
        assertArrayEquals("FIRSTEND".getBytes(StandardCharsets.UTF_8), res.get().getData());
    }

    // ==================================================================
    // T05 — metadataMismatch_discardsTransfer
    // ==================================================================
    @Test
    void metadataMismatch_discardsTransfer() {
        FileReceiveService svc = new FileReceiveService();
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 0, 3, enc("A")).isEmpty());
        // fileName mismatch → hủy toàn bộ transfer
        assertTrue(svc.onChunk(1L, "f1", "b.txt", 1, 3, enc("B")).isEmpty());
        // Transfer mới tạo lại từ chunk 1
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 1, 3, enc("B")).isEmpty());
        // Gửi 0 → chỉ có {0,1}, thiếu 2 → empty
        assertTrue(svc.onChunk(1L, "f1", "a.txt", 0, 3, enc("A")).isEmpty());
        // Gửi 2 → complete
        Optional<CompletedFile> res = svc.onChunk(1L, "f1", "a.txt", 2, 3, enc("C"));
        assertTrue(res.isPresent());
        assertEquals("a.txt", res.get().getFileName());
        assertArrayEquals("ABC".getBytes(StandardCharsets.UTF_8), res.get().getData());
    }

    // ==================================================================
    // T06 — twoSendersSameFileId_isolated
    // ==================================================================
    @Test
    void twoSendersSameFileId_isolated() {
        FileReceiveService svc = new FileReceiveService();
        assertTrue(svc.onChunk(1L, "F", "f.txt", 0, 2, enc("s1c0")).isEmpty());
        assertTrue(svc.onChunk(2L, "F", "f.txt", 0, 2, enc("s2c0")).isEmpty());
        Optional<CompletedFile> r1 = svc.onChunk(1L, "F", "f.txt", 1, 2, enc("s1c1"));
        Optional<CompletedFile> r2 = svc.onChunk(2L, "F", "f.txt", 1, 2, enc("s2c1"));
        assertTrue(r1.isPresent());
        assertTrue(r2.isPresent());
        assertEquals(1L, r1.get().getSenderId());
        assertEquals(2L, r2.get().getSenderId());
        assertArrayEquals("s1c0s1c1".getBytes(StandardCharsets.UTF_8), r1.get().getData());
        assertArrayEquals("s2c0s2c1".getBytes(StandardCharsets.UTF_8), r2.get().getData());
    }

    // ==================================================================
    // T07 — chunkDecodedOversized_andInvalidFields_dropped
    //  Cover: oversize decoded chunk + invalid chunkIndex/totalChunks/senderId
    //         (theo làm rõ của Muse — gộp trong test limits, không tạo test riêng).
    // ==================================================================
    @Test
    void chunkDecodedOversized_andInvalidFields_dropped() {
        FileReceiveService svc = new FileReceiveService();

        // (1) decoded chunk 262_145 bytes > 262_144 → drop
        byte[] big = new byte[262_145];
        assertTrue(svc.onChunk(1L, "f_big", "a", 0, 1, enc(big)).isEmpty());

        // (2) totalChunks ngoài [1, 128] → drop
        assertTrue(svc.onChunk(1L, "f_t0",  "a", 0, 0,   enc("x")).isEmpty());
        assertTrue(svc.onChunk(1L, "f_t129","a", 0, 129, enc("x")).isEmpty());
        assertTrue(svc.onChunk(1L, "f_tn",  "a", 0, -1,  enc("x")).isEmpty());

        // (3) chunkIndex ngoài [0, totalChunks) → drop
        assertTrue(svc.onChunk(1L, "f_cn", "a", -1, 3, enc("x")).isEmpty());
        assertTrue(svc.onChunk(1L, "f_ce", "a",  3, 3, enc("x")).isEmpty());

        // (4) senderId <= 0 → drop
        assertTrue(svc.onChunk(0L,  "f_s0", "a", 0, 2, enc("x")).isEmpty());
        assertTrue(svc.onChunk(-5L, "f_sn", "a", 0, 2, enc("x")).isEmpty());

        // (5) Verify các invalid chunk ở trên KHÔNG tạo transfer rỗng:
        //     gửi tiếp một chunk hợp lệ cho cùng fileId+totalChunks sẽ bắt đầu
        //     transfer MỚI (tức là state cũ không tồn tại) và chưa complete.
        assertTrue(svc.onChunk(1L, "f_s0", "a", 1, 2, enc("y")).isEmpty());
    }

    // ==================================================================
    // T08 — totalFileOversized_discardsTransfer
    // ==================================================================
    @Test
    void totalFileOversized_discardsTransfer() {
        FileReceiveService svc = new FileReceiveService();
        byte[] c = new byte[262_144];   // 256 KiB
        String enc = enc(c);
        // 4 chunks = 1_048_576 bytes = đúng MAX
        assertTrue(svc.onChunk(1L, "f1", "a", 0, 5, enc).isEmpty());
        assertTrue(svc.onChunk(1L, "f1", "a", 1, 5, enc).isEmpty());
        assertTrue(svc.onChunk(1L, "f1", "a", 2, 5, enc).isEmpty());
        assertTrue(svc.onChunk(1L, "f1", "a", 3, 5, enc).isEmpty());   // 1_048_576 = MAX
        // Chunk 5 → vượt 1 MiB → hủy transfer
        assertTrue(svc.onChunk(1L, "f1", "a", 4, 5, enc).isEmpty());
        // Verify transfer đã hủy: gửi index 0 → transfer mới (không complete)
        assertTrue(svc.onChunk(1L, "f1", "a", 0, 5, enc).isEmpty());
        // Verify transfer mới hiện chỉ có 1 chunk → gửi index 1 vẫn chưa complete
        assertTrue(svc.onChunk(1L, "f1", "a", 1, 5, enc).isEmpty());
    }

    // ==================================================================
    // T09 — invalidBase64_dropped
    //  Cover: malformed chars + whitespace (Basic decoder reject whitespace theo A4).
    // ==================================================================
    @Test
    void invalidBase64_dropped() {
        FileReceiveService svc = new FileReceiveService();
        assertTrue(svc.onChunk(1L, "f1", "a", 0, 1, "not!base64@@").isEmpty());   // ký tự ngoài alphabet
        assertTrue(svc.onChunk(1L, "f2", "a", 0, 1, "QUJD\nREVG").isEmpty());     // \n
        assertTrue(svc.onChunk(1L, "f3", "a", 0, 1, "QUJD REVG").isEmpty());      // space
        assertTrue(svc.onChunk(1L, "f4", "a", 0, 1, "QUJD\tREVG").isEmpty());     // \t
        assertTrue(svc.onChunk(1L, "f5", "a", 0, 1, "QUJD\rREVG").isEmpty());     // \r
    }

    // ==================================================================
    // T10 — base64PreCheckLengthExceeded_dropped
    // ==================================================================
    @Test
    void base64PreCheckLengthExceeded_dropped() {
        FileReceiveService svc = new FileReceiveService();
        // 349_529 > MAX_BASE64_LENGTH (349_528) → drop trước khi decode
        char[] big = new char[349_529];
        Arrays.fill(big, 'A');
        String bigStr = new String(big);
        assertTrue(svc.onChunk(1L, "f1", "a", 0, 1, bigStr).isEmpty());

        // Boundary: 349_528 = MAX, decode OK thành 262_144 bytes → không drop vì length
        String boundaryStr = enc(new byte[262_144]);
        assertEquals(349_528, boundaryStr.length());
        Optional<CompletedFile> res = svc.onChunk(1L, "f_boundary", "a", 0, 1, boundaryStr);
        assertTrue(res.isPresent(), "349_528 chars phải decode OK thành 262_144 bytes");
        assertEquals(262_144, res.get().getData().length);
    }

    // ==================================================================
    // T11 — concurrentLimit_rejectsNewTransfer
    // ==================================================================
    @Test
    void concurrentLimit_rejectsNewTransfer() {
        FileReceiveService svc = new FileReceiveService(60_000, 2);
        // Tạo 2 transfer
        assertTrue(svc.onChunk(1L, "f1", "a", 0, 2, enc("x")).isEmpty());
        assertTrue(svc.onChunk(1L, "f2", "a", 0, 2, enc("x")).isEmpty());
        // Transfer thứ 3 → reject (không tạo)
        assertTrue(svc.onChunk(1L, "f3", "a", 0, 2, enc("x")).isEmpty());
        // Verify f3 không có state → gửi index 1 của f3 → empty (không complete)
        assertTrue(svc.onChunk(1L, "f3", "a", 1, 2, enc("y")).isEmpty());
        // Complete f1 → giải phóng 1 slot
        assertTrue(svc.onChunk(1L, "f1", "a", 1, 2, enc("y")).isPresent());
        // Giờ f3 được tạo
        assertTrue(svc.onChunk(1L, "f3", "a", 0, 2, enc("x")).isEmpty());
        Optional<CompletedFile> r = svc.onChunk(1L, "f3", "a", 1, 2, enc("y"));
        assertTrue(r.isPresent());
        assertArrayEquals("xy".getBytes(StandardCharsets.UTF_8), r.get().getData());
    }

    // ==================================================================
    // T12 — ttlExpired_transferEvicted
    // ==================================================================
    @Test
    void ttlExpired_transferEvicted() throws Exception {
        FileReceiveService svc = new FileReceiveService(100, 8);
        assertTrue(svc.onChunk(1L, "f1", "a", 0, 3, enc("A")).isEmpty());
        // Sleep > TTL → transfer cũ bị evict khi onChunk lần sau
        Thread.sleep(150);
        // Gửi index 1: transfer cũ bị evict trước → transfer MỚI chỉ có {1}
        assertTrue(svc.onChunk(1L, "f1", "a", 1, 3, enc("B")).isEmpty());
        // Verify transfer mới: gửi 0 → {0,1}; thiếu 2
        assertTrue(svc.onChunk(1L, "f1", "a", 0, 3, enc("X")).isEmpty());
        // Gửi 2 → complete
        Optional<CompletedFile> res = svc.onChunk(1L, "f1", "a", 2, 3, enc("Y"));
        assertTrue(res.isPresent());
        assertArrayEquals("XBY".getBytes(StandardCharsets.UTF_8), res.get().getData());
    }

    // ==================================================================
    // T13 — ttlRefresh_extendsLife
    // ==================================================================
    @Test
    void ttlRefresh_activeChunkExtendsLife() throws Exception {
        FileReceiveService svc = new FileReceiveService(150, 8);
        assertTrue(svc.onChunk(1L, "f1", "a", 0, 3, enc("A")).isEmpty());   // t≈0
        Thread.sleep(100);
        assertTrue(svc.onChunk(1L, "f1", "a", 1, 3, enc("B")).isEmpty());   // t≈100, refresh lastActivity
        Thread.sleep(100);   // t≈200, diff so với lastActivity≈100 → 100 < 150 → chưa evict
        Optional<CompletedFile> res = svc.onChunk(1L, "f1", "a", 2, 3, enc("C"));
        assertTrue(res.isPresent(), "Transfer phải còn sống nhờ refresh từ chunk trước");
        assertArrayEquals("ABC".getBytes(StandardCharsets.UTF_8), res.get().getData());
    }
}