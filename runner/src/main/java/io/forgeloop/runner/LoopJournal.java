package io.forgeloop.runner;

import java.io.IOException;
import java.util.Map;

/** Minimal durable write-ahead contract consumed by the loop and its tool gateway. */
public interface LoopJournal {
    void append(String type, Map<String, ?> fields) throws IOException;
}
