package io.graphingest;

/**
 * Configuration options for {@link Deploy#run(DeployOpts)}.
 */
public class DeployOpts {

    private final String envPath;
    private final String projectDir;

    private DeployOpts(Builder builder) {
        this.envPath = builder.envPath;
        this.projectDir = builder.projectDir;
    }

    public String getEnvPath() { return envPath; }
    public String getProjectDir() { return projectDir; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String envPath;
        private String projectDir;

        public Builder envPath(String envPath) { this.envPath = envPath; return this; }
        public Builder projectDir(String projectDir) { this.projectDir = projectDir; return this; }
        public DeployOpts build() { return new DeployOpts(this); }
    }
}
