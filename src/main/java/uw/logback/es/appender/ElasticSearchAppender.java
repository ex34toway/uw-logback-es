package uw.logback.es.appender;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import okhttp3.Request;
import uw.httpclient.http.HttpHelper;
import uw.httpclient.http.HttpInterface;
import uw.httpclient.json.JsonInterfaceHelper;
import uw.httpclient.util.BufferRequestBody;
import uw.logback.es.util.EncoderUtils;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Logback日志批量接收器
 *
 * @author liliang
 * @since 2018-07-25
 */
public class ElasticSearchAppender<Event extends ILoggingEvent> extends UnsynchronizedAppenderBase<Event>
        implements ElasticSearchAppenderMBean {

    private static final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

    private static final HttpInterface httpInterface = new JsonInterfaceHelper();

    /**
     * Elasticsearch Web API endpoint
     */
    private String esHost;

    /**
     * Elasticsearch bulk api endpoint
     */
    private String esBulk = "/_bulk";

    /**
     * 索引
     */
    private String index;

    /**
     * 索引类型
     */
    private String indexType = "logs";

    /**
     * 索引pattern
     */
    private String indexPattern;

    /******************************************自定义字段*********************************/
    /**
     * 主机
     */
    private String host;
    /**
     * 应用名称
     */
    private String appname;
    /******************************************自定义字段*********************************/

    /**
     * Used to create the necessary {@link JsonGenerator}s for generating JSON.
     */
    private MappingJsonFactory jsonFactory = (MappingJsonFactory) new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .getFactory()
            .enable(JsonGenerator.Feature.ESCAPE_NON_ASCII)
            .disable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);

    /**
     * stack_trace转换器
     */
    private ThrowableHandlingConverter throwableConverter = new ExtendedThrowableProxyConverter();

    /**
     * 读写锁
     */
    private final Lock batchLock = new ReentrantLock();

    /**
     * 刷新Bucket时间毫秒数
     */
    private long maxFlushInMilliseconds = 1000;

    /**
     * 允许最大Bucket 字节数。
     */
    private long maxBytesOfBatch = 5*1024*1024;

    /**
     * 最大批量线程数。
     */
    private int maxBatchThreads = 3;

    /**
     * 是否开启JMX
     */
    private boolean jmxMonitoring = false;

    /**
     * bucketList
     */
    private okio.Buffer buffer = new okio.Buffer();

    /**
     * 后台线程
     */
    private ElasticsearchDaemonExporter daemonExporter;

    /**
     * JMX注册名称
     */
    private ObjectName registeredObjectName;

    /**
     * 后台批量线程池。
     */
    private ThreadPoolExecutor batchExecutor;

    @Override
    protected void append(Event event) {
        if (!isStarted()) {
            return;
        }
        // SegmentPool pooling
        batchLock.lock();
        try {
            buffer.writeUtf8("{\"index\":{\"_index\":\"")
                  .writeUtf8(processIndex())
                  .writeUtf8("\",\"_type\":\"")
                  .writeUtf8(getIndexType())
                  .writeUtf8("\"}}")
                  .write(EncoderUtils.LINE_SEPARATOR_BYTES);
            JsonGenerator jsonGenerator = jsonFactory.createGenerator(buffer.outputStream(), JsonEncoding.UTF8);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("@timestamp", EncoderUtils.DATE_FORMAT.format(event.getTimeStamp()));
            jsonGenerator.writeStringField("app_name", appname);
            jsonGenerator.writeStringField("host", host);
            jsonGenerator.writeStringField("level", event.getLevel().toString());
            jsonGenerator.writeStringField("logger_name", event.getLoggerName());
            jsonGenerator.writeStringField("message", event.getMessage());
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                jsonGenerator.writeStringField("stack_trace", throwableConverter.convert(event));
            }
            jsonGenerator.writeEndObject();
            jsonGenerator.flush();
            buffer.write(EncoderUtils.LINE_SEPARATOR_BYTES);
        } catch (Exception e) {
            addError(e.getMessage(), e);
        } finally {
            batchLock.unlock();
        }
    }

    @Override
    public void start() {
        if (esHost == null) {
            addError("No config for <esHost>");
        }
        if(appname == null) {
            addError("No elasticsearch index was configured. Use <index> to specify the fully qualified class name of the encoder to use");
        }
        if(index == null) {
            index = appname;
        }
        if (jmxMonitoring) {
            String objectName = "ch.qos.logback:type=ElasticsearchBatchAppender,name=ElasticsearchBatchAppender@" + System.identityHashCode(this);
            try {
                registeredObjectName = mbeanServer.registerMBean(this, new ObjectName(objectName)).getObjectName();
            } catch (Exception e) {
                addWarn("Exception registering mbean '" + objectName + "'", e);
            }
        }
        daemonExporter = new ElasticsearchDaemonExporter();
        daemonExporter.init();
        daemonExporter.start();

        batchExecutor = new ThreadPoolExecutor(1, maxBatchThreads, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("logback-es-batch-%d").build(), new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                System.err.println("Logback ES Batch Task " + r.toString() +" rejected from " + executor.toString());
            }
        });
        super.start();
    }

    @Override
    public void stop() {
        // 赶紧处理一把
        forceProcessLogBucket();
        daemonExporter.readyDestroy();
        batchExecutor.shutdown();
        if (registeredObjectName != null) {
            try {
                mbeanServer.unregisterMBean(registeredObjectName);
            } catch (Exception e) {
                addWarn("Exception unRegistering mbean " + registeredObjectName, e);
            }
        }
        super.stop();
    }

    /**
     * 处理索引
     *
     * @return
     */
    private String processIndex() {
        return index + indexPattern;
    }

    /**
     * Send buffer to Elasticsearch
     *
     * @param force - 是否强制发送
     */
    private void processLogBucket(boolean force) {
        batchLock.lock();
        okio.Buffer bufferData = null;
        try {
            if (force || buffer.size() > maxBytesOfBatch) {
                bufferData = buffer;
                buffer = new okio.Buffer();
            }
        } finally {
            batchLock.unlock();
        }
        // XXX: 注意,Spring 为了让自己控制Logging,会对Logging重启一把,此时force一把,buffer有可能是空的
        if (bufferData == null || bufferData.size() == 0) {
            return;
        }
        try {
            httpInterface.requestForObject(new Request.Builder().url(esHost + getEsBulk())
                    .post(BufferRequestBody.create(HttpHelper.JSON_UTF8, bufferData)).build(), String.class);
        } catch (Exception e) {
            addError(e.getMessage(), e);
        }
    }

    /**
     * 强制日志提交
     */
    @Override
    public void forceProcessLogBucket() {
        processLogBucket(true);
    }

    public String getEsHost() {
        return esHost;
    }

    public void setEsHost(String esHost) {
        this.esHost = esHost;
    }

    public String getEsBulk() {
        return esBulk;
    }

    public void setEsBulk(String esBulk) {
        this.esBulk = esBulk;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public String getIndexPattern() {
        return indexPattern;
    }

    public void setIndexPattern(String indexPattern) {
        this.indexPattern = indexPattern;
    }

    public long getMaxFlushInMilliseconds() {
        return maxFlushInMilliseconds;
    }

    @Override
    public void setMaxFlushInMilliseconds(long maxFlushInMilliseconds) {
        this.maxFlushInMilliseconds = maxFlushInMilliseconds;
    }

    public long getMaxBytesOfBatch() {
        return maxBytesOfBatch;
    }

    @Override
    public void setMaxBytesOfBatch(long maxBytesOfBatch) {
        this.maxBytesOfBatch = maxBytesOfBatch;
    }

    public int getMaxBatchThreads() {
        return maxBatchThreads;
    }

    public void setMaxBatchThreads(int maxBatchThreads) {
        this.maxBatchThreads = maxBatchThreads;
    }

    public boolean isJmxMonitoring() {
        return jmxMonitoring;
    }

    public void setJmxMonitoring(boolean jmxMonitoring) {
        this.jmxMonitoring = jmxMonitoring;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public ThrowableHandlingConverter getThrowableConverter() {
        return throwableConverter;
    }

    public void setThrowableConverter(ThrowableHandlingConverter throwableConverter) {
        this.throwableConverter = throwableConverter;
    }

    /**
     * 后台写日志线程
     */
    public class ElasticsearchDaemonExporter extends Thread {

        /**
         * 运行标记.
         */
        private volatile boolean isRunning = false;

        /**
         * 下一次运行时间
         */
        private volatile long nextScanTime = 0;

        /**
         * 初始化
         */
        public void init() {
            isRunning = true;
        }

        /**
         * 销毁标记.
         */
        public void readyDestroy() {
            isRunning = false;
        }

        @Override
        public void run() {
            while (isRunning) {
                try {
                    if (nextScanTime < System.currentTimeMillis()) {
                        nextScanTime = System.currentTimeMillis() + maxFlushInMilliseconds;
                        batchExecutor.submit(new Runnable() {
                            @Override
                            public void run() {
                                processLogBucket(false);
                            }
                        });
                    }
                    Thread.sleep(maxFlushInMilliseconds / 2);
                } catch (Exception e) {
                    addWarn("Exception processing log entries", e);
                }
            }
        }
    }
}
