public class MetricsCollector implements IMetricsCollector {
    private long[] buckets; // 0 - 1024 ms (4 ms in each bucket)
    private long count;
    private long sum;
    private long min;
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
        long p99 = 0;
        long p50 = 0;

        long p50_border = (long) (this.count * 0.5);
        long p99_border = (long) (this.count * 0.99);

        long acc = 0;

        for (int i = 0; i < 255; i++) {
            acc += this.buckets[i];

            if ((acc >= p50_border) && (p50 == 0))
                p50 = i * 4;

            if (acc >= p99_border) {
                p99 = i * 4;
                break;
            }
        }

        return new Snapshot(this.buckets, this.count, this.sum, this.min, this.max, p50, p99);
    }

}
