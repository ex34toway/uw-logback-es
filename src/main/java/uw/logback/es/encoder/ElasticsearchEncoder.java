package uw.logback.es.encoder;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import uw.logback.es.util.EncoderUtils;

import java.io.ByteArrayOutputStream;

/**
 * Elasticsearch 编码器
 *
 * @author liliang
 * @since 2018-07-26
 */
public class ElasticsearchEncoder<Event extends ILoggingEvent> extends EncoderBase<Event> {

    /**
     * Empty bytes
     */
    private static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * Used to create the necessary {@link JsonGenerator}s for generating JSON.
     */
    private MappingJsonFactory jsonFactory = (MappingJsonFactory) new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .getFactory()
            .enable(JsonGenerator.Feature.ESCAPE_NON_ASCII)
            .disable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);

    private ThrowableHandlingConverter throwableConverter = new ExtendedThrowableProxyConverter();

    /**
     * version
     */
    private int version = 1;

    /**
     * Min buffer size: include LINE_SEPARATOR length
     */
    private int minBufferSize = 1024;

    /*****************************自定义日志字段**********************************/
    /**
     * 主机
     */
    private String host;
    /**
     * 应用名称
     */
    private String appname;

    @Override
    public byte[] headerBytes() {
        return EMPTY_BYTES;
    }

    @Override
    public byte[] encode(Event event) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(minBufferSize);
        try {
            JsonGenerator jsonGenerator = jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("@timestamp",EncoderUtils.DATE_FORMAT.format(event.getTimeStamp()));
            jsonGenerator.writeNumberField("@version", 1);
            jsonGenerator.writeStringField("appname", appname);
            jsonGenerator.writeStringField( "host", host);
            jsonGenerator.writeStringField( "level", event.getLevel().toString());
            jsonGenerator.writeNumberField("level_value", event.getLevel().toInt());
            jsonGenerator.writeStringField( "logger_name", event.getLoggerName());
            jsonGenerator.writeStringField(  "message", event.getMessage());
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                jsonGenerator.writeStringField("stack_trace", throwableConverter.convert(event));
            }
            jsonGenerator.writeEndObject();
            jsonGenerator.flush();
            outputStream.write(EncoderUtils.LINE_SEPARATOR_BYTES);
        } catch (Exception e) {
            addWarn("Error encountered while encoding log event. Event: " + event, e);
            return EMPTY_BYTES;
        }
        return outputStream.toByteArray();
    }

    @Override
    public byte[] footerBytes() {
        return EMPTY_BYTES;
    }

    @Override
    public void start() {
        this.throwableConverter.start();
        super.start();
    }

    @Override
    public void stop() {
        this.throwableConverter.stop();
        super.stop();
    }

    public ThrowableHandlingConverter getThrowableConverter() {
        return throwableConverter;
    }

    public void setThrowableConverter(ThrowableHandlingConverter throwableConverter) {
        this.throwableConverter = throwableConverter;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getMinBufferSize() {
        return minBufferSize;
    }

    public void setMinBufferSize(int minBufferSize) {
        this.minBufferSize = minBufferSize;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getAppname() {
        return appname;
    }
}
