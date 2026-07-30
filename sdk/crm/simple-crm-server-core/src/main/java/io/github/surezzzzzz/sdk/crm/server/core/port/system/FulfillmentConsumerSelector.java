package io.github.surezzzzzz.sdk.crm.server.core.port.system;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;

/**
 * 履约消费者选择端口。
 *
 * <p>实现必须以权威安全状态判定消费者；无法确定时拒绝选择。</p>
 *
 * @author surezzzzzz
 */
public interface FulfillmentConsumerSelector {

    /**
     * 选择满足履约义务的消费者。
     *
     * @param tenantId           租户唯一标识
     * @param obligationTemplate 冻结的履约义务模板
     * @return 处理后的领域事实或校验结果。
     */
    FulfillmentConsumer select(String tenantId, FulfillmentObligationTemplate obligationTemplate);
}
