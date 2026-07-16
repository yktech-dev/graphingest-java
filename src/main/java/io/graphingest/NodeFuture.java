package io.graphingest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handle for an async node dispatch. Call {@link #result()} to block until done.
 *
 * <pre>{@code
 * NodeFuture<Map<String, Object>> future = extract.arun("https://example.com");
 * var result = future.result();          // block until done
 * var result = future.result(60_000);    // block with timeout (ms)
 * }</pre>
 */
public class NodeFuture<Out> {

    private final CompletableFuture<Out> future;
    private final String nodeKey;

    NodeFuture(Node<?, Out> node, Object input) {
        this.nodeKey = node.getName();
        @SuppressWarnings("unchecked")
        Node<Object, Out> typed = (Node<Object, Out>) node;
        this.future = CompletableFuture.supplyAsync(() -> typed.run(input));
    }

    /**
     * Block until the result is available.
     */
    public Out result() {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("graphingest: NodeFuture for " + nodeKey + " failed", e);
        }
    }

    /**
     * Block until the result is available, with a timeout in milliseconds.
     */
    public Out result(long timeoutMs) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("graphingest: NodeFuture for " + nodeKey + " timed out or failed", e);
        }
    }

    /**
     * Check if the result is available without blocking.
     */
    public boolean isDone() {
        return future.isDone();
    }
}
