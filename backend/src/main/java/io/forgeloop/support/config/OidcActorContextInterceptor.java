package io.forgeloop.support.config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Configuration; import org.springframework.graphql.server.*; import org.springframework.security.core.context.SecurityContextHolder; import reactor.core.publisher.Mono;
@Configuration @ConditionalOnProperty(name="forgeloop.auth.mode",havingValue="oidc",matchIfMissing=true) public class OidcActorContextInterceptor implements WebGraphQlInterceptor {
 public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) { String actorId=SecurityContextHolder.getContext().getAuthentication().getName(); request.configureExecutionInput((input,builder)->builder.graphQLContext(context->context.put("actorId",actorId)).build()); return chain.next(request); }
}
