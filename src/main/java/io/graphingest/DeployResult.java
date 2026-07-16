package io.graphingest;

import java.util.List;

/**
 * Response from a successful {@link Deploy#run()} call.
 */
public class DeployResult {

    private List<String> functions;
    private List<String> dashboard_env_vars;

    public List<String> getFunctions() { return functions; }
    public List<String> getDashboardEnvVars() { return dashboard_env_vars; }
}
