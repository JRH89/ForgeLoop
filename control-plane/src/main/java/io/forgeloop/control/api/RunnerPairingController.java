package io.forgeloop.control.api;

import io.forgeloop.control.application.*;
import org.springframework.stereotype.Controller;
import org.springframework.graphql.data.method.annotation.*;

@Controller
public class RunnerPairingController {
    private final RunnerPairingService service;
    public RunnerPairingController(RunnerPairingService service) { this.service=service; }
    @MutationMapping public boolean approveRunnerPairing(@Argument String challenge,@Argument String name) { return service.approve(challenge,name); }
    @MutationMapping public RunnerEnrollment exchangeRunnerPairing(@Argument String verifier) { return service.exchange(verifier); }
}
