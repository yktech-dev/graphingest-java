# GraphIngest Java SDK

Java SDK for the [GraphIngest Orchestrator](../../README.md) — define pipeline nodes and graphs with typed Java functions.

## Installation

**Maven:**
```xml
<dependency>
    <groupId>io.graphingest</groupId>
    <artifactId>graphingest-sdk</artifactId>
    <version>0.3.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.graphingest:graphingest-sdk:0.3.0'
```

## Quick Start

```java
import io.graphingest.*;
import java.util.Map;

// Define nodes with typed input/output
Node<String, Map<String, Object>> extract = Node.create("extract", url ->
    Map.of("url", url, "rows", 100)
);

Node<Map<String, Object>, Map<String, Object>> transform = Node.create("transform", data -> {
    data.put("cleaned", true);
    return data;
});

// Define a graph with retry policy
Graph<String, Map<String, Object>> pipeline = Graph.create("etl-pipeline",
    GraphOpts.builder()
        .retryPolicy(new RetryPolicy(3, 1.0, 2.0))
        .timeoutSeconds(300)
        .build(),
    url -> {
        var data = extract.run(url);
        return transform.run(data);
    });

// Run it
var result = pipeline.run("https://api.example.com/data");
```

## Deploy

Push your code to the platform:

```java
// Dashboard-only (no local env file):
DeployResult result = Deploy.run();

// With a local .env file:
DeployResult result = Deploy.run(DeployOpts.builder().envPath(".env").build());

// Absolute path:
DeployResult result = Deploy.run(DeployOpts.builder().envPath("/home/me/prod.env").build());
```

Dashboard variables always take precedence over file variables at runtime.

## AI Agent (ReAct)

```java
import io.graphingest.*;
import java.util.List;
import java.util.Map;

// Define tools from your node functions
ToolDef searchTool = ToolDef.create("search", "Search the web.",
    Map.of("query", Map.of("type", "string")),
    args -> {
        var result = search.run((String) args.get("query"));
        return result.toString();
    }
);

// One-liner agent: graph + ReAct loop
AgentRunner researcher = React.agent(AgentOpts.builder()
    .name("researcher")
    .tools(List.of(searchTool))
    .model("standard")  // or "high", "gpt-4o", "claude-3.5-sonnet", etc.
    .systemPrompt("You are a research assistant.")
    .build());

String answer = researcher.run("What is quantum computing?");

// Or use React.run() directly for more control
ReactResult result = React.run(ReactOpts.builder()
    .query("Research fusion energy")
    .tools(List.of(searchTool))
    .model("standard")
    .maxIterations(10)
    .build());

System.out.println(result.getAnswer());
System.out.println(result.getSteps() + " steps, " + result.getElapsedSeconds() + "s");
```

**Model tiers:**

| Model | Description |
|-------|-------------|
| `"standard"` | Fast and cost-effective (default, no API key needed) |
| `"high"` | Premium quality for complex reasoning (no API key needed) |
| `"gpt-4o"` | BYOK: OpenAI (set `OPENAI_API_KEY`) |
| `"claude-3.5-sonnet"` | BYOK: Anthropic (set `ANTHROPIC_API_KEY`) |
| `"gemini-2.5-flash"` | BYOK: Google (set `GOOGLE_API_KEY`) |

## API Reference

### Node

```java
// Create a typed node
Node<String, Map<String, Object>> myNode = Node.create("my-node", input -> {
    return Map.of("result", input);
});

// With cache TTL (seconds)
Node<String, Map<String, Object>> cached = Node.create("cached-node", 3600, input -> {
    return Map.of("result", input);
});

// Run directly
var result = myNode.run("input");

// Fan-out: parallel dispatch
var results = myNode.map(List.of("a", "b", "c"));

// Async dispatch
NodeFuture<Map<String, Object>> future = myNode.arun("input");
var result = future.result();          // block until done
var result = future.result(30_000);    // with timeout (ms)
```

### Graph

```java
Graph<String, Object> myGraph = Graph.create("my-graph",
    GraphOpts.builder()
        .version("1.0")
        .tags(List.of("prod"))
        .timeoutSeconds(600)
        .retryPolicy(new RetryPolicy(3, 1.0, 2.0, 120.0, true))
        .build(),
    input -> {
        var data = extract.run(input);
        return transform.run(data);
    });

var result = myGraph.run("input");
```

## Exports

| Export | Type | Description |
|--------|------|-------------|
| `Node` | class | Create a typed node wrapper |
| `Graph` | class | Create a graph wrapper |
| `Deploy` | class | Push code to platform (supports `envPath`) |
| `React` | class | ReAct loop: LLM + tool routing |
| `AgentRunner` | class | One-liner agent combining Graph + React |
| `NodeFuture` | class | Handle for async node dispatch |
| `ToolDef` | class | Tool definition for LLM |
| `GraphOpts` | class | Graph configuration (builder) |
| `DeployOpts` | class | Deploy configuration (builder) |
| `DeployResult` | class | Deploy response |
| `ReactOpts` | class | React loop configuration (builder) |
| `ReactResult` | class | React loop result |
| `AgentOpts` | class | Agent configuration (builder) |
| `RetryPolicy` | class | Retry configuration |
| `GraphIngestClient` | class | Platform API client |
