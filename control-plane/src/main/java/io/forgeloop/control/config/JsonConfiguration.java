package io.forgeloop.control.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the mapper used for signed GitHub webhook payload parsing. */
@Configuration
public class JsonConfiguration {
  @Bean
  ObjectMapper githubWebhookObjectMapper() {
    return new ObjectMapper();
  }
}
