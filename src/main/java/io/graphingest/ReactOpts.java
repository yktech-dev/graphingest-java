package io.graphingest;

import java.util.List;

/**
 * Configuration for {@link React#run(ReactOpts)}.
 */
public class ReactOpts {

    private final String query;
    private final List<ToolDef> tools;
    private final String model;
    private final String systemPrompt;
    private final int maxIterations;
    private final double temperature;

    private ReactOpts(Builder builder) {
        this.query = builder.query;
        this.tools = builder.tools;
        this.model = builder.model;
        this.systemPrompt = builder.systemPrompt;
        this.maxIterations = builder.maxIterations;
        this.temperature = builder.temperature;
    }

    public String getQuery() { return query; }
    public List<ToolDef> getTools() { return tools; }
    public String getModel() { return model; }
    public String getSystemPrompt() { return systemPrompt; }
    public int getMaxIterations() { return maxIterations; }
    public double getTemperature() { return temperature; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String query;
        private List<ToolDef> tools;
        private String model = "standard";
        private String systemPrompt;
        private int maxIterations = 10;
        private double temperature = 0;

        public Builder query(String query) { this.query = query; return this; }
        public Builder tools(List<ToolDef> tools) { this.tools = tools; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public ReactOpts build() { return new ReactOpts(this); }
    }
}
