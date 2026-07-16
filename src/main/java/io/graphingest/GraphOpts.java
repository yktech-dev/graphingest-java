package io.graphingest;

import java.util.List;

/**
 * Configuration options for a {@link Graph}.
 */
public class GraphOpts {

    private final String version;
    private final List<String> tags;
    private final int timeoutSeconds;
    private final RetryPolicy retryPolicy;

    private GraphOpts(Builder builder) {
        this.version = builder.version;
        this.tags = builder.tags;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.retryPolicy = builder.retryPolicy;
    }

    public String getVersion() { return version; }
    public List<String> getTags() { return tags; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String version;
        private List<String> tags;
        private int timeoutSeconds;
        private RetryPolicy retryPolicy;

        public Builder version(String version) { this.version = version; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }
        public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }
        public Builder retryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; return this; }
        public GraphOpts build() { return new GraphOpts(this); }
    }
}
