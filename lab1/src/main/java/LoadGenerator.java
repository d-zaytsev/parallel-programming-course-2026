import java.util.Random;

public class LoadGenerator {
    private long[] values;
    private Random random;

    private static final long DATASET_LENGTH = 1_048_576; // 2^20
    private static final int K_MAX_VALUE = 1023;
    private static final double ALPHA = 1.15;

    public LoadGenerator(int seed) {
        this.random = new Random(seed);
    }

    public void generateLoad() {
        values = new long[(int) DATASET_LENGTH];
        double[] cdf = new double[K_MAX_VALUE];
        double sum = 0.0;

        for (int k = 1; k <= K_MAX_VALUE; k++)
            // accumulate weights
            sum += 1.0 / Math.pow(k, ALPHA);

        double acc = 0.0;

        for (int k = 1; k <= K_MAX_VALUE; k++) {
            // normalize weights to get probabilities
            acc += (1.0 / Math.pow(k, ALPHA)) / sum;
            // build Cumulative Distribution Function
            cdf[k - 1] = acc;
        }

        for (int i = 0; i < DATASET_LENGTH; i++) {
            double rand = random.nextDouble();

            // binary search in CDF
            int left = 0;
            int right = K_MAX_VALUE;

            while (left < right) {
                int mid_i = (left + right) >>> 1;

                if (cdf[mid_i] >= rand)
                    right = mid_i;
                else
                    left = mid_i + 1;

            }

            this.values[i] = left + 1;
        }
    }

    public long[] getValues() {
        return values;
    }

    public long getDatasetLength() {
        return DATASET_LENGTH;
    }
}