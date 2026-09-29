package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CredentialPathsTest {
    @Test
    void recognizesCredentialNamesAndNestedCredentialLocationsCaseInsensitively() {
        for (String path : new String[]{".env", ".ENV.local", "config/server.pem", "certs/private.KEY", "tokens/key.p12",
                "id_rsa", "id_ed25519.backup", ".npmrc", ".pypirc", ".netrc", ".git-credentials",
                "service/.aws/credentials", "nested/.docker/config.json"}) {
            assertTrue(CredentialPaths.isCredentialFile(path), path);
        }
    }

    @Test
    void examplesAndPublicKeyFilesStayAccessible() {
        for (String path : new String[]{".env.example", "server.key.sample", "secret.pem.template", "id_rsa.pub", "id_ed25519.pub"}) {
            assertFalse(CredentialPaths.isCredentialFile(path), path);
        }
    }
}
