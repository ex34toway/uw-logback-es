package uw.logback.es.appender;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * 日志接收器抽象接口
 *
 * @author liliang
 * @since 2018-07-25
 */
public abstract class AbstractElasticsearchAppender<Event> extends UnsynchronizedAppenderBase<Event> {

    public static final String DEFAULT_LAYOUT_PATTERN = "%d{\"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'\",UTC} %-5level [%thread] %logger: %m%n";
    protected static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Elasticsearch Web API endpoint
     */
    protected String endpoint;

    /**
     * Created layout Implicitly?
     */
    protected boolean layoutCreatedImplicitly = false;

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
        if (encoder == null) {
            addError("No encoder was configured. Use <encoder> to specify the fully qualified class name of the encoder to use");
        }
        /*
         * Destinations can be configured via <remoteHost>/<port> OR <destination> but not both!
         */
        if (endpoint == null) {
            addError("No config for <endpoint>");
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

    protected String readResponseBody(final InputStream input) throws IOException {
        try {
            final byte[] bytes = toBytes(input);
            return new String(bytes, UTF_8);
        } finally {
            input.close();
        }
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
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
