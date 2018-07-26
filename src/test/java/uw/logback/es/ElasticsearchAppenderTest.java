package uw.logback.es;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uw.logback.es.appender.AbstractElasticsearchAppender;
import uw.logback.es.appender.ElasticsearchAppender;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 *
 * @author liliang
 * @since 2018/7/25
 */
@RunWith(MockitoJUnitRunner.class)
public class ElasticsearchAppenderTest {

    private AbstractElasticsearchAppender appender;

    @Before
    public void setUp() {
        appender = new ElasticsearchAppender();
    }

    @Test
    public void testLogger() {
        String loggerName = "elastic-debug-log";
        ILoggingEvent eventToLog = mock(ILoggingEvent.class);
        given(eventToLog.getLoggerName()).willReturn(loggerName);
        appender.setEndpoint("http://192.168.88.16:9200/appaname/logs");
        appender.start();

        appender.doAppend(eventToLog);
    }
}