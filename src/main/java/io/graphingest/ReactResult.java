package io.graphingest;

import java.util.List;
import java.util.Map;

/**
 * Result of a {@link React#run(ReactOpts)} call.
 */
public class ReactResult {

    private final String answer;
    private final List<Map<String, Object>> toolCalls;
    private final int steps;
    private final double elapsedSeconds;
    private final String model;

    public ReactResult(String answer, List<Map<String, Object>> toolCalls, int steps, double elapsedSeconds, String model) {
        this.answer = answer;
        this.toolCalls = toolCalls;
        this.steps = steps;
        this.elapsedSeconds = elapsedSeconds;
        this.model = model;
    }

    public String getAnswer() { return answer; }
    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public int getSteps() { return steps; }
    public double getElapsedSeconds() { return elapsedSeconds; }
    public String getModel() { return model; }
}
