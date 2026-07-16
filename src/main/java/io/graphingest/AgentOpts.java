package io.graphingest;

import java.util.List;

/**
 * Configuration for {@link React#agent(AgentOpts)}.
 */
public class AgentOpts {

    private final String name;
    private final List<ToolDef> tools;
    private final String model;
    private final String systemPrompt;
    private final int maxIterations;
    private final double temperature;
    private final int timeoutSeconds;
    private final RetryPolicy retryPolicy;
    private final List<String> tags;

    private AgentOpts(Builder builder) {
        this.name = builder.name;
        this.tools = builder.tools;
        this.model = builder.model;
        this.systemPrompt = builder.systemPrompt;
        this.maxIterations = builder.maxIterations;
        this.temperature = builder.temperature;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.retryPolicy = builder.retryPolicy;
        this.tags = builder.tags;
    }

    public String getName() { return name; }
    public List<ToolDef> getTools() { return tools; }
    public String getModel() { return model; }
    public String getSystemPrompt() { return systemPrompt; }
    public int getMaxIterations() { return maxIterations; }
    public double getTemperature() { return temperature; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public List<String> getTags() { return tags; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private List<ToolDef> tools;
        private String model = "standard";
        private String systemPrompt;
        private int maxIterations = 10;
        private double temperature = 0;
        private int timeoutSeconds = 600;
        private RetryPolicy retryPolicy;
        private List<String> tags;

        public Builder name(String name) { this.name = name; return this; }
        public Builder tools(List<ToolDef> tools) { this.tools = tools; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }
        public Builder retryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }
        public AgentOpts build() { return new AgentOpts(this); }
    }
}
