package io.forgeloop.runner;

import java.time.Duration;

@FunctionalInterface
public interface ToolSleeper {
    void sleep(Duration duration) throws InterruptedException;
}
