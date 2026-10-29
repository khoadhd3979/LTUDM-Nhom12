package network;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientPacketHandler {
    private static final int CHUNK_SIZE = 8192;

    public static List<byte[]> splitFileIntoChunks(File file) throws IOException {
        List<byte[]> chunks = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                if (bytesRead == CHUNK_SIZE) {
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