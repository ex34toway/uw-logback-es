package uw.logback.es.encoder;

import ch.qos.logback.core.encoder.EncoderBase;
import uw.httpclient.http.ObjectMapper;
import uw.logback.es.util.EncodeUtil;

import java.io.ByteArrayOutputStream;


/**
 * Elasticsearch 编码器
 *
 * @author liliang
 * @since 2018-07-26
 */
public class ElasticsearchEncoder<Event> extends EncoderBase<Event> {

    /**
     * Empty bytes
     */
    private static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * Min buffer size: include lineSeparator length
     */
    private int minBufferSize = 1024;

    /*****************************自定义日志字段**********************************/
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
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // TODO JSON Encoding
            ObjectMapper.DEFAULT_JSON_MAPPER.write(outputStream,event);
            outputStream.write(EncodeUtil.lineSeparatorBytes);
        } catch (Exception e) {
            addWarn("Error encountered while encoding log event. "
                    + "Event: " + event, e);
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
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    public int getMinBufferSize() {
        return minBufferSize;
    }

    public void setMinBufferSize(int minBufferSize) {
        this.minBufferSize = minBufferSize;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getAppname() {
        return appname;
    }
}
