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

    public long run(IMetricsCollector collector, long[] values, int T, int seconds) throws InterruptedException {
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

        return (long) (LongStream.of(ops).sum() / millisElapsed);
    }

    public long measurePoint(IMetricsCollector collector, long[] values, int T) throws InterruptedException {
        // warmup
        this.run(collector, values, T, WARMUP_SECONDS);

        long[] results = new long[RUNS];
        for (int r = 0; r < RUNS; r++)
            results[r] = run(collector, values, T, RUN_SECONDS); // ops in millisecond

        System.out.println("records count: " + collector.snapshot().count());

        Arrays.sort(results);
        return results[RUNS / 2];
    }

    private static IMetricsCollector createCollector(String name) {
        switch (name) {
            case "single":
                return new MetricsCollector();
            default:
                throw new IllegalArgumentException("Unknown collector: " + name);
        }
    }

    public String getGreeting() {
        return "Hello world.";
    }

    public static void main(String[] args) throws InterruptedException {
        String name = args.length > 0 ? args[0] : "single";
        int threadCount = args.length > 1 ? Integer.parseInt(args[1]) : 1;

        // Prepare dataset
        LoadGenerator gen = new LoadGenerator(SEED);
        gen.generateLoad();
        long[] values = gen.getValues();

        App app = new App();
        // Prepare collector
        IMetricsCollector collector = createCollector(name);
        System.out.println("collector: " + name + " T=" + threadCount);

        // Measure
        long opsPerMs = app.measurePoint(collector, values, threadCount);
        System.out.printf("median: %.2f M ops/sec%n", opsPerMs / 1000F);
    }
}
