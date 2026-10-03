package io.github.surezzzzzz.sdk.s3.route.test.cases;

import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.constant.S3RouteAuthenticationType;
import io.github.surezzzzzz.sdk.s3.route.constant.S3RouteSignerType;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.validator.DefaultS3RoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

/**
 * 默认 S3 Route 配置校验器边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultS3RoutePropertiesValidatorTest {

    private final DefaultS3RoutePropertiesValidator validator = new DefaultS3RoutePropertiesValidator();

    @Test
    void validPropertiesPass() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").setEndpoint("https://storage-a.internal:9000/");
        SimpleS3RouteProperties.TargetConfig authenticated = target("http://storage-b.internal:9000");
        authenticated.getAuthentication().setType(S3RouteAuthenticationType.ACCESS_KEY);
        authenticated.getAuthentication().setAccessKey("fixture-access-key");
        authenticated.getAuthentication().setSecretKey("fixture-secret-key");
        properties.getTargets().put("test-main-b", authenticated);

        assertThatCode(() -> validator.validate(properties))
                .doesNotThrowAnyException();
        log.info("完整合法配置通过校验");
    }

    @Test
    void disabledPropertiesSkipValidation() {
        SimpleS3RouteProperties properties = new SimpleS3RouteProperties();

        assertThatCode(() -> validator.validate(properties))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyTargetsFail() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().clear();

        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    @Test
    void nonPositiveShutdownTimeoutFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.setShutdownTimeoutMs(0);

        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    @Test
    void invalidTargetKeyFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().put("invalid/key", target("http://127.0.0.1:19000"));

        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    @Test
    void nullTargetValueFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().put("test-null", null);

        assertThatIllegalConfigurationIsThrownBy(properties);
        log.info("target 配置为 null 值时被拒绝");
    }

    @Test
    void invalidEndpointFails() {
        assertEndpointRejected(null);
        assertEndpointRejected("  ");
        assertEndpointRejected("ftp://127.0.0.1:19000");
        assertEndpointRejected("http://127.0.0.1:19000/prefix");
        assertEndpointRejected("http://127.0.0.1:19000?query=1");
        assertEndpointRejected("http://127.0.0.1:19000#fragment");
        assertEndpointRejected("::::");
        log.info("全部非法 endpoint 场景均被拒绝");
    }

    @Test
    void invalidRegionFails() {
        assertRegionRejected("US-EAST");
        assertRegionRejected("us_east");
        assertRegionRejected("-us-east");
        assertRegionRejected("");
        log.info("全部非法 region 场景均被拒绝");
    }

    @Test
    void noneAuthenticationWithCredentialsFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").getAuthentication().setAccessKey("fixture-access-key");

        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    @Test
    void accessKeyAuthenticationWithoutSecretFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        SimpleS3RouteProperties.TargetConfig target = properties.getTargets().get("test-main");
        target.getAuthentication().setType(S3RouteAuthenticationType.ACCESS_KEY);
        target.getAuthentication().setAccessKey("fixture-access-key");

        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    @Test
    void controlCharacterInCredentialsFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        SimpleS3RouteProperties.TargetConfig target = properties.getTargets().get("test-main");
        target.getAuthentication().setType(S3RouteAuthenticationType.ACCESS_KEY);
        target.getAuthentication().setAccessKey("fixture-access-key");
        target.getAuthentication().setSecretKey("fixture-secret\nkey");

        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    @Test
    void invalidClientParametersFail() {
        assertClientRejected(client -> client.setConnectTimeoutMs(0));
        assertClientRejected(client -> client.setSocketTimeoutMs(-1));
        assertClientRejected(client -> client.setMaxConnections(0));
        assertClientRejected(client -> client.setRequestTimeoutMs(-1));
        assertClientRejected(client -> client.setClientExecutionTimeoutMs(-1));
        assertClientRejected(client -> client.setConnectionMaxIdleMs(0));
        assertClientRejected(client -> client.setConnectionTtlMs(-2L));
        log.info("全部非法客户端参数场景均被拒绝");
    }

    @Test
    void v2SignerTypeAccepted() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").setSignerType(S3RouteSignerType.S3_V2);

        assertThatCode(() -> validator.validate(properties))
                .doesNotThrowAnyException();
        log.info("V2 签名配置通过校验");
    }

    @Test
    void nullSignerTypeFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").setSignerType(null);

        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    @Test
    void httpsEndpointWithTrustedCaAccepted() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").setEndpoint("https://storage-a.internal:9000");
        properties.getTargets().get("test-main").setTrustedCaFile("/fixture/trusted-ca.crt");

        assertThatCode(() -> validator.validate(properties))
                .doesNotThrowAnyException();
        log.info("HTTPS + 私有 CA 配置通过校验（文件内容在客户端创建期加载）");
    }

    @Test
    void httpEndpointWithTrustedCaFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").setTrustedCaFile("/fixture/trusted-ca.crt");

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(S3RouteException.class)
                .satisfies(exception -> assertThat(((S3RouteException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TARGET_CONFIGURATION_ILLEGAL));
        log.info("HTTP + 私有 CA 组合被拒绝");
    }

    @Test
    void controlCharacterInTrustedCaFileFails() {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").setEndpoint("https://storage-a.internal:9000");
        properties.getTargets().get("test-main").setTrustedCaFile("/fixture/trusted\ncia.crt");

        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    private void assertEndpointRejected(String endpoint) {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").setEndpoint(endpoint);
        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    private void assertRegionRejected(String region) {
        SimpleS3RouteProperties properties = enabledProperties();
        properties.getTargets().get("test-main").setRegion(region);
        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    private void assertClientRejected(Consumer<SimpleS3RouteProperties.ClientConfig> mutator) {
        SimpleS3RouteProperties properties = enabledProperties();
        mutator.accept(properties.getTargets().get("test-main").getClient());
        assertThatIllegalConfigurationIsThrownBy(properties);
    }

    private void assertThatIllegalConfigurationIsThrownBy(SimpleS3RouteProperties properties) {
        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(S3RouteException.class)
                .satisfies(exception -> assertThat(((S3RouteException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TARGET_CONFIGURATION_ILLEGAL));
    }

    private SimpleS3RouteProperties enabledProperties() {
        SimpleS3RouteProperties properties = new SimpleS3RouteProperties();
        properties.setEnable(true);
        properties.getTargets().put("test-main", target("http://127.0.0.1:19000"));
        return properties;
    }

    private SimpleS3RouteProperties.TargetConfig target(String endpoint) {
        SimpleS3RouteProperties.TargetConfig target = new SimpleS3RouteProperties.TargetConfig();
        target.setEndpoint(endpoint);
        return target;
    }
}
