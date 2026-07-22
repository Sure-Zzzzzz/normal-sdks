package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementConfigurationException;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

/**
 * SmartRedisLimiter Management 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SmartRedisLimiterManagementConstant.CONFIG_PREFIX)
public class SmartRedisLimiterManagementProperties {

    /**
     * 是否启用
     */
    private Boolean enable = SmartRedisLimiterManagementConstant.DEFAULT_ENABLE;
    /**
     * 快照 API 配置
     */
    private ApiConfig api = new ApiConfig();
    /**
     * 管理页面配置
     */
    private UiConfig ui = new UiConfig();
    /**
     * 固定管理员配置
     */
    private AdminConfig admin = new AdminConfig();
    /**
     * 对外 REST 固定 token 兜底配置
     */
    private RestConfig rest = new RestConfig();
    /**
     * 分页配置
     */
    private PageConfig page = new PageConfig();

    /**
     * 初始化并验证配置
     */
    @PostConstruct
    public void init() {
        if (!Boolean.TRUE.equals(enable)) {
            return;
        }
        boolean apiEnabled = Boolean.TRUE.equals(api.getEnable());
        boolean uiEnabled = Boolean.TRUE.equals(ui.getEnable());
        if (!apiEnabled && !uiEnabled) {
            throw configurationException(ErrorMessage.CONFIG_ENTRY_REQUIRED);
        }
        if (uiEnabled && !apiEnabled) {
            throw configurationException(ErrorMessage.CONFIG_UI_API_REQUIRED);
        }
        if (apiEnabled) {
            api.setBasePath(normalizeBasePath(api.getBasePath()));
        }
        if (uiEnabled) {
            ui.setBasePath(normalizeBasePath(ui.getBasePath()));
            if (!hasText(admin.getUsername()) || !hasText(admin.getPassword())) {
                throw configurationException(ErrorMessage.CONFIG_ADMIN_REQUIRED);
            }
        }
        if (apiEnabled && uiEnabled && pathsOverlap(api.getBasePath(), ui.getBasePath())) {
            throw configurationException(ErrorMessage.CONFIG_BASE_PATH_OVERLAP);
        }
        if (page.getDefaultSize() == null || page.getDefaultSize() <= 0
                || page.getMaxSize() == null || page.getMaxSize() < page.getDefaultSize()) {
            throw configurationException(ErrorMessage.PAGE_INVALID);
        }
    }

    private String normalizeBasePath(String basePath) {
        if (!hasText(basePath)) {
            throw configurationException(ErrorMessage.CONFIG_BASE_PATH_INVALID);
        }
        String normalizedBasePath = basePath.trim();
        if (!normalizedBasePath.startsWith("/")
                || normalizedBasePath.length() > 1 && normalizedBasePath.endsWith("/")
                || normalizedBasePath.contains("*") || normalizedBasePath.contains("?")) {
            throw configurationException(ErrorMessage.CONFIG_BASE_PATH_INVALID);
        }
        return normalizedBasePath;
    }

    private boolean pathsOverlap(String firstPath, String secondPath) {
        return firstPath.equals(secondPath)
                || firstPath.startsWith(secondPath + "/")
                || secondPath.startsWith(firstPath + "/");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private SmartRedisLimiterManagementConfigurationException configurationException(String reason) {
        return new SmartRedisLimiterManagementConfigurationException(
                ErrorCode.CONFIG_VALIDATION_FAILED,
                String.format(ErrorMessage.CONFIG_VALIDATION_FAILED, reason));
    }

    /**
     * 快照 API 配置
     */
    @Data
    public static class ApiConfig {
        /**
         * 是否启用
         */
        private Boolean enable = SmartRedisLimiterManagementConstant.DEFAULT_API_ENABLE;
        /**
         * API 根路径
         */
        private String basePath = SmartRedisLimiterManagementConstant.DEFAULT_API_BASE_PATH;
    }

    /**
     * 管理页面配置
     */
    @Data
    public static class UiConfig {
        /**
         * 是否启用
         */
        private Boolean enable = SmartRedisLimiterManagementConstant.DEFAULT_UI_ENABLE;
        /**
         * 页面根路径
         */
        private String basePath = SmartRedisLimiterManagementConstant.DEFAULT_UI_BASE_PATH;
    }

    /**
     * 固定管理员配置
     */
    @Data
    public static class AdminConfig {
        /**
         * 管理员用户名
         */
        private String username;
        /**
         * 管理员密码
         */
        @ToString.Exclude
        private String password;
    }

    /**
     * 对外 REST 固定 token 兜底配置
     *
     * <p>仅在 resource-server 显式关闭（io...resource.server.enabled=false）时生效，resource-server 接管快照和 CRUD 时本配置不参与认证。
     */
    @Data
    public static class RestConfig {
        /**
         * 对外 REST 固定 token
         */
        @ToString.Exclude
        private String policyToken;
    }

    /**
     * 分页配置
     */
    @Data
    public static class PageConfig {
        /**
         * 默认分页大小
         */
        private Integer defaultSize = SmartRedisLimiterManagementConstant.DEFAULT_PAGE_SIZE;
        /**
         * 最大分页大小
         */
        private Integer maxSize = SmartRedisLimiterManagementConstant.DEFAULT_MAX_PAGE_SIZE;
    }
}
