package io.forgeloop.runner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Strict UTF-8 reader shared by text tools so binary files never become misleading model input. */
final class WorktreeText {
    private WorktreeText() { }

    static String read(Path file, long maxBytes) throws IOException, TooLarge, BinaryFile {
        long size = Files.size(file);
        if (size > maxBytes) throw new TooLarge();
        byte[] bytes = Files.readAllBytes(file);
        for (byte value : bytes) if (value == 0) throw new BinaryFile();
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new BinaryFile();
        }
    }

    static final class TooLarge extends Exception { private TooLarge() { super("Text file exceeds the configured byte limit"); } }
    static final class BinaryFile extends Exception { private BinaryFile() { super("File is not UTF-8 text"); } }
}
