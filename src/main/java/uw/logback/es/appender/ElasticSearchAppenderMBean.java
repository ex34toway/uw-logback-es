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
     */
    void forceProcessLogBucket();

    /**
     * setMaxFlushInMilliseconds
     *
     * @param maxFlushInMilliseconds
     */
    void setMaxFlushInMilliseconds(long maxFlushInMilliseconds);

    /**
     * setMaxBytesOfBatch
     *
     * @param maxBytesOfBatch
     */
    void setMaxBytesOfBatch(long maxBytesOfBatch);
}
