package io.graphingest;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Configures retry behavior with exponential backoff and optional jitter.
 *
 * <pre>{@code
 * new RetryPolicy(3, 1.0, 2.0)          // 3 retries, 1s initial, 2x backoff
 * new RetryPolicy(3, 1.0, 2.0, 120.0, true) // with max delay and jitter
 * }</pre>
 */
public class RetryPolicy {

    private final int maxRetries;
    private final double delaySeconds;
    private final double backoffFactor;
    private final double maxDelaySeconds;
    private final boolean jitter;

    public RetryPolicy(int maxRetries, double delaySeconds, double backoffFactor) {
        this(maxRetries, delaySeconds, backoffFactor, 120.0, true);
    }

    public RetryPolicy(int maxRetries, double delaySeconds, double backoffFactor, double maxDelaySeconds, boolean jitter) {
        this.maxRetries = maxRetries;
        this.delaySeconds = delaySeconds;
        this.backoffFactor = backoffFactor > 0 ? backoffFactor : 2.0;
        this.maxDelaySeconds = maxDelaySeconds > 0 ? maxDelaySeconds : 120.0;
        this.jitter = jitter;
    }

    public int getMaxRetries() { return maxRetries; }

    /**
     * Compute delay in milliseconds for a given attempt (0-indexed).
     */
    public long computeDelayMs(int attempt) {
        if (delaySeconds <= 0) return 0;
        double delay = delaySeconds * Math.pow(backoffFactor, attempt);
        delay = Math.min(delay, maxDelaySeconds);
        if (jitter) {
            delay *= 0.5 + ThreadLocalRandom.current().nextDouble(); // [0.5, 1.5)
        }
        return (long) (delay * 1000);
    }
}
