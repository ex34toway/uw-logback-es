package uw.logback.es;

/**
 * ElasticsearchBatchAppender JMX Bean
 *
 * @author liliang
 * @since 2018-07-25
 */
public interface ElasticsearchBatchAppenderMBean {

    void processLogEntries();

    /**
     * Number of bytes sent to Loggly.
     */
    long getSentBytes();

    /**
     * Duration spent sending logs to Loggly.
     */
    long getSendDurationInNanos();

    /**
     * Number of successful invocations to Loggly's send logs API.
     */
    int getSendSuccessCount();

    /**
     * Number of failing invocations to Loggly's send logs API.
     */
    int getSendExceptionCount();

    /**
     * Number of discarded buckets
     */
    int getDiscardedBucketsCount();

    /**
     * Size in bytes of the log entries that have not yet been sent to Loggly.
     */
    long getCurrentLogEntriesBufferSizeInBytes();

    boolean isDebug();

    /**
     * Enable debugging
     */
    void setDebug(boolean debug);
}
