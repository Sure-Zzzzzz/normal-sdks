package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import io.github.surezzzzzz.sdk.mysql.route.validator.MySqlRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL Route 配置校验测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class MySqlRoutePropertiesValidatorTest {

    private final MySqlRoutePropertiesValidator validator =
            new MySqlRoutePropertiesValidator(new MySqlRoutePatternMatcher());

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 配置校验测试");
    }

    @Test
    public void shouldAcceptValidConfiguration() {
        validator.validate(validProperties());
    }

    @Test
    public void shouldRejectMissingCredentialAndDuplicateDatabaseBinding() {
        SimpleMysqlRouteProperties missingCredential = validProperties();
        missingCredential.getClusters().get("test-cluster-a").setCredentialRef(" ");
        ConfigurationException credentialException = assertThrows(ConfigurationException.class,
                () -> validator.validate(missingCredential));
        assertEquals(ErrorCode.CONFIG_INVALID, credentialException.getCode());
        assertTrue(credentialException.getMessage().contains("credential-ref"));

        SimpleMysqlRouteProperties duplicate = validProperties();
        SimpleMysqlRouteProperties.DatasourceConfig second = new SimpleMysqlRouteProperties.DatasourceConfig();
        second.setClusterKey("test-cluster-a");
        second.setDatabase("test_ops");
        duplicate.getDatasources().put("test-duplicate", second);
        ConfigurationException duplicateException = assertThrows(ConfigurationException.class,
                () -> validator.validate(duplicate));
        assertEquals(ErrorCode.CONFIG_INVALID, duplicateException.getCode());
    }

    @Test
    public void shouldRejectUnknownRuleTargetAndInvalidPattern() {
        SimpleMysqlRouteProperties unknownTarget = validProperties();
        unknownTarget.getRules().get(0).setDatasourceKey("test-unknown");
        ConfigurationException unknownTargetException = assertThrows(ConfigurationException.class,
                () -> validator.validate(unknownTarget));
        assertEquals(ErrorCode.CONFIG_INVALID, unknownTargetException.getCode());

        SimpleMysqlRouteProperties invalidRegex = validProperties();
        invalidRegex.getRules().get(0).setMatchType("regex");
        invalidRegex.getRules().get(0).setPattern("[");
        ConfigurationException invalidRegexException = assertThrows(ConfigurationException.class,
                () -> validator.validate(invalidRegex));
        assertEquals(ErrorCode.CONFIG_INVALID, invalidRegexException.getCode());
    }

    private SimpleMysqlRouteProperties validProperties() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        SimpleMysqlRouteProperties.ClusterConfig cluster = new SimpleMysqlRouteProperties.ClusterConfig();
        cluster.setHost("example.invalid");
        cluster.setCredentialRef("test-reader-credential");
        properties.getClusters().put("test-cluster-a", cluster);

        SimpleMysqlRouteProperties.DatasourceConfig datasource = new SimpleMysqlRouteProperties.DatasourceConfig();
        datasource.setClusterKey("test-cluster-a");
        datasource.setDatabase("test_ops");
        properties.getDatasources().put("test-ops-a", datasource);

        SimpleMysqlRouteProperties.RouteRule rule = new SimpleMysqlRouteProperties.RouteRule();
        rule.setPattern("test_order");
        rule.setDatasourceKey("test-ops-a");
        properties.getRules().add(rule);
        return properties;
    }
}
