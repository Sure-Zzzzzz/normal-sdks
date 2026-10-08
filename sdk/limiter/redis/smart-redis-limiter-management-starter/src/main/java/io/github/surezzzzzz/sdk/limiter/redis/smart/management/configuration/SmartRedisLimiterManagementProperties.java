package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementConfigurationException;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * 部署形态，默认保留 Console 入口。
     */
    private String mode = SmartRedisLimiterManagementConstant.MODE_CONSOLE;
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
     * v2 类型化目录声明（服务协议模式/资源/维度/命名空间/自定义类型/静态对象目录）
     */
    private TypedConfig typed = new TypedConfig();

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
        if (!SmartRedisLimiterManagementConstant.MODE_CONSOLE.equals(mode)
                && !SmartRedisLimiterManagementConstant.MODE_PORTAL.equals(mode)) {
            throw configurationException(ErrorMessage.CONFIG_MODE_INVALID);
        }
        if (isPortal() && (!apiEnabled || uiEnabled || hasText(rest.getPolicyToken()))) {
            throw configurationException(ErrorMessage.CONFIG_PORTAL_ENTRY_INVALID);
        }
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

    /**
     * 判断是否启用独立门户入口。
     */
    public boolean isPortal() {
        return SmartRedisLimiterManagementConstant.MODE_PORTAL.equals(mode);
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

    /**
     * v2 类型化目录配置：宿主以部署配置声明目录，不自动注册
     */
    @Data
    public static class TypedConfig {
        /**
         * 服务声明列表（serviceCode 唯一）
         */
        private List<TypedServiceConfig> services = new ArrayList<>();
    }

    /**
     * v2 服务目录声明
     */
    @Data
    public static class TypedServiceConfig {
        /**
         * 服务编码（最多 128 字符稳定 ASCII，区分大小写）
         */
        private String serviceCode;
        /**
         * 协议模式编码：LEGACY_V1 / TYPED_V2（默认 TYPED_V2）
         */
        private String controlMode = "TYPED_V2";
        /**
         * 策略代次（类型化快照 policyEpoch）：协议切换时由宿主显式递增，默认 1
         */
        private Long policyEpoch;
        /**
         * 展示名称（可空）
         */
        private String displayName;
        /**
         * 资源声明：资源编码 -> 可用计数维度编码列表
         */
        private Map<String, List<String>> resources = new LinkedHashMap<>();
        /**
         * 维度 -> 计数命名空间（未声明维度使用空命名空间）
         */
        private Map<String, String> namespaces = new LinkedHashMap<>();
        /**
         * CUSTOM 维度允许的自定义类型编码列表
         */
        private List<String> customTypes = new ArrayList<>();
        /**
         * 静态对象目录（客户/主体等稳定对象的 ID 与名称；动态目录由宿主提供 SPI Bean）
         */
        private List<TypedObjectConfig> objects = new ArrayList<>();
    }

    /**
     * v2 静态对象目录条目
     */
    @Data
    public static class TypedObjectConfig {
        /**
         * 计数维度编码
         */
        private String dimension;
        /**
         * CUSTOM 维度的自定义类型编码，其他维度可为空
         */
        private String customType;
        /**
         * 稳定对象标识（最多 256 码点，规则使用该值）
         */
        private String id;
        /**
         * 展示名称（可空）
         */
        private String name;
    }
}
