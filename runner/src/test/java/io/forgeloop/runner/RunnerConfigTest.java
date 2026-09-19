package io.forgeloop.runner;
import static org.junit.jupiter.api.Assertions.*; import java.net.URI; import java.util.List; import org.junit.jupiter.api.Test;
class RunnerConfigTest { @Test void rejectsMissingRegistrationMaterial(){assertThrows(IllegalArgumentException.class,()->new RunnerConfig(URI.create("http://localhost:8090"),"","node","1",List.of("git")));} @Test void acceptsRunnerManagedCapabilities(){RunnerConfig config=new RunnerConfig(URI.create("http://localhost:8090"),"token","node","1",List.of("git","docker"));assertEquals(List.of("git","docker"),config.capabilities());} }
