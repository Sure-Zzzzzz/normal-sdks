package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.AkskOwnerAuthorizationSynchronizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.spi.OwnerAuthorizationProvider;
import io.github.surezzzzzz.sdk.auth.resource.server.configuration.ResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceSecurityPathHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Simple AKSK Server 启动校验。
 * <p>
 * 旧 @Order(2) /api 链删除后其"无条件兜底 /api"的防护消失，本校验补位：
 * 确保公共资源层链必然接管 /api/**（显式关闭或覆盖摘除均 fail-fast）。
 * 同时校验 keyId 不携带路由前缀（签发侧自动包装 aksk/，误配会造成 kid 双前缀路由失败）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class SimpleAkskServerStartupValidator {

    private static final String PROTECTED_PATH_API = "/api/**";
    private static final String ADMIN_ENABLED_PROPERTY =
            "io.github.surezzzzzz.sdk.auth.aksk.server.admin.enabled";
    private static final String ADMIN_DISABLED_HINT =
            "AKSK 管理台已停用（admin.enabled=false）；门户接入方式见 README";

    private final SimpleAkskServerProperties properties;
    private final Environment environment;
    private final ObjectProvider<OwnerAuthorizationProvider> ownerAuthorizationProvider;

    /**
     * 启动校验与软提示。
     */
    @PostConstruct
    public void validate() {
        validateKeyId();
        validateProtectedPaths();
        validateOwnerAuthorization();
        log.info("启动校验通过：keyId 形态合法，/api/** 已由公共资源层鉴权链保护");
        if (environment.getProperty(ADMIN_ENABLED_PROPERTY, Boolean.class, Boolean.TRUE)) {
            return;
        }
        log.info(ADMIN_DISABLED_HINT);
    }

    private void validateKeyId() {
        String keyId = properties.getJwt().getKeyId();
        log.debug("校验 keyId：{}", keyId);
        if (keyId == null || !keyId.matches(AkskConstant.ROUTE_KEY_ID_ALLOWED_CHARACTER_PATTERN)) {
            throw new ConfigurationException(String.format(ServerErrorMessage.JWT_KEY_ID_INVALID,
                    keyId, AkskConstant.ROUTE_KEY_ID_ALLOWED_CHARACTER_PATTERN));
        }
    }

    private void validateProtectedPaths() {
        ResourceServerProperties resourceServerProperties = org.springframework.boot.context.properties.bind.Binder
                .get(environment)
                .bind(SimpleResourceServerStarterConstant.CONFIG_PREFIX,
                        org.springframework.boot.context.properties.bind.Bindable.of(ResourceServerProperties.class))
                .orElseGet(ResourceServerProperties::new);
        if (!resourceServerProperties.isEnabled()) {
            throw new ConfigurationException(ServerErrorMessage.RESOURCE_SERVER_DISABLED);
        }
        String contextPath = environment.getProperty(
                SimpleResourceServerStarterConstant.PROPERTY_SERVER_SERVLET_CONTEXT_PATH);
        List<String> normalizedPaths = ResourceSecurityPathHelper.normalizePaths(
                resourceServerProperties.getSecurity().getProtectedPaths(), contextPath,
                resourceServerProperties.getSecurity().isContextPathAware());
        log.debug("公共资源层 protected-paths 归一化：{}（contextPath={}）", normalizedPaths, contextPath);
        if (!normalizedPaths.contains(PROTECTED_PATH_API)) {
            throw new ConfigurationException(String.format(ServerErrorMessage.PROTECTED_PATHS_MISSING_API,
                    resourceServerProperties.getSecurity().getProtectedPaths()));
        }
    }

    /**
     * OWNER_INHERITED 开启后必须具备完整 HTTPS reader 配置，缺项直接阻止启动而不是运行时降级。
     */
    private void validateOwnerAuthorization() {
        SimpleAkskServerProperties.OwnerAuthorizationProjectionConfig reader =
                properties.getOwnerAuthorization();
        if (!Boolean.TRUE.equals(reader.getEnabled())) {
            return;
        }
        if (!hasText(reader.getOwnerSourceId()) || !hasText(reader.getHumanResourceSourceId())
                || !positive(reader.getPullIntervalMillis())
                || !positive(reader.getLeaseSeconds()) || !inRange(reader.getPullPageSize(), 1, 200)
                || reader.getSynchronizationMode() == null) {
            throw new ConfigurationException("AKSK owner authorization 已启用，但身份或租约配置不完整");
        }
        if (ownerAuthorizationProvider.getIfAvailable() == null
                || !ownerAuthorizationProvider.getIfAvailable().isAvailable()) {
            throw new ConfigurationException("AKSK owner authorization 已启用，但未提供 owner authorization provider");
        }
        if (AkskOwnerAuthorizationSynchronizationMode.EVENTUAL_WITH_LEASE
                .equals(reader.getSynchronizationMode())
                && reader.getPullIntervalMillis().longValue() >= reader.getLeaseSeconds().longValue() * 1000L) {
            throw new ConfigurationException("AKSK owner authorization 的同步间隔必须小于本地授权租约");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean positive(Integer value) {
        return value != null && value.intValue() > 0;
    }

    private boolean inRange(Integer value, int minimum, int maximum) {
        return value != null && value.intValue() >= minimum && value.intValue() <= maximum;
    }

}
