package io.forgeloop.control.api;
import io.forgeloop.control.application.PlatformConfigurationService;import io.forgeloop.control.domain.*;import java.util.*;import org.springframework.graphql.data.method.annotation.*;import org.springframework.stereotype.Controller;
@Controller public class PlatformConfigurationController{
 private final PlatformConfigurationService platform;public PlatformConfigurationController(PlatformConfigurationService platform){this.platform=platform;}
 @QueryMapping public OrganizationPolicy organizationPolicy(){return platform.policy();}@QueryMapping public List<HarnessDefinition> harnessDefinitions(){return platform.harnesses();}@QueryMapping public List<LocalMcpConfiguration> localMcpConfigurations(){return platform.mcpConfigurations();}
 @MutationMapping public OrganizationPolicy configureOrganizationPolicy(@Argument OrganizationPolicyInput input){return platform.configurePolicy(input.maxRunBudgetUsd(),input.maxParallelTasks(),input.allowedProviders(),input.requireHumanApproval(),input.autoMergeEnabled());}
 @MutationMapping public HarnessDefinition createHarnessDefinition(@Argument HarnessDefinitionInput input){return platform.createHarness(input.name(),input.description(),input.allowedRoles(),input.defaultAttemptBudget());}
 @MutationMapping public LocalMcpConfiguration createLocalMcpConfiguration(@Argument LocalMcpConfigurationInput input){return platform.createMcp(input.name(),input.command(),input.arguments(),input.allowedRoles(),input.contextTool(),input.toolArguments());}
 public record OrganizationPolicyInput(double maxRunBudgetUsd,int maxParallelTasks,List<String> allowedProviders,boolean requireHumanApproval,boolean autoMergeEnabled){}public record HarnessDefinitionInput(String name,String description,List<String> allowedRoles,int defaultAttemptBudget){}public record LocalMcpConfigurationInput(String name,String command,List<String> arguments,List<String> allowedRoles,String contextTool,String toolArguments){}
}
