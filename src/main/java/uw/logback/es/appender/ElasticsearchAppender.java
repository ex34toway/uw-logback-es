package uw.logback.es.appender;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import com.google.common.collect.Lists;
import okhttp3.Request;
import uw.httpclient.http.HttpHelper;
import uw.httpclient.http.HttpInterface;
import uw.httpclient.json.JsonInterfaceHelper;
import uw.httpclient.util.BufferRequestBody;
import uw.logback.es.util.EncoderUtils;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Logback日志批量接收器
 *
 * @author liliang
 * @since 2018-07-25
 */
public class ElasticsearchAppender<Event> extends UnsynchronizedAppenderBase<Event> {

    private static final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

    private static final HttpInterface httpInterface = new JsonInterfaceHelper();

    /**
     * Elasticsearch Web API endpoint
     */
    protected String esHost;

    /**
     * Elasticsearch bulk api endpoint
     */
    protected String esBulk = "/_bulk";

    /**
     * 索引[默认appname]
     */
    protected String index;

    /**
     * 索引类型
     */
    protected String indexType = "logs";

    /**
     * pattern
     */
    protected String pattern;

    /**
     * 日志编码器,直接编码成字节交给okhttp
     */
    protected Encoder<Event> encoder;

    /**
     * 定期刷新Bucket时间毫秒数
     */
    private int flushIntervalInMillSeconds = 1000;

    /**
     * 允许最大Bucket数量
     */
    private int maxNumberOfBuckets = 1;

    /**
     * 读写锁
     */
    private final Lock bucketLock = new ReentrantLock();

    /**
     * bucketList
     */
    private List<okio.Buffer> bucketList = Lists.newArrayList();

    /**
     * 是否开启JMX
     */
    private boolean jmxMonitoring = false;

    /**
     * 后台线程
     */
    private ElasticsearchDaemonExporter daemonExporter;

    /**
     * JMX注册名称
     */
    private ObjectName registeredObjectName;

    @Override
    protected void append(Event eventObject) {
        if (!isStarted()) {
            return;
        }
        // SegmentPool pooling
        okio.Buffer bucket = new okio.Buffer();
        bucket.writeUtf8("{\"index\":{\"_index\":\"")
                .writeUtf8(processIndex())
                .writeUtf8("\",\"_type\":\"")
                .writeUtf8(getIndexType())
                .writeUtf8("\"}}").write(EncoderUtils.LINE_SEPARATOR_BYTES);
        bucket.write(this.encoder.encode(eventObject));
        bucket.write(EncoderUtils.LINE_SEPARATOR_BYTES);
        writeBucket(bucket);
    }

    @Override
    public void start() {
        if (index == null) {
            addError("No elasticsearch index was configured. Use <index> to specify the fully qualified class name of the encoder to use");
        }
        if (encoder == null) {
            addError("No encoder was configured. Use <encoder> to specify the fully qualified class name of the encoder to use");
        }
        if (esHost == null) {
            addError("No config for <esHost>");
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
        super.start();
    }

    @Override
    public void stop() {
        // 赶紧处理一把
        processLogBucket(true);
        daemonExporter.readyDestroy();
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
        return index;
    }

    /**
     * Send log entries to Elasticsearch
     */
    private void processLogBucket(boolean force) {
        bucketLock.lock();
        List<okio.Buffer> bucketData = null;
        try {
            if (force || bucketList.size() > maxNumberOfBuckets) {
                bucketData = bucketList;
                bucketList = Lists.newArrayList();
            }
        } finally {
            bucketLock.unlock();
        }
        if (bucketData == null) {
            return;
        }
        try {
            processLogBucket(bucketData);
        } catch (Exception e) {
            addError(e.getMessage(), e);
        }
    }

    /**
     * Send log entries to Elasticsearch
     */
    private void processLogBucket(List<okio.Buffer> bucketList) throws Exception {
        okio.Buffer sendBuffer = new okio.Buffer();
        for(okio.Buffer bucket : bucketList){
            sendBuffer.writeAll(bucket);
        }
        httpInterface.requestForObject(new Request.Builder().url(esHost+getEsBulk())
                .post(BufferRequestBody.create(HttpHelper.JSON_UTF8,sendBuffer)).build(),String.class);
    }

    /**
     *
     * @param buffer
     */
    private void writeBucket(okio.Buffer buffer) {
        bucketLock.lock();
        try {
            bucketList.add(buffer);
        } finally {
            bucketLock.unlock();
        }
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

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Encoder<Event> getEncoder() {
        return encoder;
    }

    public void setEncoder(Encoder<Event> encoder) {
        this.encoder = encoder;
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
                        nextScanTime = System.currentTimeMillis() + flushIntervalInMillSeconds;
                        processLogBucket(false);
                    }
                    // 休息一会儿
                    Thread.sleep(flushIntervalInMillSeconds / 2);
                } catch (Exception e) {
                    addWarn("Exception processing log entries", e);
                }
            }
        }
    }
}
