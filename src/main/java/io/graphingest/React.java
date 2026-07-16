package io.graphingest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * ReAct agent primitives for Java.
 *
 * <pre>{@code
 * ToolDef searchTool = ToolDef.create("search", "Search the web.",
 *     Map.of("query", Map.of("type", "string")),
 *     args -> searchNode.run((String) args.get("query")).toString()
 * );
 *
 * // One-liner agent
 * AgentRunner researcher = React.agent(AgentOpts.builder()
 *     .name("researcher")
 *     .tools(List.of(searchTool))
 *     .model("standard")
 *     .systemPrompt("You are a research assistant.")
 *     .build());
 * String answer = researcher.run("What is quantum computing?");
 *
 * // Or use react() directly
 * ReactResult result = React.run(ReactOpts.builder()
 *     .query("Research fusion energy")
 *     .tools(List.of(searchTool))
 *     .build());
 * }</pre>
 */
public class React {

    private static final Logger LOG = Logger.getLogger(React.class.getName());
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final Map<String, String> PLATFORM_TIERS = Map.of(
            "standard", "gemini-2.5-flash",
            "high", "gemini-2.5-pro"
    );

    /**
     * Run a ReAct loop: LLM reasons → picks tools → tools execute → repeat.
     */
    public static ReactResult run(ReactOpts opts) throws IOException, InterruptedException {
        String model = opts.getModel() != null ? opts.getModel() : "standard";
        int maxIter = opts.getMaxIterations() > 0 ? opts.getMaxIterations() : 10;
        double temperature = opts.getTemperature();

        ProviderInfo provider = getProvider(model);
        List<Map<String, Object>> toolSchemas = buildToolSchemas(opts.getTools());

        // Build tool lookup
        Map<String, ToolDef> toolMap = new LinkedHashMap<>();
        for (ToolDef t : opts.getTools()) {
            toolMap.put(t.getName(), t);
        }

        // Initial messages
        List<Map<String, Object>> messages = new ArrayList<>();
        if (opts.getSystemPrompt() != null && !opts.getSystemPrompt().isEmpty()) {
            messages.add(Map.of("role", "system", "content", opts.getSystemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", opts.getQuery()));

        List<Map<String, Object>> allToolCalls = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int step = 0; step < maxIter; step++) {
            LLMResponse response = chatOpenAI(provider, messages, toolSchemas, temperature);

            // Append assistant message
            appendAssistant(messages, response);

            // No tool calls → done
            if (response.toolCalls.isEmpty()) {
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                return new ReactResult(response.content, allToolCalls, step + 1, elapsed, model);
            }

            // Execute tool calls
            for (ToolCallInfo tc : response.toolCalls) {
                ToolDef tool = toolMap.get(tc.name);
                String toolResult;
                if (tool == null) {
                    toolResult = "Unknown tool: " + tc.name;
                } else {
                    try {
                        toolResult = tool.getFn().apply(tc.arguments);
                    } catch (Exception e) {
                        toolResult = "Error: " + e.getMessage();
                    }
                }

                allToolCalls.add(Map.of("tool", tc.name, "args", tc.arguments, "result", toolResult));
                messages.add(Map.of("role", "tool", "tool_call_id", tc.id, "content", toolResult));
            }
        }

        double elapsed = (System.currentTimeMillis() - start) / 1000.0;
        return new ReactResult("Max iterations (" + maxIter + ") reached without a final answer.", allToolCalls, maxIter, elapsed, model);
    }

    /**
     * Create an agent that combines Graph + ReAct.
     */
    public static AgentRunner agent(AgentOpts opts) {
        return new AgentRunner(opts);
    }

    // --- Internal ---

    private static ProviderInfo getProvider(String model) {
        if (PLATFORM_TIERS.containsKey(model)) {
            String platformUrl = System.getenv("GRAPHINGEST_API_URL");
            String apiKey = System.getenv("GRAPHINGEST_API_KEY");
            if (platformUrl == null || platformUrl.isEmpty()) {
                throw new IllegalStateException("model=\"" + model + "\" requires GRAPHINGEST_API_URL. Run Deploy.run() first or set the env var.");
            }
            return new ProviderInfo(platformUrl + "/llm/v1", apiKey, PLATFORM_TIERS.get(model));
        }

        String lower = model.toLowerCase();
        if (lower.startsWith("gpt-") || lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4")) {
            return new ProviderInfo("https://api.openai.com/v1", System.getenv("OPENAI_API_KEY"), model);
        }
        // Default: OpenAI-compatible
        return new ProviderInfo("https://api.openai.com/v1", System.getenv("OPENAI_API_KEY"), model);
    }

    @SuppressWarnings("unchecked")
    private static LLMResponse chatOpenAI(ProviderInfo provider, List<Map<String, Object>> messages,
                                          List<Map<String, Object>> tools, double temperature)
            throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.resolvedModel);
        body.put("messages", messages);
        body.put("temperature", temperature);
        if (!tools.isEmpty()) body.put("tools", tools);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(provider.baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + provider.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("LLM API error (" + resp.statusCode() + "): " + resp.body());
        }

        Map<String, Object> data = GSON.fromJson(resp.body(), new TypeToken<Map<String, Object>>(){}.getType());
        List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");

        String content = msg.get("content") != null ? msg.get("content").toString() : "";
        List<ToolCallInfo> toolCalls = new ArrayList<>();

        if (msg.containsKey("tool_calls") && msg.get("tool_calls") != null) {
            List<Map<String, Object>> tcs = (List<Map<String, Object>>) msg.get("tool_calls");
            for (Map<String, Object> tc : tcs) {
                Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                Map<String, Object> args = GSON.fromJson(fn.get("arguments").toString(), new TypeToken<Map<String, Object>>(){}.getType());
                toolCalls.add(new ToolCallInfo(tc.get("id").toString(), fn.get("name").toString(), args));
            }
        }

        return new LLMResponse(content, toolCalls);
    }

    private static void appendAssistant(List<Map<String, Object>> messages, LLMResponse response) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        if (response.content != null && !response.content.isEmpty()) msg.put("content", response.content);
        if (!response.toolCalls.isEmpty()) {
            List<Map<String, Object>> tcs = new ArrayList<>();
            for (ToolCallInfo tc : response.toolCalls) {
                tcs.add(Map.of(
                        "id", tc.id,
                        "type", "function",
                        "function", Map.of("name", tc.name, "arguments", GSON.toJson(tc.arguments))
                ));
            }
            msg.put("tool_calls", tcs);
        }
        messages.add(msg);
    }

    private static List<Map<String, Object>> buildToolSchemas(List<ToolDef> tools) {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ToolDef t : tools) {
            Map<String, Object> params = t.getParameters() != null ? t.getParameters() : Map.of("input", Map.of("type", "string"));
            List<String> required = t.getRequired() != null ? t.getRequired() : new ArrayList<>(params.keySet());
            schemas.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", t.getName(),
                            "description", t.getDescription(),
                            "parameters", Map.of("type", "object", "properties", params, "required", required)
                    )
            ));
        }
        return schemas;
    }

    // Inner types
    private record ProviderInfo(String baseUrl, String apiKey, String resolvedModel) {}
    private record LLMResponse(String content, List<ToolCallInfo> toolCalls) {}
    private record ToolCallInfo(String id, String name, Map<String, Object> arguments) {}
}
