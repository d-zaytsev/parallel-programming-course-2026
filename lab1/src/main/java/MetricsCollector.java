public class MetricsCollector implements IMetricsCollector {
    private final long[] buckets = new long[256]; // 0 - 1024 ms (4 ms in each bucket)
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / 4, 255);

        this.buckets[bucket] += 1;
        this.count += 1;
        this.sum += value;
        this.min = Math.min(value, min);
        this.max = Math.max(value, max);
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = this.buckets.clone();
        long p50 = computePercentile(bucketsCopy, this.count, 0.50);
        long p99 = computePercentile(bucketsCopy, this.count, 0.99);

        return new Snapshot(bucketsCopy, this.count, this.sum, this.min, this.max, p50, p99);
    }

    static long computePercentile(long[] buckets, long count, double q) {
        double threshold = count * q;
        long acc = 0;

        for (int i = 0; i < buckets.length; i++) {
            acc += buckets[i];

            if (acc >= threshold)
                return i * 4L;
        }

        return (buckets.length - 1) * 4L;
    }
}
