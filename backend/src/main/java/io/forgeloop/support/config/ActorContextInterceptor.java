package io.forgeloop.support.config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Configuration; import org.springframework.graphql.server.*; import reactor.core.publisher.Mono;
@Configuration @ConditionalOnProperty(name="forgeloop.auth.mode",havingValue="header-demo") public class ActorContextInterceptor implements WebGraphQlInterceptor {
 public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) { String actorId=request.getHeaders().getFirst("X-Actor-Id"); request.configureExecutionInput((input,builder)->builder.graphQLContext(context->{ if(actorId!=null) context.put("actorId",actorId); }).build()); return chain.next(request); }
}
