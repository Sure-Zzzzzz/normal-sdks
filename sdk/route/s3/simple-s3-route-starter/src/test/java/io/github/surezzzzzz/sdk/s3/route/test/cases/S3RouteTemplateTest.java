package io.github.surezzzzzz.sdk.s3.route.test.cases;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import io.github.surezzzzzz.sdk.s3.route.client.S3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.registry.SimpleS3RouteRegistry;
import io.github.surezzzzzz.sdk.s3.route.resolver.DefaultS3RouteResolver;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import io.github.surezzzzzz.sdk.s3.route.validator.DefaultS3RoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * S3 Route 门面委托与异常边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class S3RouteTemplateTest {

    private AmazonS3 client;
    private S3RouteTemplate template;

    @BeforeEach
    void setUp() {
        client = mock(AmazonS3.class);
        SimpleS3RouteProperties properties = new SimpleS3RouteProperties();
        properties.setEnable(true);
        SimpleS3RouteProperties.TargetConfig target = new SimpleS3RouteProperties.TargetConfig();
        target.setEndpoint("http://127.0.0.1:19000");
        properties.getTargets().put("test-main", target);
        S3RouteClientFactory factory = (targetKey, config) -> client;
        SimpleS3RouteRegistry registry = new SimpleS3RouteRegistry(properties,
                new DefaultS3RoutePropertiesValidator(), factory);
        template = new S3RouteTemplate(registry, new DefaultS3RouteResolver(registry));
    }

    @Test
    void executeDelegatesCallbackWithClient() {
        String result = template.execute("test-main", amazonS3 -> "marker-" + (amazonS3 == client));

        log.info("execute 回调返回值: {}", result);
        assertThat(result).isEqualTo("marker-true");
    }

    @Test
    void amazonS3ReturnsRegisteredClient() {
        AmazonS3 actual = template.amazonS3("test-main");

        log.info("amazonS3 返回与注册客户端同源: {}", actual == client);
        assertThat(actual).isSameAs(client);
    }

    @Test
    void unregisteredTargetThrowsTargetNotRegistered() {
        assertThatThrownBy(() -> template.execute("unknown", amazonS3 -> null))
                .isInstanceOf(S3RouteException.class)
                .satisfies(exception -> assertThat(((S3RouteException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TARGET_NOT_REGISTERED));
    }

    @Test
    void nullCallbackThrowsRequestIllegal() {
        assertThatThrownBy(() -> template.execute("test-main", null))
                .isInstanceOf(S3RouteException.class)
                .satisfies(exception -> assertThat(((S3RouteException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.REQUEST_ILLEGAL));
        log.info("callback 为 null 时以请求非法拒绝");
    }

    @Test
    void amazonS3ExceptionPropagatesUnwrapped() {
        AmazonS3Exception failure = new AmazonS3Exception("access denied");
        failure.setStatusCode(403);

        assertThatThrownBy(() -> template.execute("test-main", amazonS3 -> {
            throw failure;
        })).isSameAs(failure);
        log.info("S3 操作异常原样透传不包装");
    }
}
