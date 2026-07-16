package io.graphingest;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Wraps a ReAct loop inside a Graph for one-liner agent creation.
 *
 * <pre>{@code
 * AgentRunner researcher = React.agent(AgentOpts.builder()
 *     .name("researcher")
 *     .tools(List.of(searchTool))
 *     .model("standard")
 *     .systemPrompt("You are a research assistant.")
 *     .build());
 *
 * String answer = researcher.run("What is quantum computing?");
 * }</pre>
 */
public class AgentRunner {

    private static final Logger LOG = Logger.getLogger(AgentRunner.class.getName());
    private final AgentOpts opts;

    AgentRunner(AgentOpts opts) {
        if (opts.getName() == null || opts.getName().isEmpty()) {
            throw new IllegalArgumentException("AgentOpts.name is required");
        }
        this.opts = opts;
    }

    /**
     * Run the agent with the given query.
     */
    public String run(String query) {
        try {
            ReactResult result = React.run(ReactOpts.builder()
                    .query(query)
                    .tools(opts.getTools())
                    .model(opts.getModel())
                    .systemPrompt(opts.getSystemPrompt())
                    .maxIterations(opts.getMaxIterations())
                    .temperature(opts.getTemperature())
                    .build());

            LOG.info(String.format("[agent:%s] Completed in %.2fs (%d steps, %d tool calls)",
                    opts.getName(), result.getElapsedSeconds(), result.getSteps(), result.getToolCalls().size()));

            return result.getAnswer();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("graphingest: Agent " + opts.getName() + " failed", e);
        }
    }
}
