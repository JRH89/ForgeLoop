package io.forgeloop.control.integrations.github;
/** Short-lived, lease-bound read credential for one configured repository. */
public record GithubCheckoutGrant(String repository,String baseBranch,String token){}
