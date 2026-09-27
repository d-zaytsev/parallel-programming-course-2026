import java.util.concurrent.atomic.AtomicInteger;

class CollectorDoubleBufferThreadState {
    // Double buffers
    final long[][] buckets = new long[2][256];
    final long[] count = new long[2];
    final long[] sum = new long[2];
    final long[] min = { Long.MAX_VALUE, Long.MAX_VALUE };
    final long[] max = { 0, 0 };
    // -1 means "no buffers in use"
    final AtomicInteger inside = new AtomicInteger(-1);
}