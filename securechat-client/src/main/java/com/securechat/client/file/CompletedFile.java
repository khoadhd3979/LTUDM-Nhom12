package com.securechat.client.file;

/**
 * [M4-C] Kết quả của một transfer FILE_CHUNK đã hoàn tất.
 *
 * Pure Java — không phụ thuộc Swing. Immutable: cả field và byte[] đều defensive-copy.
 *
 * Theo A5: không chứa senderEmail — chỉ senderId, fileName (đã sanitize), và bytes.
 */
public final class CompletedFile {

    private final long senderId;
    private final String fileName;
    private final byte[] data;

    public CompletedFile(long senderId, String fileName, byte[] data) {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("fileName bắt buộc và không được rỗng");
        }
        if (data == null) {
            throw new IllegalArgumentException("data bắt buộc");
        }
        this.senderId = senderId;
        this.fileName = fileName;
        this.data = data.clone();
    }

    public long getSenderId() { return senderId; }
    public String getFileName() { return fileName; }
    public byte[] getData() { return data.clone(); }
}