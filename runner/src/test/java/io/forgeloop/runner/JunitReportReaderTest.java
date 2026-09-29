package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JunitReportReaderTest {
    @TempDir Path temp;

    @Test void readsNestedSuitesAndUsesTheNearestSuiteNameWhenClassnameIsMissing() throws Exception {
        Path report = temp.resolve("reports/results.xml");
        Files.createDirectories(report.getParent());
        Files.writeString(report, """
                <testsuites><testsuite name="outer"><testsuite name="inner">
                  <testcase name="added"/><testcase classname="pkg.Service" name="old"><failure/></testcase>
                </testsuite></testsuite></testsuites>
                """);

        TestRunReport parsed = new JunitReportReader().read(temp.resolve("reports"));

        assertEquals(TestRunReport.Status.READ, parsed.status());
        assertEquals(TestRunReport.Outcome.PASSED, parsed.outcomes().get("inner#added"));
        assertEquals(TestRunReport.Outcome.FAILED, parsed.outcomes().get("pkg.Service#old"));
        assertEquals(1, parsed.passed());
        assertEquals(1, parsed.failed());
    }

    @Test void duplicateIdentitiesKeepTheWorstOutcomeAcrossFiles() throws Exception {
        Files.writeString(temp.resolve("one.xml"), "<testsuite name='S'><testcase name='x'/></testsuite>");
        Files.writeString(temp.resolve("two.xml"), "<testsuite name='S'><testcase name='x'><skipped/></testcase></testsuite>");

        TestRunReport parsed = new JunitReportReader().read(temp);

        assertEquals(TestRunReport.Outcome.SKIPPED, parsed.outcomes().get("S#x"));
        assertEquals(1, parsed.skipped());
    }

    @Test void categorizesEveryOutcomeWithErrorAsWorstAndIgnoresRerunChildren() throws Exception {
        Files.writeString(temp.resolve("outcomes.xml"), """
                <testsuite name="S">
                  <testcase name="error"><error/></testcase>
                  <testcase name="failed"><failure/></testcase>
                  <testcase name="skip"><skipped/></testcase>
                  <testcase name="rerun"><flakyFailure/><rerunFailure/></testcase>
                </testsuite>
                """);

        TestRunReport parsed = new JunitReportReader().read(temp);

        assertEquals(TestRunReport.Outcome.ERROR, parsed.outcomes().get("S#error"));
        assertEquals(TestRunReport.Outcome.FAILED, parsed.outcomes().get("S#failed"));
        assertEquals(TestRunReport.Outcome.SKIPPED, parsed.outcomes().get("S#skip"));
        assertEquals(TestRunReport.Outcome.PASSED, parsed.outcomes().get("S#rerun"));
    }

    @Test void refusesDtdAndExternalEntityReports() throws Exception {
        Files.writeString(temp.resolve("unsafe.xml"), """
                <!DOCTYPE testsuite [<!ENTITY x SYSTEM "file:///secret">]>
                <testsuite name="S"><testcase name="&x;"/></testsuite>
                """);

        assertEquals(TestRunReport.Status.UNREADABLE, new JunitReportReader().read(temp).status());
    }

    @Test void skipsSymbolicLinksRatherThanReadingOutsideTheReportDirectory() throws Exception {
        Path outside = Files.createTempFile("outside-report", ".xml");
        Files.writeString(outside, "<testsuite name='S'><testcase name='secret'/></testsuite>");
        Path link = temp.resolve("linked.xml");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            assumeTrue(false, "This Windows account cannot create symbolic links: " + unavailable.getMessage());
        }

        TestRunReport parsed = new JunitReportReader().read(temp);

        assertEquals(TestRunReport.Status.MISSING, parsed.status());
        assertTrue(parsed.outcomes().isEmpty());
    }

    @Test void reportsMissingWhenNoRegularXmlFilesExist() throws Exception {
        assertEquals(TestRunReport.Status.MISSING, new JunitReportReader().read(temp).status());
    }

    @Test void rejectsReportsThatExceedFileCountAndByteBounds() throws Exception {
        JunitReportReader smallBounds = new JunitReportReader(1, 24);
        Files.writeString(temp.resolve("first.xml"), "<testsuite name='S'/>");
        assertEquals(TestRunReport.Status.READ, smallBounds.read(temp).status());
        Files.writeString(temp.resolve("second.xml"), "<testsuite name='T'/>");
        assertEquals(TestRunReport.Status.UNREADABLE, smallBounds.read(temp).status());

        Files.delete(temp.resolve("second.xml"));
        assertEquals(TestRunReport.Status.UNREADABLE,
                new JunitReportReader(10, 8).read(temp).status());
    }

    @Test void reportsTooManyDistinctTestIdentitiesAsUnreadable() throws Exception {
        Files.writeString(temp.resolve("bounded-outcomes.xml"),
                "<testsuite name='Suite'><testcase name='one'/><testcase name='two'/></testsuite>");

        TestRunReport parsed = new JunitReportReader(10, 1024, 1).read(temp);

        assertEquals(TestRunReport.Status.UNREADABLE, parsed.status());
        assertTrue(parsed.outcomes().isEmpty());
    }

    @Test void stripsControlCharactersAndRejectsOversizedIdentities() throws Exception {
        Files.writeString(temp.resolve("identity.xml"), """
                <?xml version="1.1"?>
                <testsuite name="pkg.Serv&#x1;ice"><testcase name="test&#x2;name"/></testsuite>
                """);
        TestRunReport parsed = new JunitReportReader().read(temp);
        assertTrue(parsed.outcomes().containsKey("pkg.Service#testname"));

        Files.writeString(temp.resolve("identity.xml"),
                "<testsuite name='S'><testcase name='" + "x".repeat(500) + "'/></testsuite>");
        assertEquals(TestRunReport.Status.UNREADABLE, new JunitReportReader().read(temp).status());
    }
}
