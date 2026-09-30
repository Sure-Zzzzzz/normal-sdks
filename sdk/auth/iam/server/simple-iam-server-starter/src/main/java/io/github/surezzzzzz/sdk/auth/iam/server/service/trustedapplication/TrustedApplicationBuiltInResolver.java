package io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;

/**
 * 内置应用唯一判定口径：数据库 {@code built_in} 列为准，引导配置清单兜底。
 *
 * <p>管理面通过更新接口调整 built_in 列；配置清单中的编码（如 iam）无论列值
 * 一律视为内置，保证引导项不可被误摘除。内置应用禁删除/禁停用，其 OAuth2
 * 客户端强制 {@code require-authorization-consent=false}（免授权确认页）。</p>
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class TrustedApplicationBuiltInResolver {

    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final SimpleIamServerProperties properties;

    public boolean isBuiltIn(IamTrustedApplicationEntity application) {
        if (Boolean.TRUE.equals(application.getBuiltIn())) {
            return true;
        }
        return isConfiguredBuiltIn(application.getApplicationCode());
    }

    public boolean isBuiltIn(Long applicationId) {
        return trustedApplicationRepository.findById(applicationId).map(this::isBuiltIn).orElse(false);
    }

    private boolean isConfiguredBuiltIn(String applicationCode) {
        return properties.getBootstrap().getBuiltInApplications().stream()
                .anyMatch(config -> config.getApplicationCode() != null
                        && config.getApplicationCode().equals(applicationCode));
    }
}
