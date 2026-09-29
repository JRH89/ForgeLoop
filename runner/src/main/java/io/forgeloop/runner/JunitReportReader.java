package io.forgeloop.runner;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Reads bounded, untrusted JUnit reports without following symbolic links or resolving XML entities. */
public final class JunitReportReader {
    public static final int MAX_REPORT_FILES = 1_000;
    public static final long MAX_REPORT_BYTES = 32L * 1024 * 1024;
    public static final int MAX_IDENTITY_LENGTH = 500;
    public static final int MAX_TEST_IDENTITIES = 50_000;
    private static final int MAX_DIRECTORY_ENTRIES = 100_000;

    private final int maxFiles;
    private final long maxBytes;
    private final int maxOutcomes;

    public JunitReportReader() { this(MAX_REPORT_FILES, MAX_REPORT_BYTES, MAX_TEST_IDENTITIES); }

    JunitReportReader(int maxFiles, long maxBytes) {
        this(maxFiles, maxBytes, MAX_TEST_IDENTITIES);
    }

    JunitReportReader(int maxFiles, long maxBytes, int maxOutcomes) {
        if (maxFiles < 1 || maxBytes < 1 || maxOutcomes < 1) throw new IllegalArgumentException("JUnit report limits must be positive");
        this.maxFiles = maxFiles;
        this.maxBytes = maxBytes;
        this.maxOutcomes = maxOutcomes;
    }

    public TestRunReport read(Path reportDirectory) {
        try {
            if (reportDirectory == null || !Files.isDirectory(reportDirectory, LinkOption.NOFOLLOW_LINKS)) {
                return missing();
            }
            Path root = reportDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            List<Path> files = xmlFiles(root);
            if (files.isEmpty()) return missing();
            if (files.size() > maxFiles) return unreadable();

            long totalBytes = 0;
            for (Path file : files) {
                totalBytes = Math.addExact(totalBytes, Files.size(file));
                if (totalBytes > maxBytes) return unreadable();
            }

            Map<String, TestRunReport.Outcome> outcomes = new TreeMap<>();
            for (Path file : files) parseFile(file, outcomes, maxOutcomes);
            return new TestRunReport(TestRunReport.Status.READ, outcomes);
        } catch (Exception invalidReport) {
            return unreadable();
        }
    }

    private List<Path> xmlFiles(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            var iterator = paths.iterator();
            int visited = 0;
            while (iterator.hasNext()) {
                if (++visited > MAX_DIRECTORY_ENTRIES) throw new IllegalStateException("JUnit report directory has too many entries");
                Path path = iterator.next();
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) continue;
                Path safe = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!safe.startsWith(root) || Files.isSymbolicLink(path)) continue;
                files.add(safe);
                if (files.size() > maxFiles) break;
            }
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private static void parseFile(Path file, Map<String, TestRunReport.Outcome> outcomes, int maxOutcomes) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML entities are not permitted");
        });

        try (InputStream input = Files.newInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                parseEvents(reader, outcomes, maxOutcomes);
            } finally {
                reader.close();
            }
        }
    }

    private static void parseEvents(XMLStreamReader reader, Map<String, TestRunReport.Outcome> outcomes,
                                    int maxOutcomes) throws Exception {
        Deque<String> suiteNames = new ArrayDeque<>();
        TestCaseResult testCase = null;
        boolean rootSeen = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new XMLStreamException("DTD and entity references are not permitted");
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                String element = reader.getLocalName();
                if (!rootSeen) {
                    if (!"testsuite".equals(element) && !"testsuites".equals(element)) {
                        throw new XMLStreamException("JUnit report root element is invalid");
                    }
                    rootSeen = true;
                }
                if ("testsuite".equals(element)) {
                    String suiteName = attribute(reader, "name");
                    suiteNames.push(suiteName == null ? "" : suiteName);
                } else if ("testcase".equals(element)) {
                    if (testCase != null) throw new XMLStreamException("Nested test cases are invalid");
                    String className = attribute(reader, "classname");
                    String testName = attribute(reader, "name");
                    if (className == null || className.isBlank()) {
                        className = suiteNames.stream().filter(name -> !name.isBlank()).findFirst().orElse(null);
                    }
                    testCase = new TestCaseResult(className, testName);
                } else if (testCase != null) {
                    switch (element) {
                        case "error" -> testCase.error = true;
                        case "failure" -> testCase.failure = true;
                        case "skipped" -> testCase.skipped = true;
                        default -> { /* Rerun/flaky extension elements do not alter final test status. */ }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String element = reader.getLocalName();
                if ("testcase".equals(element)) {
                    if (testCase == null) throw new XMLStreamException("Unbalanced test case element");
                    String identity = identity(testCase.className, testCase.name);
                    TestRunReport.Outcome outcome = testCase.outcome();
                    if (!outcomes.containsKey(identity) && outcomes.size() >= maxOutcomes) {
                        throw new XMLStreamException("JUnit report has too many test identities");
                    }
                    outcomes.merge(identity, outcome, TestRunReport.Outcome::worst);
                    testCase = null;
                } else if ("testsuite".equals(element)) {
                    if (suiteNames.isEmpty()) throw new XMLStreamException("Unbalanced test suite element");
                    suiteNames.pop();
                }
            }
        }
        if (!rootSeen || testCase != null || !suiteNames.isEmpty()) throw new XMLStreamException("JUnit report is incomplete");
    }

    private static String attribute(XMLStreamReader reader, String name) {
        return reader.getAttributeValue(null, name);
    }

    private static String identity(String className, String testName) throws XMLStreamException {
        String normalizedClass = stripControls(className);
        String normalizedName = stripControls(testName);
        if (normalizedClass.isBlank() || normalizedName.isBlank()) throw new XMLStreamException("Test identity is incomplete");
        String identity = normalizedClass + "#" + normalizedName;
        if (identity.length() > MAX_IDENTITY_LENGTH) throw new XMLStreamException("Test identity exceeds its bound");
        return identity;
    }

    private static String stripControls(String value) {
        if (value == null) return "";
        StringBuilder cleaned = new StringBuilder(value.length());
        value.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)).forEach(cleaned::appendCodePoint);
        return cleaned.toString().strip();
    }

    private static TestRunReport missing() { return new TestRunReport(TestRunReport.Status.MISSING, Map.of()); }
    private static TestRunReport unreadable() { return new TestRunReport(TestRunReport.Status.UNREADABLE, Map.of()); }

    private static final class TestCaseResult {
        private final String className;
        private final String name;
        private boolean error;
        private boolean failure;
        private boolean skipped;

        private TestCaseResult(String className, String name) {
            this.className = className;
            this.name = name;
        }

        private TestRunReport.Outcome outcome() {
            if (error) return TestRunReport.Outcome.ERROR;
            if (failure) return TestRunReport.Outcome.FAILED;
            if (skipped) return TestRunReport.Outcome.SKIPPED;
            return TestRunReport.Outcome.PASSED;
        }
    }
}
