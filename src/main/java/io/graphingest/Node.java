package io.graphingest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A node is a single unit of work that runs on managed infrastructure.
 *
 * <pre>{@code
 * Node<String, Map<String, Object>> extract = Node.create("extract", url -> {
 *     return Map.of("url", url, "rows", 100);
 * });
 *
 * // Sync execution
 * var result = extract.run("https://example.com");
 *
 * // Parallel fan-out
 * var results = extract.map(List.of("url1", "url2", "url3"));
 * }</pre>
 */
public class Node<In, Out> {

    private static final Logger LOG = Logger.getLogger(Node.class.getName());

    private final String name;
    private final int cacheTtl;
    private final int timeoutSeconds;
    private final Function<In, Out> fn;

    private Node(String name, int cacheTtl, int timeoutSeconds, Function<In, Out> fn) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Node name is required");
        }
        this.name = name;
        this.cacheTtl = cacheTtl;
        this.timeoutSeconds = PlatformLimits.clampNodeTimeout(timeoutSeconds);
        this.fn = fn;
    }

    /**
     * Create a node with a name and function.
     */
    public static <In, Out> Node<In, Out> create(String name, Function<In, Out> fn) {
        return new Node<>(name, 0, 0, fn);
    }

    /**
     * Create a node with a name, cache TTL, and function.
     */
    public static <In, Out> Node<In, Out> create(String name, int cacheTtl, Function<In, Out> fn) {
        return new Node<>(name, cacheTtl, 0, fn);
    }

    /**
     * Create a node with a name, cache TTL, timeout, and function.
     * Timeout defaults to tier limit (Free: 5min, Pro: 10min, Enterprise: 60min).
     * Clamped to tier max (Free: 10min, Pro: 60min, Enterprise: 24hr).
     */
    public static <In, Out> Node<In, Out> create(String name, int cacheTtl, int timeoutSeconds, Function<In, Out> fn) {
        return new Node<>(name, cacheTtl, timeoutSeconds, fn);
    }

    public String getName() { return name; }
    public int getCacheTtl() { return cacheTtl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }

    /**
     * Execute the node synchronously.
     */
    public Out run(In input) {
        String runId = UUID.randomUUID().toString();
        LOG.info("[graphingest] Starting node: " + name + " (run=" + runId + ", timeout=" + timeoutSeconds + "s)");
        long start = System.currentTimeMillis();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Out> future = executor.submit(() -> fn.apply(input));
            Out result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;
            LOG.info("[graphingest] Node " + name + " completed in " + duration + "ms");
            return result;
        } catch (TimeoutException e) {
            throw new RuntimeException("graphingest: Node " + name + " timed out after " + timeoutSeconds + "s", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("graphingest: Node " + name + " failed: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("graphingest: Node " + name + " interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Fan-out: execute the node across multiple inputs in parallel.
     */
    public List<Out> map(List<In> inputs) {
        LOG.info("[graphingest] Mapping " + inputs.size() + " invocations of node " + name);
        return inputs.parallelStream()
                .map(this::run)
                .toList();
    }

    /**
     * Async dispatch: returns a NodeFuture for the result.
     */
    public NodeFuture<Out> arun(In input) {
        return new NodeFuture<>(this, input);
    }
}
