package io.forgeloop.runner;

import java.util.Locale;

/** Fixed, case-insensitive deny list for credential-bearing file paths. */
public final class CredentialPaths {
    private CredentialPaths() { }

    public static boolean isCredentialFile(String repositoryPath) {
        if (repositoryPath == null || repositoryPath.isBlank() || repositoryPath.indexOf('\\') >= 0) return false;
        String path = repositoryPath.toLowerCase(Locale.ROOT);
        String[] parts = path.split("/", -1);
        if (java.util.Arrays.stream(parts).anyMatch(part -> part.isBlank() || part.equals(".") || part.equals(".."))) return false;
        String name = parts[parts.length - 1];
        if (isExample(name)) return false;
        if (name.equals(".env") || name.startsWith(".env.")) return true;
        if (java.util.List.of(".npmrc", ".pypirc", ".netrc", ".git-credentials").contains(name)) return true;
        if (name.matches("id_(rsa|dsa|ecdsa|ed25519)(\\..*)?") && !name.endsWith(".pub")) return true;
        if (java.util.List.of(".pem", ".key", ".p12", ".pfx", ".jks", ".keystore").stream().anyMatch(name::endsWith)) return true;
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].equals(".aws") && index + 1 < parts.length && parts[index + 1].equals("credentials")) return true;
            if (parts[index].equals(".docker") && index + 1 < parts.length && parts[index + 1].equals("config.json")) return true;
        }
        return false;
    }

    private static boolean isExample(String name) {
        return java.util.List.of(".example", ".sample", ".template", ".dist").stream().anyMatch(name::endsWith);
    }
}
