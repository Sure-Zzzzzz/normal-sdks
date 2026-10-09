package io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory;

import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterServiceDeclaration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;

import java.util.*;

/**
 * 配置式目录提供方：以部署配置（management.typed.services）为数据源
 *
 * <p>设计约定：宿主以部署配置维护目录，不自动注册、无心跳；本实现作为默认装配，
 * 宿主自带目录实现（如对接组织事实的客户目录）时以自有 Bean 覆盖。
 *
 * @author surezzzzzz
 */
public class ConfigurationSmartRedisLimiterDirectoryProvider
        implements SmartRedisLimiterDirectoryProvider {

    private final SmartRedisLimiterManagementProperties properties;

    /**
     * 构造配置式目录提供方
     *
     * @param properties 管理配置
     */
    public ConfigurationSmartRedisLimiterDirectoryProvider(SmartRedisLimiterManagementProperties properties) {
        this.properties = properties;
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    @Override
    public List<SmartRedisLimiterServiceDeclaration> listServices() {
        List<SmartRedisLimiterServiceDeclaration> declarations = new ArrayList<>();
        for (SmartRedisLimiterManagementProperties.TypedServiceConfig config
                : properties.getTyped().getServices()) {
            declarations.add(toDeclaration(config));
        }
        return declarations;
    }

    @Override
    public SmartRedisLimiterServiceDeclaration findService(String serviceCode) {
        Optional<SmartRedisLimiterManagementProperties.TypedServiceConfig> matched =
                properties.getTyped().getServices().stream()
                        .filter(config -> config.getServiceCode().equals(serviceCode))
                        .findFirst();
        return matched.map(this::toDeclaration).orElse(null);
    }

    @Override
    public List<SmartRedisLimiterDirectoryObject> listObjects(String serviceCode,
                                                              String dimension,
                                                              String customType,
                                                              String keyword,
                                                              int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        Optional<SmartRedisLimiterManagementProperties.TypedServiceConfig> matched =
                properties.getTyped().getServices().stream()
                        .filter(config -> config.getServiceCode().equals(serviceCode))
                        .findFirst();
        if (!matched.isPresent()) {
            return Collections.emptyList();
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<SmartRedisLimiterDirectoryObject> results = new ArrayList<>();
        for (SmartRedisLimiterManagementProperties.TypedObjectConfig object : matched.get().getObjects()) {
            if (results.size() >= limit) {
                break;
            }
            if (!object.getDimension().equals(dimension)) {
                continue;
            }
            if ("CUSTOM".equals(dimension)
                    && !nullable(object.getCustomType()).equals(nullable(customType))) {
                continue;
            }
            if (!normalizedKeyword.isEmpty()
                    && !object.getId().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                    && !nullable(object.getName()).toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
                continue;
            }
            results.add(new SmartRedisLimiterDirectoryObject(
                    object.getDimension(), nullable(object.getCustomType()),
                    object.getId(), nullable(object.getName())));
        }
        return results;
    }

    private SmartRedisLimiterServiceDeclaration toDeclaration(
            SmartRedisLimiterManagementProperties.TypedServiceConfig config) {
        SmartRedisLimiterServiceDeclaration declaration = new SmartRedisLimiterServiceDeclaration();
        declaration.setServiceCode(config.getServiceCode());
        declaration.setControlMode(config.getControlMode());
        declaration.setDisplayName(nullable(config.getDisplayName()));
        declaration.setPolicyEpoch(config.getPolicyEpoch());
        declaration.setResources(config.getResources());
        declaration.setNamespaces(config.getNamespaces());
        declaration.setCustomTypes(config.getCustomTypes());
        return declaration;
    }
}
