package io.forgeloop.control.api;

import io.forgeloop.control.domain.DeliveryTask;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Resolves the immutable run-record choice copied onto the task's owning run. */
@Controller
public class TaskRunRecordController {
    @SchemaMapping(typeName = "Task", field = "runRecord")
    public boolean runRecord(DeliveryTask task) {
        return task.getRun().isRunRecordEnabled();
    }
}
