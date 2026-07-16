package io.graphingest;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Tier-based platform limits for execution timeouts and quotas.
 *
 * <p>Limits are resolved from the {@code GRAPHINGEST_TIER} environment variable
 * (defaults to "free"). Requested timeouts are clamped to the tier maximum.</p>
 *
 * <table>
 *   <tr><th>Tier</th><th>Node Default</th><th>Node Max</th><th>Graph Default</th><th>Graph Max</th><th>Monthly Min</th><th>Pipelines</th></tr>
 *   <tr><td>free</td><td>5 min</td><td>10 min</td><td>15 min</td><td>30 min</td><td>60</td><td>5</td></tr>
 *   <tr><td>pro</td><td>10 min</td><td>60 min</td><td>1 hr</td><td>6 hr</td><td>unlimited</td><td>unlimited</td></tr>
 *   <tr><td>enterprise</td><td>60 min</td><td>24 hr</td><td>6 hr</td><td>24 hr</td><td>unlimited</td><td>unlimited</td></tr>
 * </table>
 */
public final class PlatformLimits {

    private static final Logger LOG = Logger.getLogger(PlatformLimits.class.getName());

    public final int nodeDefaultTimeout;      // seconds
    public final int nodeMaxTimeout;          // seconds
    public final int graphDefaultTimeout;     // seconds
    public final int graphMaxTimeout;         // seconds
    public final int monthlyExecutionMinutes; // 0 = unlimited
    public final int maxPipelines;            // 0 = unlimited

    private PlatformLimits(int nodeDefault, int nodeMax, int graphDefault, int graphMax,
                           int monthlyMinutes, int maxPipelines) {
        this.nodeDefaultTimeout = nodeDefault;
        this.nodeMaxTimeout = nodeMax;
        this.graphDefaultTimeout = graphDefault;
        this.graphMaxTimeout = graphMax;
        this.monthlyExecutionMinutes = monthlyMinutes;
        this.maxPipelines = maxPipelines;
    }

    private static final Map<String, PlatformLimits> TIERS = Map.of(
        "free",       new PlatformLimits(300, 600, 900, 1800, 60, 5),
        "pro",        new PlatformLimits(600, 3600, 3600, 21600, 0, 0),
        "enterprise", new PlatformLimits(3600, 86400, 21600, 86400, 0, 0)
    );

    /**
     * Returns the current tier name from GRAPHINGEST_TIER env var (default: "free").
     */
    public static String getTier() {
        String tier = System.getenv("GRAPHINGEST_TIER");
        return (tier != null && !tier.isEmpty()) ? tier.toLowerCase() : "free";
    }

    /**
     * Returns the PlatformLimits for the current tier.
     */
    public static PlatformLimits get() {
        return TIERS.getOrDefault(getTier(), TIERS.get("free"));
    }

    /**
     * Clamp a requested timeout (seconds) to the tier limits.
     * Returns the default if requested &lt;= 0, or the clamped value if it exceeds the max.
     */
    public static int clampNodeTimeout(int requested) {
        PlatformLimits limits = get();
        if (requested <= 0) return limits.nodeDefaultTimeout;
        if (requested > limits.nodeMaxTimeout) {
            LOG.warning("[graphingest] Requested node timeout " + requested + "s exceeds "
                + getTier() + " tier max (" + limits.nodeMaxTimeout + "s). Clamped.");
            return limits.nodeMaxTimeout;
        }
        return requested;
    }

    /**
     * Clamp a requested graph timeout (seconds) to the tier limits.
     */
    public static int clampGraphTimeout(int requested) {
        PlatformLimits limits = get();
        if (requested <= 0) return limits.graphDefaultTimeout;
        if (requested > limits.graphMaxTimeout) {
            LOG.warning("[graphingest] Requested graph timeout " + requested + "s exceeds "
                + getTier() + " tier max (" + limits.graphMaxTimeout + "s). Clamped.");
            return limits.graphMaxTimeout;
        }
        return requested;
    }
}
