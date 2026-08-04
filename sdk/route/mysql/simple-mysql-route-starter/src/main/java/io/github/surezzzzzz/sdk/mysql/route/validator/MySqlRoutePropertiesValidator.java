package io.github.surezzzzzz.sdk.mysql.route.validator;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import io.github.surezzzzzz.sdk.mysql.route.support.MySqlRouteStringHelper;

import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * MySQL Route 配置校验器。
 *
 * @author surezzzzzz
 */
public class MySqlRoutePropertiesValidator {

    private final MySqlRoutePatternMatcher patternMatcher;

    public MySqlRoutePropertiesValidator(MySqlRoutePatternMatcher patternMatcher) {
        this.patternMatcher = patternMatcher;
    }

    public void validate(SimpleMysqlRouteProperties properties) {
        if (properties == null) {
            fail(ErrorMessage.PROPERTIES_REQUIRED);
        }
        Map<String, SimpleMysqlRouteProperties.DatasourceConfig> datasources = properties.getDatasources();
        if (datasources == null || datasources.isEmpty()) {
            fail(ErrorMessage.DATASOURCES_REQUIRED);
        }
        if (!MySqlRouteStringHelper.hasText(properties.getPrimaryDatasource())) {
            fail(ErrorMessage.PRIMARY_DATASOURCE_REQUIRED);
        }
        if (!isDatasource(properties.getPrimaryDatasource())) {
            fail(ErrorMessage.PRIMARY_DATASOURCE_NOT_FOUND);
        }
        SimpleMysqlRouteProperties.DatasourceConfig primary = datasources.get(properties.getPrimaryDatasource());
        if (primary == null) {
            fail(ErrorMessage.PRIMARY_DATASOURCE_NOT_FOUND);
        }
        for (Map.Entry<String, SimpleMysqlRouteProperties.DatasourceConfig> entry : datasources.entrySet()) {
            validateDatasource(entry.getKey(), entry.getValue());
        }
        validateRules(datasources, properties.getRules());
    }

    private void validateDatasource(String datasourceName, SimpleMysqlRouteProperties.DatasourceConfig datasource) {
        if (!isDatasource(datasourceName)) {
            fail(ErrorMessage.DATASOURCE_KEY_INVALID);
        }
        if (datasource == null) {
            fail(String.format(ErrorMessage.DATASOURCE_URL_REQUIRED, datasourceName));
        }
        if (!MySqlRouteStringHelper.hasText(datasource.getUrl())) {
            fail(String.format(ErrorMessage.DATASOURCE_URL_REQUIRED, datasourceName));
        }
        if (!MySqlRouteStringHelper.hasText(datasource.getUsername())) {
            fail(String.format(ErrorMessage.DATASOURCE_USERNAME_REQUIRED, datasourceName));
        }
        if (!MySqlRouteStringHelper.hasText(datasource.getPassword())) {
            fail(String.format(ErrorMessage.DATASOURCE_PASSWORD_REQUIRED, datasourceName));
        }
        if (!MySqlRouteStringHelper.hasText(datasource.getDriverClassName())) {
            fail(String.format(ErrorMessage.DATASOURCE_DRIVER_REQUIRED, datasourceName));
        }
        validateHikari(datasourceName, datasource.getHikari());
    }

    private void validateHikari(String datasourceName, Map<String, String> hikari) {
        if (hikari == null) {
            return;
        }
        for (Map.Entry<String, String> entry : hikari.entrySet()) {
            if (!MySqlRouteStringHelper.hasText(entry.getKey()) || !MySqlRouteStringHelper.hasText(entry.getValue())) {
                fail(String.format(ErrorMessage.HIKARI_CONFIGURATION_INVALID, datasourceName));
            }
        }
    }

    private void validateRules(Map<String, SimpleMysqlRouteProperties.DatasourceConfig> datasources,
                               List<SimpleMysqlRouteProperties.RouteRule> rules) {
        if (rules == null) {
            return;
        }
        for (int index = 0; index < rules.size(); index++) {
            SimpleMysqlRouteProperties.RouteRule rule = rules.get(index);
            if (rule == null || !rule.isEnable()) {
                continue;
            }
            if (!MySqlRouteStringHelper.hasText(rule.getPattern())) {
                fail(String.format(ErrorMessage.RULE_PATTERN_REQUIRED, index));
            }
            RouteMatchType matchType = RouteMatchType.fromCode(rule.getMatchType());
            if (matchType == null) {
                fail(String.format(ErrorMessage.RULE_MATCH_TYPE_INVALID, index));
            }
            if (!MySqlRouteStringHelper.hasText(rule.getDatasource())
                    || !datasources.containsKey(rule.getDatasource())) {
                fail(String.format(ErrorMessage.RULE_DATASOURCE_NOT_FOUND, index));
            }
            if (matchType == RouteMatchType.REGEX || matchType == RouteMatchType.WILDCARD) {
                try {
                    patternMatcher.compile(matchType, rule.getPattern());
                } catch (PatternSyntaxException e) {
                    fail(String.format(ErrorMessage.RULE_PATTERN_COMPILE_FAILED, index));
                }
            }
        }
    }

    private boolean isDatasource(String datasourceName) {
        return MySqlRouteStringHelper.hasText(datasourceName)
                && datasourceName.matches(SimpleMysqlRouteConstant.DATASOURCE_KEY_PATTERN);
    }

    private void fail(String detail) {
        throw new ConfigurationException(ErrorCode.CONFIG_INVALID,
                String.format(ErrorMessage.CONFIG_INVALID, detail));
    }
}
