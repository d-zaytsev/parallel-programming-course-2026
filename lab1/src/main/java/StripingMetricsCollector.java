import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class StripingMetricsCollector implements IMetricsCollector {
    private final long[] buckets = new long[256]; // 0 - 1024 ms (4 ms in each bucket)
    private final ReentrantLock[] bucketLocks = new ReentrantLock[16];
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong();

    public StripingMetricsCollector() {
        for (int i = 0; i < bucketLocks.length; i++)
            this.bucketLocks[i] = new ReentrantLock();
    }

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / 4, 255);
        ReentrantLock lock = bucketLocks[bucket % 16];

        lock.lock();
        this.buckets[bucket] += 1;
        lock.unlock();

        this.count.incrementAndGet();
        this.sum.addAndGet(value);

        long loc_min = this.min.get();
        long new_min = Math.min(value, loc_min);
        while (true) {
            loc_min = this.min.compareAndExchange(loc_min, new_min);

            if (loc_min <= new_min)
                break;
        }

        long loc_max = this.max.get();
        long new_max = Math.max(value, loc_max);
        while (true) {
            loc_max = this.max.compareAndExchange(loc_max, new_max);

            if (loc_max >= new_max)
                break;
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = new long[256];

        for (int b = 0; b < 16; b++) {
            this.bucketLocks[b].lock();
            for (int i = b; i < 256; i += 16) {
                bucketsCopy[i] = this.buckets[i];
            }
            this.bucketLocks[b].unlock();
        }

        long loc_count = this.count.get();
        long loc_sum = this.sum.get();
        long loc_min = this.min.get();
        long loc_max = this.max.get();

        long p50 = computePercentile(bucketsCopy, loc_count, 0.50);
        long p99 = computePercentile(bucketsCopy, loc_count, 0.99);

        return new Snapshot(bucketsCopy, loc_count, loc_sum, loc_min, loc_max, p50, p99);
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
