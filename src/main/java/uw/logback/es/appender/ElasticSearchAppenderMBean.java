package uw.logback.es.appender;

/**
 * ElasticSearchAppender JMX Bean
 *
 * @author liliang
 * @since 2018-07-27
 */
public interface ElasticSearchAppenderMBean {

    /**
     * force processLogEntries
     *
     */
    void forceProcessLogBucket();

    /**
     * getMaxFlushInMilliseconds
     *
     */
    long getMaxFlushInMilliseconds();

    /**
     * changeMaxFlushInMilliseconds
     *
     * @param maxFlushInMilliseconds
     */
    void changeMaxFlushInMilliseconds(long maxFlushInMilliseconds);

    /**
     * getMaxBytesOfBatch
     *
     */
    long getMaxBytesOfBatch();

    /**
     * changeMaxBytesOfBatch
     *
     * @param maxBytesOfBatch
     */
    void changeMaxBytesOfBatch(long maxBytesOfBatch);
}
