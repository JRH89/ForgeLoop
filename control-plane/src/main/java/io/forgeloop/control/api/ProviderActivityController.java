package io.forgeloop.control.api;

import io.forgeloop.control.application.ProviderActivityService;
import io.forgeloop.control.domain.ProviderActivity;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ProviderActivityController {
    private final ProviderActivityService activities;

    public ProviderActivityController(ProviderActivityService activities) { this.activities = activities; }

    @QueryMapping
    public List<ProviderActivity> providerActivities(@Argument int days, @Argument String repository) {
        return activities.list(days, repository);
    }
}
