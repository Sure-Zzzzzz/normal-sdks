package io.github.surezzzzzz.sdk.feedback.server.service;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册表数据来源核对服务（修正㉔：区分 IAM 同源行与本表登记行）。
 *
 * <p>口径：目录取数点（全局目录，无用户维度）按编码命中判定同源——同源行的展示名由
 * IAM 单一事实源承担（登记时自动抄录、更新时拒绝改写、可显式重同步），受理门
 * （启用/停用）与处理组始终是注册表自主面。</p>
 *
 * <p>降级语义（聚散原则）：adaptor 不在场（散形态）或目录出站失败时返回 {@code null}
 * ——来源未知，注册表保持全字段可编辑，不因核对不可用而锁死；每次调用即时出站一次
 * （管理面低频操作，不设缓存，避免目录漂移期间的陈旧锁定）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
public class ApplicationSourceService {

    /**
     * 来源枚举值（响应 wire 值）：IAM 同源 / 本表登记 / 未核对。
     */
    public static final String SOURCE_IAM = "IAM";

    public static final String SOURCE_MANUAL = "MANUAL";

    public static final String SOURCE_UNKNOWN = "UNKNOWN";

    private final ObjectProvider<AccessibleApplicationProvider> accessibleApplicationProvider;

    public ApplicationSourceService(ObjectProvider<AccessibleApplicationProvider> accessibleApplicationProvider) {
        this.accessibleApplicationProvider = accessibleApplicationProvider;
    }

    /**
     * 判定单行来源。
     *
     * @param applicationCode 应用编码
     * @param catalogIndex    目录索引（可为 null=不可核对）
     * @return SOURCE_IAM / SOURCE_MANUAL / SOURCE_UNKNOWN
     */
    public static String sourceOf(String applicationCode, Map<String, AccessibleApplication> catalogIndex) {
        if (catalogIndex == null) {
            return SOURCE_UNKNOWN;
        }
        return catalogIndex.containsKey(applicationCode) ? SOURCE_IAM : SOURCE_MANUAL;
    }

    /**
     * 拉取全局目录索引（编码 → 目录条目）。
     *
     * @return 目录索引；null=不可核对（散形态或出站失败，来源未知）
     */
    public Map<String, AccessibleApplication> catalogIndex() {
        AccessibleApplicationProvider provider = accessibleApplicationProvider.getIfAvailable();
        if (provider == null) {
            return null;
        }
        List<AccessibleApplication> catalog;
        try {
            catalog = provider.catalogApplications();
        } catch (RuntimeException exception) {
            log.warn("注册表目录核对出站异常，来源核对降级（注册表不锁）来源=AccessibleApplicationProvider");
            return null;
        }
        if (catalog == null) {
            return null;
        }
        Map<String, AccessibleApplication> index = new HashMap<String, AccessibleApplication>();
        for (AccessibleApplication application : catalog) {
            index.put(application.getApplicationCode(), application);
        }
        return index;
    }
}
