package io.forgeloop.control.api;

import io.forgeloop.control.application.RunnerEventService;
import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.domain.RunnerEvent;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/** Lease-bound event intake; payloads contain progress metadata, never source or prompts. */
@RestController
@RequestMapping("/api/runner/events")
public class RunnerEventController {
    private final RunnerService runners; private final RunnerEventService events;
    public RunnerEventController(RunnerService runners,RunnerEventService events){this.runners=runners;this.events=events;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public RunnerEvent append(@RequestHeader("X-ForgeLoop-Runner-Id")String runnerId,@RequestHeader("X-ForgeLoop-Runner-Credential")String credential,@RequestHeader("X-ForgeLoop-Lease-Id")String leaseId,@RequestHeader("X-ForgeLoop-Lease-Nonce")String nonce,@RequestBody EventInput input){
        runners.authenticated(runnerId,credential);
        return events.append(leaseId,runnerId,nonce,input.sequence(),input.level(),input.eventType(),input.message(),input.occurredAt());
    }
    public record EventInput(long sequence,String level,String eventType,String message,Instant occurredAt){}
}
