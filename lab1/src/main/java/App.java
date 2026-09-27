import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;

public class App {
    private static final int WARMUP_SECONDS = 5;
    private static final int RUN_SECONDS = 5;
    private static final int RUNS = 5;
    private static final int SEED = 0;

    public double run(IMetricsCollector collector, long[] values, int T, int seconds) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean stopAtomic = new AtomicBoolean();
        long[] ops = new long[T];
        Thread[] thrds = new Thread[T];

        for (int k = 0; k < T; k++) {
            final int threadId = k;

            Thread thr = new Thread() {
                public void run() {
                    long localCount = 0;
                    int i = threadId * 1000;

                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    while (!stopAtomic.get()) {
                        collector.record(values[i]);
                        localCount += 1;
                        i += 1;
                        if (i == values.length)
                            i = 0;
                    }
                    ops[threadId] = localCount;
                }
            };

            thrds[k] = thr;
            thr.start();
        }

        Instant t0 = Instant.now();
        startLatch.countDown();
        Thread.sleep(seconds * 1000);
        stopAtomic.set(true);
        Instant t1 = Instant.now();

        for (int k = 0; k < T; k++)
            thrds[k].join();

        long millisElapsed = Duration.between(t0, t1).toMillis();

        return LongStream.of(ops).sum() / millisElapsed;
    }

    public double measurePoint(IMetricsCollector collector, long[] values, int T) throws InterruptedException {
        // warmup
        this.run(collector, values, T, WARMUP_SECONDS);

        double[] results = new double[RUNS];
        for (int r = 0; r < RUNS; r++)
            results[r] = run(collector, values, T, RUN_SECONDS); // ops in millisecond

        System.out.println("records count: " + collector.snapshot().count());

        Arrays.sort(results);
        return results[RUNS / 2]; // median
    }

    public void inconsistencyTest(IMetricsCollector collector, long[] values, int T, int times)
            throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean stopAtomic = new AtomicBoolean();
        long incorrectSnapshotsLess = 0;
        long incorrectSnapshotsMore = 0;
        long[] ops = new long[T];
        Thread[] thrds = new Thread[T];

        for (int k = 0; k < T; k++) {
            final int threadId = k;

            Thread thr = new Thread() {
                public void run() {
                    long localCount = 0;
                    int i = threadId * 1000;

                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    while (!stopAtomic.get()) {
                        collector.record(values[i]);
                        localCount += 1;
                        i += 1;
                        if (i == values.length)
                            i = 0;
                    }
                    ops[threadId] = localCount;
                }
            };

            thrds[k] = thr;
            thr.start();
        }

        startLatch.countDown();
        Thread.sleep(200);
        for (int i = 0; i < times; i++) {
            Snapshot snap = collector.snapshot();
            long sum = LongStream.of(snap.buckets()).sum();

            if (sum < snap.count())
                incorrectSnapshotsLess += 1;
            if (sum > snap.count())
                incorrectSnapshotsMore += 1;
        }
        stopAtomic.set(true);

        for (int k = 0; k < T; k++)
            thrds[k].join();

        var lastSnapshot = collector.snapshot();
        var opsSum = LongStream.of(ops).sum();

        System.out.println("Inconsistency Test");
        System.out.println("Result snapshot count: " + lastSnapshot.count());
        System.out.println("Thread operations count: " + opsSum);
        System.out.println("Main thread incorrect snapshots (buckets less than count): " + incorrectSnapshotsLess);
        System.out.println("Main thread incorrect snapshots (buckets more than count): " + incorrectSnapshotsMore);
    }

    public String getGreeting() {
        return "Hello world.";
    }

    public static void main(String[] args) throws InterruptedException {
        int[] threadCounts = { 1, 2, 4, 8, 10, 12 };

        // Prepare dataset
        LoadGenerator gen = new LoadGenerator(SEED);
        gen.generateLoad();
        long[] values = gen.getValues();

        boolean doInconsistencyTest = true;

        App app = new App();
        for (int T : threadCounts) {
            // Prepare collector
            IMetricsCollector collector = new StripingMetricsCollector();
            System.out.println("synchronized collector, T = " + T);

            if (doInconsistencyTest)
                // Inconsistency Test (optional)
                app.inconsistencyTest(collector, values, T, 20_000);
            else {
                // Measure
                double opsPerMs = app.measurePoint(collector, values, T);
                System.out.printf("median: %.2f ops/sec%n", opsPerMs * 1000F);
            }
        }
    }
}
