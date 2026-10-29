package io.github.surezzzzzz.sdk.crm.server.service;

import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.FulfillmentConsumerSelector;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentConsumerRepository;

import java.util.List;

/**
 * 消费者选择器：按履约义务所需能力查启用中的注册表；多个候选时取稳定首个（注册表 id 序）。
 *
 * @author surezzzzzz
 */
public class SimpleCrmServerConsumerSelectorAdapter implements FulfillmentConsumerSelector {

    private final SimpleCrmServerJdbcFulfillmentConsumerRepository repository;

    /**
     * 创建选择器。
     *
     * @param repository 消费者注册表仓储
     */
    public SimpleCrmServerConsumerSelectorAdapter(SimpleCrmServerJdbcFulfillmentConsumerRepository repository) {
        this.repository = repository;
    }

    @Override
    public FulfillmentConsumer select(FulfillmentObligationTemplate obligationTemplate) {
        List<FulfillmentConsumer> candidates = repository.findEnabled(
                obligationTemplate.getRequiredConsumerCapability());
        if (candidates.isEmpty()) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "no enabled fulfillment consumer for capability");
        }
        return candidates.get(0);
    }
}
