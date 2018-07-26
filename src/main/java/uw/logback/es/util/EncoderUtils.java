package uw.logback.es.util;

import java.nio.charset.Charset;

/**
 * 编码工具类
 *
 * @author liliang
 * @since 2018-07-26
 */
public class EncoderUtils {

    /**
     * 日志编码
     */
    public static final Charset LOG_CHARSET = Charset.forName("UTF-8");

    /**
     * 换行符
     */
    public static final String lineSeparator = System.getProperty("line.separator");

    /**
     * 换行符字节
     */
    public static final byte[] lineSeparatorBytes = lineSeparator.getBytes(EncoderUtils.LOG_CHARSET);
}
