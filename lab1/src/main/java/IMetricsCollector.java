public interface IMetricsCollector {
    void record(long value);
    Snapshot snapshot();
}
