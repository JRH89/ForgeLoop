package io.forgeloop.control.api;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

/** Converts expected validation failures into safe GraphQL client errors. */
@ControllerAdvice
public class GraphQlExceptionAdvice {
    @GraphQlExceptionHandler
    GraphQLError invalidInput(IllegalArgumentException exception) {
        return GraphqlErrorBuilder.newError()
                .message(exception.getMessage())
                .errorType(ErrorType.BAD_REQUEST)
                .build();
    }
}
