package io.graphingest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Defines a tool that the LLM can call in a ReAct loop.
 *
 * <pre>{@code
 * ToolDef searchTool = ToolDef.create("search", "Search the web.",
 *     Map.of("query", Map.of("type", "string")),
 *     args -> searchNode.run((String) args.get("query")).toString()
 * );
 * }</pre>
 */
public class ToolDef {

    private final String name;
    private final String description;
    private final Map<String, Map<String, Object>> parameters;
    private final List<String> required;
    private final Function<Map<String, Object>, String> fn;

    private ToolDef(String name, String description, Map<String, Map<String, Object>> parameters,
                    List<String> required, Function<Map<String, Object>, String> fn) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.required = required;
        this.fn = fn;
    }

    /**
     * Create a tool definition. All parameters are required by default.
     */
    public static ToolDef create(String name, String description,
                                 Map<String, Map<String, Object>> parameters,
                                 Function<Map<String, Object>, String> fn) {
        return new ToolDef(name, description, parameters, null, fn);
    }

    /**
     * Create a tool definition with explicit required parameters.
     */
    public static ToolDef create(String name, String description,
                                 Map<String, Map<String, Object>> parameters,
                                 List<String> required,
                                 Function<Map<String, Object>, String> fn) {
        return new ToolDef(name, description, parameters, required, fn);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Map<String, Object>> getParameters() { return parameters; }
    public List<String> getRequired() { return required; }
    public Function<Map<String, Object>, String> getFn() { return fn; }
}
