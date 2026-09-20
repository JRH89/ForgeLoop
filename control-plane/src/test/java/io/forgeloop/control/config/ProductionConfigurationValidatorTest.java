package io.forgeloop.control.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class ProductionConfigurationValidatorTest {
    @Test void rejectsMissingProductionRequirements() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfigurationValidator("production", "", "", "jdbc:h2:mem:test", "update").validate());
    }
    @Test void acceptsSafeProductionRequirements() {
        assertDoesNotThrow(() -> new ProductionConfigurationValidator("production", "https://issuer.example", "secret", "jdbc:postgresql://db/forgeloop", "validate").validate());
    }
}
