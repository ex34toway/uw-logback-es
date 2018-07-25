package uw.logback.es;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 自动配置类
 *
 * @author liliang
 * @since 2018/7/25
 */
@Configuration
@EnableConfigurationProperties({LogbackProperties.class})
public class LogbackAutoConfiguration {
}
