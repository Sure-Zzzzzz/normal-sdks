package io.github.surezzzzzz.sdk.feedback.server.adaptor;

import io.github.surezzzzzz.sdk.feedback.server.adaptor.annotation.SimpleFeedbackIamAdaptorComponent;
import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.model.PageAdmittedApplication;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 可访问应用集 SPI 实现（聚形态真实现；IAM 1.3.7 页面准入详情接口）。
 *
 * <p>非直观约束：配对键=入参 subjectId 绑定（IamUser 响应不含 subjectId，修正㉑同源纪律）。
 * 出站失败按降级处理（返回 null+WARN，不拒提交）——便利性失败不扩大为可用性故障；
 * 客户端 Bean 缺席则本模块装配期即失败（见 AutoConfiguration，不静默降级）。</p>
 *
 * <p>取数口径（修正㉓）：详情端点（code/displayName/description，displayName 与门户侧边栏
 * 同源）而非裸编码端点——下拉命名由 IAM 单一事实源承担；与裸端点同判定同码（iam:portal:api）。</p>
 *
 * <p>目录口径（修正㉔）：catalogApplications 走全局目录端点（iam:application:api +
 * iam:application DATA read all），分页拉全（createdAt 倒序）；目录投影不含描述字段，
 * 描述无同源依据，注册表说明字段不参与锁定。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackIamAdaptorComponent
public class IamAccessibleApplicationProvider implements AccessibleApplicationProvider {

    /**
     * 目录翻页页大小（目录为管理面低频取数，页大小取上限量级即可）。
     */
    public static final int CATALOG_PAGE_SIZE = 100;

    private final IamUserClient iamUserClient;

    private final io.github.surezzzzzz.sdk.iam.client.IamApplicationClient iamApplicationClient;

    public IamAccessibleApplicationProvider(IamUserClient iamUserClient,
                                            io.github.surezzzzzz.sdk.iam.client.IamApplicationClient iamApplicationClient) {
        this.iamUserClient = iamUserClient;
        this.iamApplicationClient = iamApplicationClient;
    }

    /**
     * 查询提交人有页面准入投影的应用详情集（与注册表启用项取交集由 HintService 执行）。
     *
     * @param subjectId 提交人 subjectId（裸值，入参侧绑定）
     * @return 应用详情集；出站失败返回 null（该取数点降级为注册表全量）
     */
    @Override
    public List<AccessibleApplication> accessibleApplications(String subjectId) {
        long startNanos = System.nanoTime();
        try {
            List<PageAdmittedApplication> details = iamUserClient.listPageAdmittedApplicationDetails(subjectId);
            List<AccessibleApplication> result = new ArrayList<AccessibleApplication>();
            for (PageAdmittedApplication detail : details) {
                result.add(AccessibleApplication.builder()
                        .applicationCode(detail.getCode())
                        .displayName(detail.getDisplayName())
                        .description(detail.getDescription())
                        .build());
            }
            log.debug("hint 应用详情取数完成 条数={} 耗时ms={}", result.size(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return result;
        } catch (RuntimeException exception) {
            log.warn("hint 应用详情取数出站失败，该项降级（不拒提交）结果=IAM客户端异常 耗时ms={}",
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return null;
        }
    }

    /**
     * 拉全 IAM 可信应用全局目录（分页翻页；目录投影无描述字段）。
     *
     * @return 目录集（编码+名称）；出站失败返回 null（来源核对降级为未知，注册表不锁）
     */
    @Override
    public List<AccessibleApplication> catalogApplications() {
        long startNanos = System.nanoTime();
        try {
            List<AccessibleApplication> result = new ArrayList<AccessibleApplication>();
            int page = 0;
            while (true) {
                io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage<io.github.surezzzzzz.sdk.iam.client.model.IamApplication> chunk =
                        iamApplicationClient.listApplications(page, CATALOG_PAGE_SIZE);
                for (io.github.surezzzzzz.sdk.iam.client.model.IamApplication application : chunk.getContent()) {
                    result.add(AccessibleApplication.builder()
                            .applicationCode(application.getApplicationCode())
                            .displayName(application.getApplicationName())
                            .description(null)
                            .build());
                }
                if (page + 1 >= chunk.getTotalPages() || chunk.getContent().isEmpty()) {
                    break;
                }
                page++;
            }
            log.debug("注册表目录核对取数完成 条数={} 页数={} 耗时ms={}", result.size(), page + 1,
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return result;
        } catch (RuntimeException exception) {
            log.warn("注册表目录核对取数出站失败，来源核对降级（注册表不锁）结果=IAM客户端异常 耗时ms={}",
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return null;
        }
    }
}
