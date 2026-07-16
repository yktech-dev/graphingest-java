package io.graphingest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A graph orchestrates multiple nodes into a pipeline with retry, timeout, and hooks.
 *
 * <pre>{@code
 * Graph<String, Map<String, Object>> pipeline = Graph.create("etl-pipeline",
 *     GraphOpts.builder().retryPolicy(new RetryPolicy(3, 1.0, 2.0)).timeoutSeconds(300).build(),
 *     url -> {
 *         var data = extract.run(url);
 *         return transform.run(data);
 *     });
 *
 * var result = pipeline.run("https://example.com");
 * }</pre>
 */
public class Graph<In, Out> {

    private static final Logger LOG = Logger.getLogger(Graph.class.getName());

    private final String name;
    private final GraphOpts opts;
    private final Function<In, Out> fn;

    private final int effectiveTimeout;

    private Graph(String name, GraphOpts opts, Function<In, Out> fn) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Graph name is required");
        }
        this.name = name;
        this.opts = opts != null ? opts : GraphOpts.builder().build();
        this.fn = fn;
        this.effectiveTimeout = PlatformLimits.clampGraphTimeout(this.opts.getTimeoutSeconds());
    }

    /**
     * Create a graph with a name and function.
     */
    public static <In, Out> Graph<In, Out> create(String name, Function<In, Out> fn) {
        return new Graph<>(name, null, fn);
    }

    /**
     * Create a graph with a name, options, and function.
     */
    public static <In, Out> Graph<In, Out> create(String name, GraphOpts opts, Function<In, Out> fn) {
        return new Graph<>(name, opts, fn);
    }

    public String getName() { return name; }

    /**
     * Execute the graph with full lifecycle: logging, retries, timeout.
     */
    public Out run(In input) {
        String runId = UUID.randomUUID().toString();
        LOG.info("[graphingest] Starting graph: " + name + " (run=" + runId + ", timeout=" + effectiveTimeout + "s)");
        long start = System.currentTimeMillis();

        int maxAttempts = opts.getRetryPolicy() != null ? opts.getRetryPolicy().getMaxRetries() + 1 : 1;
        Exception lastError = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0) {
                long delay = opts.getRetryPolicy().computeDelayMs(attempt - 1);
                LOG.warning("[graphingest] Graph " + name + ": retry " + attempt + "/" + opts.getRetryPolicy().getMaxRetries() + " after " + delay + "ms");
                try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Out> future = executor.submit(() -> fn.apply(input));
                Out result = future.get(effectiveTimeout, TimeUnit.SECONDS);
                long duration = System.currentTimeMillis() - start;
                LOG.info("[graphingest] Graph " + name + " completed in " + duration + "ms");
                return result;
            } catch (TimeoutException e) {
                throw new RuntimeException("graphingest: Graph " + name + " timed out after " + effectiveTimeout + "s", e);
            } catch (ExecutionException e) {
                lastError = (Exception) e.getCause();
                LOG.severe("[graphingest] Graph " + name + " attempt " + (attempt + 1) + " failed: " + lastError.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("graphingest: Graph " + name + " interrupted", e);
            } finally {
                executor.shutdownNow();
            }
        }

        throw new RuntimeException("graphingest: Graph " + name + " failed after " + maxAttempts + " attempts", lastError);
    }

    /**
     * Execute with a parameter map (for graphs that accept maps).
     */
    @SuppressWarnings("unchecked")
    public Out run(Map<String, Object> parameters) {
        return run((In) parameters);
    }
}
