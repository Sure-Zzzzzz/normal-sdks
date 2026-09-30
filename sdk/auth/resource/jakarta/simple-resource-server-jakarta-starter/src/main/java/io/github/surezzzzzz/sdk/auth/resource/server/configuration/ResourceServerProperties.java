package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用资源服务配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleResourceServerStarterConstant.CONFIG_PREFIX)
public class ResourceServerProperties {

    /**
     * 是否启用资源认证链。
     */
    private boolean enabled = SimpleResourceServerStarterConstant.DEFAULT_ENABLED;
    /**
     * 安全路径配置。
     */
    private Security security = new Security();

    /**
     * 安全路径配置。
     */
    @Data
    public static class Security {

        /**
         * 显式保护的业务路径。
         */
        private List<String> protectedPaths = new ArrayList<String>();
        /**
         * 显式公开的业务路径。
         */
        private List<String> permitAllPaths = new ArrayList<String>();
        /**
         * 是否按Servlet context-path归一化路径。
         */
        private boolean contextPathAware = SimpleResourceServerStarterConstant.DEFAULT_CONTEXT_PATH_AWARE;
        /**
         * 配置化精确API权限规则。
         */
        private List<ApiPermissionRule> apiPermissionRules = new ArrayList<ApiPermissionRule>();
    }

    /**
     * 配置化精确API权限规则。
     */
    @Data
    public static class ApiPermissionRule {

        /**
         * 应用内部路径Ant模式。
         */
        private String pathPattern;
        /**
         * 精确HTTP方法。
         */
        private String method;
        /**
         * 精确API权限。
         */
        private String apiPermission;
    }
}
