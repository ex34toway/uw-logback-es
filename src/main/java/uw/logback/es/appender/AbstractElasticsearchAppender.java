package uw.logback.es.appender;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 日志接收器抽象接口
 *
 * @author liliang
 * @since 2018-07-25
 */
public abstract class AbstractElasticsearchAppender<Event> extends UnsynchronizedAppenderBase<Event> {

    /**
     * Elasticsearch Web API endpoint
     */
    protected String esHost;

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
     * http 读超时时间
     */
    private int httpReadTimeoutInMillis = 1000;

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
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    protected byte[] toBytes(final InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int count;
        byte[] buf = new byte[512];

        while((count = is.read(buf, 0, buf.length)) != -1) {
            baos.write(buf, 0, count);
        }
        baos.flush();

        return baos.toByteArray();
    }


    public String getEsHost() {
        return esHost;
    }

    public void setEsHost(String esHost) {
        this.esHost = esHost;
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

    public int getHttpReadTimeoutInMillis() {
        return httpReadTimeoutInMillis;
    }

    public void setHttpReadTimeoutInMillis(int httpReadTimeoutInMillis) {
        this.httpReadTimeoutInMillis = httpReadTimeoutInMillis;
    }
}
