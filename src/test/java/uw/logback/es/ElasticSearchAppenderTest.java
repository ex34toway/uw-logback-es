package uw.logback.es;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uw.logback.es.appender.ElasticSearchAppender;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 *
 * @author liliang
 * @since 2018/7/25
 */
@RunWith(MockitoJUnitRunner.class)
public class ElasticSearchAppenderTest {

    private ElasticSearchAppender appender;

    @Before
    public void setUp() {
        appender = new ElasticSearchAppender();
    }

    @Test
    public void testLogger() {
        String loggerName = "elastic-debug-log";
        ILoggingEvent eventToLog = mock(ILoggingEvent.class);
        given(eventToLog.getLoggerName()).willReturn(loggerName);
        appender.setEsHost("http://192.168.88.16:9200");
        appender.setIndex("appaname");
        appender.setIndexType("logs");
        appender.start();

        appender.doAppend(eventToLog);
    }
}