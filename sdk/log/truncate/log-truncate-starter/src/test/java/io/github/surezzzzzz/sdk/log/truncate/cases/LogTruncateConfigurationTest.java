package io.github.surezzzzzz.sdk.log.truncate.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.log.truncate.configuration.LogTruncateComponent;
import io.github.surezzzzzz.sdk.log.truncate.configuration.LogTruncateConfiguration;
import io.github.surezzzzzz.sdk.log.truncate.configuration.LogTruncateProperties;
import io.github.surezzzzzz.sdk.log.truncate.constant.LogTruncateConstant;
import io.github.surezzzzzz.sdk.log.truncate.support.LogTruncator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日志截断自动配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class LogTruncateConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    LogTruncateConfiguration.class));

    /**
     * 验证默认 Bean 和配置绑定
     */
    @Test
    public void shouldCreateDefaultBeanAndBindProperties() {
        contextRunner.withPropertyValues(
                        LogTruncateConstant.CONFIG_PREFIX + ".max-total-bytes=123",
                        LogTruncateConstant.CONFIG_PREFIX + ".max-field-chars=45",
                        LogTruncateConstant.CONFIG_PREFIX + ".max-depth=6",
                        LogTruncateConstant.CONFIG_PREFIX + ".ellipsis=***",
                        LogTruncateConstant.CONFIG_PREFIX + ".truncated-note-template=cut {dropped}",
                        LogTruncateConstant.CONFIG_PREFIX + ".depth-exceeded-placeholder=depth")
                .run(context -> {
                    log.info("自动配置结果：Bean数量={}, 截断器名称={}",
                            context.getBeanDefinitionCount(), LogTruncateConstant.LOG_TRUNCATOR_BEAN_NAME);
                    assertThat(context).hasSingleBean(LogTruncator.class);
                    assertThat(context).hasBean(LogTruncateConstant.LOG_TRUNCATOR_BEAN_NAME);
                    LogTruncateProperties properties = context.getBean(LogTruncateProperties.class);
                    assertThat(properties.getMaxTotalBytes()).isEqualTo(123);
                    assertThat(properties.getMaxFieldChars()).isEqualTo(45);
                    assertThat(properties.getMaxDepth()).isEqualTo(6);
                    assertThat(properties.getEllipsis()).isEqualTo("***");
                    assertThat(properties.getTruncatedNoteTemplate()).isEqualTo("cut {dropped}");
                    assertThat(properties.getDepthExceededPlaceholder()).isEqualTo("depth");
                });
    }

    /**
     * 验证默认配置保持兼容
     */
    @Test
    public void shouldKeepDefaultProperties() {
        contextRunner.run(context -> {
            LogTruncateProperties properties = context.getBean(LogTruncateProperties.class);
            assertThat(properties.getMaxTotalBytes()).isEqualTo(LogTruncateConstant.DEFAULT_MAX_TOTAL_BYTES);
            assertThat(properties.getMaxFieldChars()).isEqualTo(LogTruncateConstant.DEFAULT_MAX_FIELD_CHARS);
            assertThat(properties.getMaxDepth()).isEqualTo(LogTruncateConstant.DEFAULT_MAX_DEPTH);
            assertThat(properties.getEllipsis()).isEqualTo(LogTruncateConstant.DEFAULT_ELLIPSIS);
            assertThat(properties.getTruncatedNoteTemplate())
                    .isEqualTo(LogTruncateConstant.DEFAULT_TRUNCATED_NOTE_TEMPLATE);
            assertThat(properties.getDepthExceededPlaceholder())
                    .isEqualTo(LogTruncateConstant.DEFAULT_DEPTH_EXCEEDED_PLACEHOLDER);
        });
    }

    /**
     * 验证 SDK 不注册或替换业务 ObjectMapper
     */
    @Test
    public void shouldNotRegisterApplicationObjectMapper() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(ObjectMapper.class));
    }

    /**
     * 验证自定义组件标记仍参与扫描注册
     */
    @Test
    public void shouldRegisterLogTruncateComponent() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ScannedLogTruncateExtension.class));
    }

    /**
     * 仅通过标记注册的扩展组件
     */
    @LogTruncateComponent
    public static class ScannedLogTruncateExtension {
    }

}
