package io.github.surezzzzzz.sdk.license.core.spi;

import java.util.Collections;
import java.util.List;

/**
 * CRM 履约消费端口：license 作为履约消费者经 HTTP 拉取待执行项并回写结果
 * （消费范式：纯 HTTP 契约+客户端三件套，无事件订阅）。
 *
 * <p>本接口是 license 自有 SPI 埋点——值类型不透 CRM DTO（CrmResourceView 等），
 * 生产适配由宿主骑 CRM 官方客户端（simple-crm-client-core 三件套）实现；
 * Core 只定义本端口及纯值类型，不依赖 CRM、Spring 或持久化实现；服务端消费本契约，
 * 聚散形态与 LicenseSigner/IAM client 适配同律。
 * 消费边界：拉取按派发归属（assignedConsumerId=本服务身份）；回写序号严格递增，
 * 首次 in_progress 即认领；终态必附结果数据。</p>
 *
 * @author surezzzzzz
 */
public interface CrmFulfillmentPort {

    /**
     * 拉取待执行履约项（state=pending_dispatch/in_progress）。
     *
     * @param cursor 游标（null 从头；返回 nextCursor 续拉）
     * @param limit  每页上限
     * @return 拉取页（items+nextCursor；nextCursor=null 表示拉尽）
     */
    FulfillmentPull pullPending(String cursor, int limit);

    /**
     * 点查履约项（按派发归属可见，越界=确定不存在语义）。
     *
     * @param fulfillmentId 履约项标识
     * @return 履约项；不属于本消费者或不存在的处置由实现区分（null=确定不存在）
     */
    FulfillmentItem get(String fulfillmentId);

    /**
     * 回写履约结果（认领/完成/失败；序号严格递增，旧序号拒收）。
     *
     * @param fulfillmentId 履约项标识
     * @param sequence      回写序号（首次认领为 1）
     * @param outcome       目标状态
     * @param resultData    结果数据（终态必填，认领可空）
     * @param note          备注（可空）
     */
    void report(String fulfillmentId, int sequence, Outcome outcome, String resultData, String note);

    /** 回写目标状态（对位 CRM 稳定 code：in_progress/completed/failed）。 */
    enum Outcome {
        IN_PROGRESS("in_progress"),
        COMPLETED("completed"),
        FAILED("failed");

        private final String code;

        Outcome(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /** 履约项视图（只含 license 建授权/资格所需字段，不复制 CRM 主数据）。 */
    final class FulfillmentItem {

        private final String fulfillmentId;
        private final String customerRef;
        private final String orderRef;
        private final String offeringRef;
        private final String capabilityType;
        private final long quantity;
        private final String assignedConsumerId;
        private final long sequenceWatermark;

        public FulfillmentItem(String fulfillmentId, String customerRef, String orderRef, String offeringRef,
                               String capabilityType, long quantity, String assignedConsumerId,
                               long sequenceWatermark) {
            this.fulfillmentId = fulfillmentId;
            this.customerRef = customerRef;
            this.orderRef = orderRef;
            this.offeringRef = offeringRef;
            this.capabilityType = capabilityType;
            this.quantity = quantity;
            this.assignedConsumerId = assignedConsumerId;
            this.sequenceWatermark = sequenceWatermark;
        }

        public String getFulfillmentId() {
            return fulfillmentId;
        }

        public String getCustomerRef() {
            return customerRef;
        }

        public String getOrderRef() {
            return orderRef;
        }

        public String getOfferingRef() {
            return offeringRef;
        }

        public String getCapabilityType() {
            return capabilityType;
        }

        public long getQuantity() {
            return quantity;
        }

        public String getAssignedConsumerId() {
            return assignedConsumerId;
        }

        public long getSequenceWatermark() {
            return sequenceWatermark;
        }
    }

    /** 拉取页：items+续拉游标。 */
    final class FulfillmentPull {

        private final List<FulfillmentItem> items;
        private final String nextCursor;

        public FulfillmentPull(List<FulfillmentItem> items, String nextCursor) {
            this.items = Collections.unmodifiableList(new java.util.ArrayList<FulfillmentItem>(items));
            this.nextCursor = nextCursor;
        }

        public List<FulfillmentItem> getItems() {
            return items;
        }

        /** 续拉游标；null 表示已拉尽。 */
        public String getNextCursor() {
            return nextCursor;
        }
    }
}
