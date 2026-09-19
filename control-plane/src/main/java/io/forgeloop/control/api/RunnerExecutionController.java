package io.forgeloop.control.api;
import io.forgeloop.control.application.LeaseGrant;
import io.forgeloop.control.application.TaskLeaseService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
/** Runner-facing mutations are deliberately separated from operator control-plane mutations. */
@Controller public class RunnerExecutionController { private final TaskLeaseService leases; public RunnerExecutionController(TaskLeaseService leases){this.leases=leases;} @MutationMapping public LeaseGrant claimTaskLease(@Argument String taskId,@Argument String runnerId){return leases.claim(taskId,runnerId);} }
