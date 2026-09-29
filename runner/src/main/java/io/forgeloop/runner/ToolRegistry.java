package io.forgeloop.runner;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed capability registry; the model cannot register tools from prompt or repository text. */
public final class ToolRegistry {
    private final Map<String, LoopTool> tools;

    public ToolRegistry(Collection<? extends LoopTool> registeredTools) {
        Map<String, LoopTool> indexed = new LinkedHashMap<>();
        if (registeredTools == null) throw new IllegalArgumentException("Tool registry is required");
        for (LoopTool tool : registeredTools) {
            if (tool == null || indexed.putIfAbsent(tool.spec().name(), tool) != null)
                throw new IllegalArgumentException("Tool registry contains a duplicate or null tool");
        }
        tools = Map.copyOf(indexed);
    }

    public static ToolRegistry standard(GitWorktreeManager git, RunGateExecutor gates) {
        return new ToolRegistry(List.of(new ListFilesTool(), new ReadFileTool(), new SearchFilesTool(),
                new WriteFileTool(), new EditFileTool(), new RunGateTool(gates), new FinishTool(git)));
    }

    LoopTool find(String name) { return tools.get(name); }

    public List<ToolSpec> specifications(Set<String> declaredNames) {
        if (declaredNames == null) throw new IllegalArgumentException("Declared tools are required");
        return declaredNames.stream().sorted().map(name -> {
            LoopTool tool = tools.get(name);
            if (tool == null) throw new IllegalArgumentException("Declared tool is not registered");
            return tool.spec();
        }).toList();
    }
}
