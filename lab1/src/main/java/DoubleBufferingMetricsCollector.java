import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferingMetricsCollector implements IMetricsCollector {
    private final List<CollectorDoubleBufferThreadState> states = new ArrayList<CollectorDoubleBufferThreadState>();
    private final Object statesLock = new Object();

    private final ThreadLocal<CollectorDoubleBufferThreadState> state = ThreadLocal.withInitial(() -> {
        // 'myState' init factory
        CollectorDoubleBufferThreadState s = new CollectorDoubleBufferThreadState();
        synchronized (statesLock) {
            states.add(s);
        }
        return s;
    });

    final AtomicInteger active = new AtomicInteger(0);

    // accumulated totals, guarded by statesLock
    private final long[] globalBuckets = new long[256];
    private long globalCount = 0;
    private long globalSum = 0;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax = 0;

    @Override
    public void record(long value) {
        var my = this.state.get(); // current thread double buffers

        int bucket = (int) Math.min(value / 4, 255);
        int b;

        while (true) {
            b = this.active.get();
            my.inside.set(b);
            if (this.active.get() == b)
                break;
            my.inside.setRelease(-1);
        }

        my.buckets[b][bucket] += 1;
        my.count[b] += 1;
        my.sum[b] += value;
        my.min[b] = Math.min(value, my.min[b]);
        my.max[b] = Math.max(value, my.max[b]);

        my.inside.setRelease(-1);
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy;
        long count;
        long sum;
        long min;
        long max;

        synchronized (statesLock) {
            int old = this.active.get();
            this.active.set(1 - old);

            for (var s : this.states) {
                while (s.inside.get() == old) {
                    Thread.onSpinWait();
                }

                for (int i = 0; i < 256; i++)
                    this.globalBuckets[i] += s.buckets[old][i];
                this.globalCount += s.count[old];
                this.globalSum += s.sum[old];
                this.globalMin = Math.min(this.globalMin, s.min[old]);
                this.globalMax = Math.max(this.globalMax, s.max[old]);

                Arrays.fill(s.buckets[old], 0);
                s.count[old] = 0;
                s.sum[old] = 0;
                s.min[old] = Long.MAX_VALUE;
                s.max[old] = 0;
            }

            bucketsCopy = this.globalBuckets.clone();
            count = this.globalCount;
            sum = this.globalSum;
            min = this.globalMin;
            max = this.globalMax;
        }

        long p50 = computePercentile(bucketsCopy, count, 0.50);
        long p99 = computePercentile(bucketsCopy, count, 0.99);

        return new Snapshot(bucketsCopy, count, sum, min, max, p50, p99);
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
