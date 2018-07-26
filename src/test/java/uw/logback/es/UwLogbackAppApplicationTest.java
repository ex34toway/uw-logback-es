package uw.logback.es;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * 测试日志输出
 *
 * @author liliang
 * @since 2018-07-25
 */
@SpringBootTest(classes = UwLogbackAppApplication.class)
@RunWith(SpringRunner.class)
public class UwLogbackAppApplicationTest {

    private static final Logger logger = LoggerFactory.getLogger(UwLogbackAppApplicationTest.class);

    @Test
    public void testLogger() {
        logger.error("test");
        try {
            throw new RuntimeException("RuntimeException message");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
