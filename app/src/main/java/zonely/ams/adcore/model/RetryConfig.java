package zonely.ams.adcore.model;

public class RetryConfig {
    public final int maxRetries;
    public final int delaySeconds;

    public RetryConfig(int maxRetries, int delaySeconds) {
        this.maxRetries = Math.max(0, maxRetries);
        this.delaySeconds = Math.max(0, delaySeconds);
    }
}
