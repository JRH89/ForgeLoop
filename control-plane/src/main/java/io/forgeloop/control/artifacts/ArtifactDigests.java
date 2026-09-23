package io.forgeloop.control.artifacts;

import java.security.MessageDigest;
import java.util.HexFormat;

final class ArtifactDigests {
    private ArtifactDigests() { }
    static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception failure) { throw new IllegalStateException("SHA-256 is unavailable", failure); }
    }
}
