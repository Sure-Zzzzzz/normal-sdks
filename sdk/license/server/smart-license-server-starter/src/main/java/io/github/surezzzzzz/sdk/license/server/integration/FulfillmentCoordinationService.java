package io.github.surezzzzzz.sdk.license.server.integration;

import io.github.surezzzzzz.sdk.license.core.spi.CrmFulfillmentPort;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import io.github.surezzzzzz.sdk.license.server.entity.CustomerEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.CustomerRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * CRM 履约协调器（消费范式执行链：纯 HTTP 拉取+回写，无事件订阅）：
 * 拉取待执行履约项 → 认领 → 按客户/产品映射建立在线授权草稿 →
 * 等待管理员配置及启用；已启用授权才回写 completed。
 *
 * <p>重放通过履约号定位既有授权，不重复建立草稿；回写序号从 CRM 当前水位递增。
 * 客户/产品不存在属于确定失败；认领、保存或回写结果未明时保留进度，下一轮回读恢复，
 * 不以 failed 冒充业务失败。授权草稿与自有履约台账同事务提交，由数据库唯一键去重。</p>
 *
 * @author surezzzzzz
 */
@lombok.extern.slf4j.Slf4j
@SimpleLicenseServerComponent
public class FulfillmentCoordinationService {

    /** 同步摘要：草稿建立与实际完成分别计数，回写未达不冒充业务失败。 */
    public static final class SyncSummary {
        public int pulled;
        /** 本轮新建授权草稿数量，不能解释为业务完成数量。 */
        public int created;
        /** 命中既有授权且未重复建立草稿的数量。 */
        public int skippedExisting;
        /** 已启用授权的 completed 回写得到确认的数量。 */
        public int completed;
        /** 已建立但尚未启用的授权数量，需要管理员配置/启用。 */
        public int awaitingConfiguration;
        /** 本地结果已知，但 CRM 回写未得到确认的数量。 */
        public int reportPending;
        /** 本轮处理未完成的数量；不表示 CRM 已接收 failed 终态。 */
        public int failed;
        public final Map<String, String> failures = new LinkedHashMap<String, String>();
    }

    private final CrmFulfillmentPort crm;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final FulfillmentGrantCreationService grantCreation;

    public FulfillmentCoordinationService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) CrmFulfillmentPort crm,
            CustomerRepository customers, ProductRepository products, FulfillmentGrantCreationService grantCreation) {
        this.crm = crm;
        this.customers = customers;
        this.products = products;
        this.grantCreation = grantCreation;
    }

    /** CRM 端口是否已装配（触发端点据此给出明确错误而非空转）。 */
    public boolean portAvailable() {
        return crm != null;
    }

    /**
     * 执行一轮履约同步（拉尽游标）。
     *
     * @param batchSize 每页拉取上限
     */
    public SyncSummary sync(int batchSize) {
        SyncSummary summary = new SyncSummary();
        if (crm == null) {
            throw new IllegalStateException("CRM 履约端口未装配");
        }
        String cursor = null;
        do {
            CrmFulfillmentPort.FulfillmentPull pull = crm.pullPending(cursor, batchSize);
            for (CrmFulfillmentPort.FulfillmentItem item : pull.getItems()) {
                summary.pulled++;
                process(item, summary);
            }
            cursor = pull.getNextCursor();
        } while (cursor != null);
        return summary;
    }

    private void process(CrmFulfillmentPort.FulfillmentItem item, SyncSummary summary) {
        String fulfillmentId = item.getFulfillmentId();
        long watermark = item.getSequenceWatermark();
        // CRM 协议序号为 int；两步认领/进度链不得发生截断或溢出。
        if (watermark < 0 || watermark >= Integer.MAX_VALUE) {
            recordFailure(summary, fulfillmentId, "履约回写序号已耗尽或非法");
            log.warn("履约同步停止：fulfillmentId={}，回写序号非法或已耗尽", fulfillmentId);
            return;
        }
        int nextSequence = (int) watermark + 1;
        Optional<GrantRevisionEntity> existing;
        try {
            existing = grantCreation.findExisting(fulfillmentId);
        } catch (RuntimeException failure) {
            recordUnconfirmed(summary, fulfillmentId, "授权回读未确认", failure);
            return;
        }
        if (existing.isPresent()) {
            summary.skippedExisting++;
            reportGrant(item, existing.get(), nextSequence, summary);
            return;
        }
        if (nextSequence == Integer.MAX_VALUE) {
            recordFailure(summary, fulfillmentId, "履约回写序号不足以认领及回报结果");
            log.warn("履约同步停止：fulfillmentId={}，没有足够的认领及结果回写序号", fulfillmentId);
            return;
        }
        try {
            crm.report(fulfillmentId, nextSequence,
                    CrmFulfillmentPort.Outcome.IN_PROGRESS, null, "claimed");
            log.debug("履约认领已确认：fulfillmentId={}，sequence={}", fulfillmentId, nextSequence);
        } catch (RuntimeException failure) {
            // 认领未确认不得建立授权，也不能发更高序号 failed 覆盖可能成功的认领。
            recordUnconfirmed(summary, fulfillmentId, "履约认领未确认", failure);
            return;
        }
        CustomerEntity customer;
        ProductEntity product;
        try {
            customer = customers.findByCode(item.getCustomerRef()).orElse(null);
            product = products.findById(item.getOfferingRef()).orElse(null);
        } catch (RuntimeException failure) {
            recordUnconfirmed(summary, fulfillmentId, "履约映射回读未确认", failure);
            return;
        }
        if (customer == null || product == null) {
            String reason = customer == null ? "履约对应客户未配置" : "履约对应产品未配置";
            recordFailure(summary, fulfillmentId, reason);
            reportOutcome(item, nextSequence + 1, CrmFulfillmentPort.Outcome.FAILED,
                    reason, null, summary);
            return;
        }
        FulfillmentGrantCreationService.CreationResult result;
        try {
            // 当前没有销售义务到完整授权规格的映射，仅建草稿供管理员配置，绝不自动启用。
            result = grantCreation.createOrRecover(fulfillmentId, customer.getCustomerId(), product.getProductId());
        } catch (RuntimeException failure) {
            // 保存异常可能发生在提交结果未明时，下一轮必须先回读，不能向 CRM 宣告业务失败。
            recordUnconfirmed(summary, fulfillmentId, "授权保存结果未确认", failure);
            return;
        }
        if (result.isCreated()) {
            summary.created++;
            log.info("履约授权草稿已建立：fulfillmentId={}，grantId={}，等待配置及启用",
                    fulfillmentId, result.getGrant().getGrantId());
        } else {
            summary.skippedExisting++;
        }
        reportGrant(item, result.getGrant(), nextSequence + 1, summary);
    }

    private void reportGrant(CrmFulfillmentPort.FulfillmentItem item, GrantRevisionEntity grant,
                             int sequence, SyncSummary summary) {
        // 重放和初次回写均使用同一授权标识，不切换成不同结果正文破坏上游幂等键。
        String resultData = "grantId=" + grant.getGrantId();
        if (GrantState.ENABLED.getCode().equals(grant.getState())) {
            if (reportOutcome(item, sequence, CrmFulfillmentPort.Outcome.COMPLETED,
                    resultData, null, summary)) {
                summary.completed++;
            }
        } else {
            summary.awaitingConfiguration++;
            reportOutcome(item, sequence, CrmFulfillmentPort.Outcome.IN_PROGRESS,
                    resultData, "awaiting grant configuration or enablement", summary);
        }
    }

    private boolean reportOutcome(CrmFulfillmentPort.FulfillmentItem item, int sequence,
                                  CrmFulfillmentPort.Outcome outcome, String resultData,
                                  String note, SyncSummary summary) {
        try {
            crm.report(item.getFulfillmentId(), sequence, outcome, resultData, note);
            log.debug("履约回写已确认：fulfillmentId={}，sequence={}，outcome={}",
                    item.getFulfillmentId(), sequence, outcome);
            return true;
        } catch (RuntimeException failure) {
            summary.reportPending++;
            summary.failures.put(item.getFulfillmentId(), "履约结果回写未确认，等待回读恢复");
            // 本地授权事实保留；completed 响应丢失也不能反向改报 failed。
            log.warn("履约结果回写未确认：fulfillmentId={}，sequence={}，outcome={}，异常类型={}",
                    item.getFulfillmentId(), sequence, outcome, failure.getClass().getSimpleName());
            return false;
        }
    }

    private void recordUnconfirmed(SyncSummary summary, String fulfillmentId,
                                   String reason, RuntimeException failure) {
        recordFailure(summary, fulfillmentId, reason);
        log.warn("履约同步结果未确认：fulfillmentId={}，阶段={}，异常类型={}",
                fulfillmentId, reason, failure.getClass().getSimpleName());
    }

    private void recordFailure(SyncSummary summary, String fulfillmentId, String reason) {
        summary.failed++;
        summary.failures.put(fulfillmentId, reason);
    }
}
