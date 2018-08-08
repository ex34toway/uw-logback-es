[TOC]

# uw-logback-es

项目主要是为了直接把日志发向es服务集群,而不再需要用logstash收集,方便优化应用程序日志


#### 程序使用配置
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE configuration>
<configuration>
    <springProfile name="default">
        <appender name="ES"
                  class="uw.logback.es.appender.ElasticSearchAppender">
            <esHost>http://localhost:9200</esHost>
            <!-- 索引名称,默认使用appanme -->
            <index>uw-auth-center2</index>
            <!-- 索引Pattern,生成index-indexPattern的索引,目前只支持时间格式器,比如:yyyy-MM-dd -->
            <indexPattern>_yyyy-MM-dd</indexPattern>
            <!-- 索引文档类型,默认为logs -->
            <indexType>logs</indexType>
            <!-- 自定义日志字段 -->
            <!-- 应用名称 -->
            <appName>uw-auth-center2</appName>
            <!-- 当前主机地址 -->
            <host>${host}</host>
            <!-- stack_trace 转换器 -->
            <throwableConverter
                    class="uw.logback.es.stacktrace.ShortenedThrowableConverter">
                <maxDepthPerThrowable>100</maxDepthPerThrowable>
                <maxLength>8096</maxLength>
                <shortenedClassNameLength>100</shortenedClassNameLength>
                <exclude>org\.springframwork\.*</exclude>
                <rootCauseFirst>true</rootCauseFirst>
            </throwableConverter>
            <!-- 批量提交日志最大线程数 -->
            <maxBatchThreads>5</maxBatchThreads>
            <!-- 最大批量线程队列数 -->
            <maxBatchQueueSize>10</maxBatchQueueSize>
            <!-- 批量提交最小字节数 -->
            <maxBytesOfBatch>1025</maxBytesOfBatch>
            <!-- 最大刷新时间间隔 单位毫秒 -->
            <maxFlushInMilliseconds>1000</maxFlushInMilliseconds>
            <!-- 开启JMX监控支持,默认未开启 -->
            <jmxMonitoring>true</jmxMonitoring>
        </appender>
        <root level="INFO">
            <appender-ref ref="ES"/>
        </root>
    </springProfile>
</configuration>
```