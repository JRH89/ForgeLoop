package io.forgeloop.runner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Short wire identifiers keep provider output independent of punctuation in canonical criteria. */
final class ReviewCriterionIds {
    private ReviewCriterionIds() { }
    static Map<String, String> from(List<String> statements) {
        if (statements == null || statements.isEmpty()
                || statements.stream().anyMatch(value -> value == null || value.isBlank())
                || statements.stream().distinct().count() != statements.size())
            throw new IllegalArgumentException("Review criteria must be nonempty and unique");
        Map<String, String> ids = new LinkedHashMap<>();
        for (int i = 0; i < statements.size(); i++) ids.put("criterion-" + (i + 1), statements.get(i));
        return Collections.unmodifiableMap(ids);
    }
}
