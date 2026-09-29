package io.forgeloop.control.api;

import io.forgeloop.control.application.RunRecordService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Streams an operator-authorized, tenant-bound run record without buffering the ZIP in memory. */
@RestController
@RequestMapping("/api/runs")
public class RunRecordController {
    private final RunRecordService records;

    public RunRecordController(RunRecordService records) { this.records = records; }

    @GetMapping(value = "/{runId}/record", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> export(@PathVariable String runId) {
        RunRecordService.Export export = records.prepare(runId);
        String safeRunId = runId.replaceAll("[^A-Za-z0-9-]", "_");
        StreamingResponseBody body = output -> records.writeArchive(export, output);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("forgeloop-run-" + safeRunId + ".zip").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }
}
