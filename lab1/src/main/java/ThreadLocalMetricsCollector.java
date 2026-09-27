import java.util.ArrayList;
import java.util.List;

public class ThreadLocalMetricsCollector implements IMetricsCollector {
    private final List<CollectorThreadState> states = new ArrayList<CollectorThreadState>();
    private final Object statesLock = new Object();

    private final ThreadLocal<CollectorThreadState> state = ThreadLocal.withInitial(() -> {
        // 'myState' init factory
        CollectorThreadState s = new CollectorThreadState();
        synchronized (statesLock) {
            states.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        CollectorThreadState s = this.state.get(); // call fabric
        int b = (int) Math.min(value / 4, 255);

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        if (value < s.min.getPlain())
            s.min.setRelease(value);
        if (value > s.max.getPlain())
            s.max.setRelease(value);
    }

    @Override
    public Snapshot snapshot() {
        long[] out = new long[256];
        long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

        synchronized (statesLock) {
            for (CollectorThreadState s : this.states) {
                for (int i = 0; i < 256; i++)
                    out[i] += s.buckets.get(i);
                count += s.count.get();

                sum += s.sum.get();
                min = Math.min(min, s.min.get());
                max = Math.max(max, s.max.get());
            }
        }

        long p50 = computePercentile(out, count, 0.50);
        long p99 = computePercentile(out, count, 0.99);

        return new Snapshot(out, count, sum, min, max, p50, p99);
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
