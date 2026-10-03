package io.github.surezzzzzz.sdk.s3.route.test.cases;

import com.amazonaws.services.s3.AmazonS3;
import io.github.surezzzzzz.sdk.s3.route.client.S3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.registry.SimpleS3RouteRegistry;
import io.github.surezzzzzz.sdk.s3.route.resolver.DefaultS3RouteResolver;
import io.github.surezzzzzz.sdk.s3.route.validator.DefaultS3RoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 默认 S3 target 精确解析器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultS3RouteResolverTest {

    @Test
    void resolvesRegisteredKey() {
        SimpleS3RouteRegistry registry = registry();
        DefaultS3RouteResolver resolver = new DefaultS3RouteResolver(registry);

        String resolved = resolver.resolveTargetKey("test-main");

        log.info("已登记 targetKey 解析结果: {}", resolved);
        assertThat(resolved).isEqualTo("test-main");
    }

    @Test
    void blankKeyThrowsTargetKeyIllegal() {
        DefaultS3RouteResolver resolver = new DefaultS3RouteResolver(registry());

        assertThatThrownBy(() -> resolver.resolveTargetKey(null))
                .isInstanceOf(S3RouteException.class)
                .satisfies(exception -> assertThat(((S3RouteException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TARGET_KEY_ILLEGAL));
        assertThatThrownBy(() -> resolver.resolveTargetKey("  "))
                .isInstanceOf(S3RouteException.class)
                .satisfies(exception -> assertThat(((S3RouteException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TARGET_KEY_ILLEGAL));
    }

    @Test
    void unknownKeyThrowsTargetNotRegistered() {
        DefaultS3RouteResolver resolver = new DefaultS3RouteResolver(registry());

        assertThatThrownBy(() -> resolver.resolveTargetKey("unknown"))
                .isInstanceOf(S3RouteException.class)
                .satisfies(exception -> assertThat(((S3RouteException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TARGET_NOT_REGISTERED));
    }

    @Test
    void closedRegistryThrowsRouteClosed() {
        SimpleS3RouteRegistry registry = registry();
        DefaultS3RouteResolver resolver = new DefaultS3RouteResolver(registry);
        registry.destroy();

        assertThatThrownBy(() -> resolver.resolveTargetKey("test-main"))
                .isInstanceOf(S3RouteException.class)
                .satisfies(exception -> assertThat(((S3RouteException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ROUTE_CLOSED));
    }

    private SimpleS3RouteRegistry registry() {
        SimpleS3RouteProperties properties = new SimpleS3RouteProperties();
        properties.setEnable(true);
        SimpleS3RouteProperties.TargetConfig target = new SimpleS3RouteProperties.TargetConfig();
        target.setEndpoint("http://127.0.0.1:19000");
        properties.getTargets().put("test-main", target);
        S3RouteClientFactory factory = (targetKey, config) -> mock(AmazonS3.class);
        return new SimpleS3RouteRegistry(properties,
                new DefaultS3RoutePropertiesValidator(), factory);
    }
}
