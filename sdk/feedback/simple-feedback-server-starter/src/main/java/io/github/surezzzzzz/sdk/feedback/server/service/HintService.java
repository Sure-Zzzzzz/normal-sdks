package io.github.surezzzzzz.sdk.feedback.server.service;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.SubmissionContactHint;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackApplicationRepository;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提交提示服务（hint 单口子：交集后应用详情清单+邮箱预填）。
 *
 * <p>非直观约束：交集为便利性过滤非安全边界——provider 返回 null（散形态/出站降级）
 * 或空集（交集空，常意味着 IAM 侧清单异常）时均回退注册表启用项全量，保提交可用性；
 * 便利性失败不扩大为可用性故障。</p>
 *
 * <p>名称来源：聚形态命中路径的应用展示名/描述取自 IAM「页面准入详情」（与门户侧边栏同源，
 * 注册表不再承担下拉命名）；降级/回退路径仍由注册表行填充，IAM 侧名称缺失时回退注册表展示名。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RequiredArgsConstructor
public class HintService {

    private final FeedbackApplicationRepository applicationRepository;
    private final ObjectProvider<AccessibleApplicationProvider> accessibleApplicationProvider;
    private final ObjectProvider<SubmitterProfileProvider> submitterProfileProvider;

    /**
     * 注册表视角的应用详情集（降级/回退路径的名称来源）。
     */
    private static List<AccessibleApplication> registryView(List<FeedbackApplication> enabled) {
        List<AccessibleApplication> result = new ArrayList<AccessibleApplication>();
        for (FeedbackApplication application : enabled) {
            result.add(AccessibleApplication.builder()
                    .applicationCode(application.getApplicationCode())
                    .displayName(application.getDisplayName())
                    .description(application.getDescription())
                    .build());
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static List<AccessibleApplication> nullSafeInvoke(AccessibleApplicationProvider provider, String subjectId) {
        try {
            return provider.accessibleApplications(subjectId);
        } catch (RuntimeException exception) {
            log.warn("hint 取数出站失败，该项降级（不拒提交）来源=AccessibleApplicationProvider");
            return null;
        }
    }

    private static SubmissionContactHint nullSafeProfileInvoke(SubmitterProfileProvider provider, String subjectId) {
        try {
            return provider.contactHint(subjectId);
        } catch (RuntimeException exception) {
            log.warn("hint 取数出站失败，该项降级（不拒提交）来源=SubmitterProfileProvider");
            return null;
        }
    }

    /**
     * 查询提交提示。
     *
     * @param subjectId 提交人 subjectId（裸值）
     * @return 提交提示
     */
    public SubmissionHint hint(String subjectId) {
        List<FeedbackApplication> enabled = applicationRepository.listEnabled();
        List<AccessibleApplication> result = new ArrayList<AccessibleApplication>();
        AccessibleApplicationProvider provider = accessibleApplicationProvider.getIfAvailable();
        List<AccessibleApplication> accessible = provider == null ? null : nullSafeInvoke(provider, subjectId);
        if (accessible == null) {
            log.debug("hint 应用集取数点降级（散形态或出站失败），回退注册表启用项全量");
            result.addAll(registryView(enabled));
        } else if (accessible.isEmpty()) {
            log.debug("hint 交集为空（IAM 侧清单异常或该用户无可访问应用），回退注册表启用项全量");
            result.addAll(registryView(enabled));
        } else {
            // 以注册表行为序（稳定排序），编码命中 IAM 详情即取 IAM 同源名称
            Map<String, AccessibleApplication> accessibleByCode = new LinkedHashMap<String, AccessibleApplication>();
            for (AccessibleApplication application : accessible) {
                accessibleByCode.put(application.getApplicationCode(), application);
            }
            for (FeedbackApplication application : enabled) {
                AccessibleApplication detail = accessibleByCode.get(application.getApplicationCode());
                if (detail != null) {
                    result.add(AccessibleApplication.builder()
                            .applicationCode(application.getApplicationCode())
                            .displayName(hasText(detail.getDisplayName())
                                    ? detail.getDisplayName() : application.getDisplayName())
                            .description(hasText(detail.getDescription())
                                    ? detail.getDescription() : application.getDescription())
                            .build());
                }
            }
            if (result.isEmpty()) {
                log.debug("hint 交集命中为零（可访问集与注册表无重叠），回退全量");
                result.addAll(registryView(enabled));
            }
        }
        SubmitterProfileProvider profileProvider = submitterProfileProvider.getIfAvailable();
        SubmissionContactHint contactHint = profileProvider == null ? null
                : nullSafeProfileInvoke(profileProvider, subjectId);
        String email = contactHint == null ? null : contactHint.getEmail();
        log.debug("hint 终态 应用数={} 邮箱有无={}", result.size(), email != null);
        return new SubmissionHint(result, email);
    }

    /**
     * 提交提示值对象。
     */
    @lombok.Value
    public static class SubmissionHint {

        /**
         * 可选应用详情集（已交集/回退；聚形态命中路径名称与门户同源）
         */
        private final List<AccessibleApplication> applications;

        /**
         * 邮箱预填值（可空=空输入框）
         */
        private final String contactEmail;
    }
}
