package io.github.surezzzzzz.sdk.auth.iam.server.configuration;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.resource.server.configuration.ResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceSecurityPathHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * IAM Server 开放 API 启动校验。
 *
 * <p>开放 API（{@code /iam/api/**}）由公共资源层鉴权链接管，启用方式为宿主显式配置
 * protected-paths 并外插认证 Provider（如 aksk-resource）。本开放 API 不注入默认路径：
 * 公共层对"配了路径却无任何 Provider 适配器"直接 fail-fast，注入默认会把未外插
 * Provider 的宿主（纯会话形态）全部拦在启动外。校验语义：
 * <ul>
 *   <li>未配置 protected-paths：纯会话形态合法，WARN 提示——{@code /iam/api/**}
 *   落 Order(5) {@code /iam/**} 会话链或 Order(6) denyAll 兜底，开放 API 请求一律 401/403（失败关闭）</li>
 *   <li>配置了 protected-paths 但公共层显式关闭：矛盾组合，fail-fast</li>
 *   <li>配置了 protected-paths 但归一化后不含 {@code /iam/api/**}：开放 API 半开，fail-fast</li>
 * </ul>
 * Provider 适配器缺失由公共层自身的启动校验拦截，本类不重复。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class SimpleIamServerStartupValidator {

    private final Environment environment;

    /**
     * 启动校验与软提示。
     */
    @PostConstruct
    public void validate() {
        ResourceServerProperties resourceServerProperties = Binder
                .get(environment)
                .bind(SimpleResourceServerStarterConstant.CONFIG_PREFIX,
                        Bindable.of(ResourceServerProperties.class))
                .orElseGet(ResourceServerProperties::new);
        String contextPath = environment.getProperty(
                SimpleResourceServerStarterConstant.PROPERTY_SERVER_SERVLET_CONTEXT_PATH);
        List<String> normalizedPaths = ResourceSecurityPathHelper.normalizePaths(
                resourceServerProperties.getSecurity().getProtectedPaths(), contextPath,
                resourceServerProperties.getSecurity().isContextPathAware());

        if (normalizedPaths.isEmpty()) {
            log.warn("开放 API 未启用（未配置 protected-paths）：/iam/api/** 请求将由会话链拒绝（失败关闭）；"
                    + "启用方式见 README 开放 API 部署段");
            return;
        }
        if (!resourceServerProperties.isEnabled()) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    ServerErrorMessage.RESOURCE_SERVER_DISABLED_WITH_PATHS);
        }
        if (!normalizedPaths.contains(SimpleIamServerConstant.PATH_OPEN_API)) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    String.format(ServerErrorMessage.PROTECTED_PATHS_MISSING_OPEN_API,
                            resourceServerProperties.getSecurity().getProtectedPaths()));
        }
        log.info("启动校验通过：开放 API {} 已由公共资源层鉴权链保护", SimpleIamServerConstant.PATH_OPEN_API);
    }
}
