package uw.logback.es.appender;

import ch.qos.logback.ext.loggly.io.IoUtils;
import uw.logback.es.ElasticsearchBatchAppenderMBean;
import uw.logback.es.util.DiscardingRollingOutputStream;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logback日志批量接收器
 *
 * @author liliang
 * @since 2018-07-25
 */
public class ElasticsearchBatchAppender<E> extends AbstractElasticsearchAppender<E> implements ElasticsearchBatchAppenderMBean {

    private boolean debug = false;

    private int flushIntervalInSeconds = 3;

    private DiscardingRollingOutputStream outputStream;

    protected final AtomicLong sendDurationInNanos = new AtomicLong();

    protected final AtomicLong sentBytes = new AtomicLong();

    protected final AtomicInteger sendSuccessCount = new AtomicInteger();

    protected final AtomicInteger sendExceptionCount = new AtomicInteger();

    private ScheduledExecutorService scheduledExecutor;

    private boolean jmxMonitoring = true;

    private MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

    private ObjectName registeredObjectName;

    private int maxNumberOfBuckets = 8;

    private int maxBucketSizeInKilobytes = 1024;

    private Charset charset = Charset.forName("UTF-8");

    @Override
    protected void append(E eventObject) {
        if (!isStarted()) {
            return;
        }
        String msg = this.layout.doLayout(eventObject);

        // Issue #21: Make sure messages end with new-line to delimit
        // individual log events within the batch sent to elasticsearch.
        if (!msg.endsWith("\n")) {
            msg += "\n";
        }

        try {
            outputStream.write(msg.getBytes(charset));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start() {

        // OUTPUTSTREAM
        outputStream = new DiscardingRollingOutputStream(
                maxBucketSizeInKilobytes * 1024,
                maxNumberOfBuckets) {
            @Override
            protected void onBucketDiscard(ByteArrayOutputStream discardedBucket) {
                if (isDebug()) {
                    addInfo("Discard bucket - " + getDebugInfo());
                }
                String s = new Timestamp(System.currentTimeMillis()) + " - OutputStream is full, discard previous logs" + LINE_SEPARATOR;
                try {
                    getFilledBuckets().peekLast().write(s.getBytes(charset));
                    addWarn(s);
                } catch (IOException e) {
                    addWarn("Exception appending warning message '" + s + "'", e);
                }
            }

            @Override
            protected void onBucketRoll(ByteArrayOutputStream rolledBucket) {
                if (isDebug()) {
                    addInfo("Roll bucket - " + getDebugInfo());
                }
            }

        };

        // SCHEDULER
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setName("logback-elasticsearch-appender");
                thread.setDaemon(true);
                return thread;
            }
        };
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
        scheduledExecutor.scheduleWithFixedDelay(new ElasticsearchBatchAppender.ElasticsearchExporter(),
                flushIntervalInSeconds, flushIntervalInSeconds, TimeUnit.SECONDS);

        // MONITORING
        if (jmxMonitoring) {
            String objectName = "ch.qos.logback:type=ElasticsearchBatchAppender,name=ElasticsearchBatchAppender@" + System.identityHashCode(this);
            try {
                registeredObjectName = mbeanServer.registerMBean(this, new ObjectName(objectName)).getObjectName();
            } catch (Exception e) {
                addWarn("Exception registering mbean '" + objectName + "'", e);
            }
        }

        super.start();
    }

    @Override
    public void stop() {
        scheduledExecutor.shutdown();

        processLogEntries();

        if (registeredObjectName != null) {
            try {
                mbeanServer.unregisterMBean(registeredObjectName);
            } catch (Exception e) {
                addWarn("Exception unRegistering mbean " + registeredObjectName, e);
            }
        }

        try {
            scheduledExecutor.awaitTermination(2 * this.flushIntervalInSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            addWarn("Exception waiting for termination of ElasticsearchAppender scheduler", e);
        }

        outputStream.close();

        super.stop();
    }

    /**
     * Send log entries to Elasticsearch
     */
    @Override
    public void processLogEntries() {
        if (isDebug()) {
            addInfo("Process log entries - " + getDebugInfo());
        }

        outputStream.rollCurrentBucketIfNotEmpty();
        BlockingDeque<ByteArrayOutputStream> filledBuckets = outputStream.getFilledBuckets();

        ByteArrayOutputStream bucket;

        while ((bucket = filledBuckets.poll()) != null) {
            try {
                InputStream in = new ByteArrayInputStream(bucket.toByteArray());
                processLogEntries(in);
            } catch (Exception e) {
                addWarn("Internal error", e);
            }
            outputStream.recycleBucket(bucket);
        }
    }

    /**
     * Creates a configured HTTP connection to a URL (does not open the
     * connection)
     *
     * @param url target URL
     * @return the newly created HTTP connection
     * @throws IOException
     */
    protected HttpURLConnection getHttpConnection(URL url) throws IOException {
        HttpURLConnection conn;
        if (proxy == null) {
            conn = (HttpURLConnection) url.openConnection();
        } else {
            conn = (HttpURLConnection) url.openConnection(proxy);
        }

        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestProperty("Content-Type", layout.getContentType() + "; charset=" + charset.name());
        conn.setRequestMethod("POST");
        conn.setReadTimeout(getHttpReadTimeoutInMillis());
        return conn;
    }

    /**
     * Send log entries to Elasticsearch
     */
    protected void processLogEntries(InputStream in) throws IOException {
        long nanosBefore = System.nanoTime();
        try {

            HttpURLConnection conn = getHttpConnection(new URL(endpointUrl));
            BufferedOutputStream out = new BufferedOutputStream(conn.getOutputStream());

            long len = IoUtils.copy(in, out);
            sentBytes.addAndGet(len);

            out.flush();
            out.close();

            int responseCode = conn.getResponseCode();
            String response = super.readResponseBody(conn.getInputStream());
            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                case HttpURLConnection.HTTP_ACCEPTED:
                    sendSuccessCount.incrementAndGet();
                    break;
                default:
                    sendExceptionCount.incrementAndGet();
                    addError("ElasticsearchAppender server-side exception: " + responseCode + ": " + response);
            }
            // force url connection recycling
            try {
                conn.getInputStream().close();
                conn.disconnect();
            } catch (Exception e) {
                // swallow exception
            }
        } catch (Exception e) {
            sendExceptionCount.incrementAndGet();
            addError("ElasticsearchAppender client-side exception", e);
        } finally {
            sendDurationInNanos.addAndGet(System.nanoTime() - nanosBefore);
        }
    }

    public int getFlushIntervalInSeconds() {
        return flushIntervalInSeconds;
    }

    public void setFlushIntervalInSeconds(int flushIntervalInSeconds) {
        this.flushIntervalInSeconds = flushIntervalInSeconds;
    }

    @Override
    public long getSentBytes() {
        return sentBytes.get();
    }

    @Override
    public long getSendDurationInNanos() {
        return sendDurationInNanos.get();
    }

    @Override
    public int getSendSuccessCount() {
        return sendSuccessCount.get();
    }

    @Override
    public int getSendExceptionCount() {
        return sendExceptionCount.get();
    }

    @Override
    public int getDiscardedBucketsCount() {
        return outputStream.getDiscardedBucketCount();
    }

    @Override
    public long getCurrentLogEntriesBufferSizeInBytes() {
        return outputStream.getCurrentOutputStreamSize();
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    public void setJmxMonitoring(boolean jmxMonitoring) {
        this.jmxMonitoring = jmxMonitoring;
    }

    public void setMbeanServer(MBeanServer mbeanServer) {
        this.mbeanServer = mbeanServer;
    }

    public void setMaxNumberOfBuckets(int maxNumberOfBuckets) {
        this.maxNumberOfBuckets = maxNumberOfBuckets;
    }

    public void setMaxBucketSizeInKilobytes(int maxBucketSizeInKilobytes) {
        this.maxBucketSizeInKilobytes = maxBucketSizeInKilobytes;
    }

    private String getDebugInfo() {
        return "{" +
                "sendDurationInMillis=" + TimeUnit.MILLISECONDS.convert(sendDurationInNanos.get(), TimeUnit.NANOSECONDS) +
                ", sendSuccessCount=" + sendSuccessCount +
                ", sendExceptionCount=" + sendExceptionCount +
                ", sentBytes=" + sentBytes +
                ", discardedBucketsCount=" + getDiscardedBucketsCount() +
                ", currentLogEntriesBufferSizeInBytes=" + getCurrentLogEntriesBufferSizeInBytes() +
                '}';
    }

    public class ElasticsearchExporter implements Runnable {
        @Override
        public void run() {
            try {
                processLogEntries();
            } catch (Exception e) {
                addWarn("Exception processing log entries", e);
            }
        }
    }
}
