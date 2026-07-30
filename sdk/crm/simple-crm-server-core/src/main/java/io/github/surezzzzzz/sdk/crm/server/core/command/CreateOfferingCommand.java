package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 创建 Offering 命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class CreateOfferingCommand {

    private final String offeringReference;
    private final String displayName;
    private final CommercialCapabilityType capabilityType;
    private final String requiredConsumerCapability;

    /**
     * 创建CreateOfferingCommand。
     *
     * @param offeringReference          商品或服务业务引用
     * @param displayName                展示名称
     * @param capabilityType             商业能力类型
     * @param requiredConsumerCapability 消费者必须具备的能力
     *
     */
    public CreateOfferingCommand(String offeringReference, String displayName,
                                 CommercialCapabilityType capabilityType,
                                 String requiredConsumerCapability) {
        this.offeringReference = CrmValidationHelper.required(offeringReference, "offeringReference");
        this.displayName = CrmValidationHelper.required(displayName, "displayName");
        this.capabilityType = CrmValidationHelper.requiredObject(capabilityType, "capabilityType");
        this.requiredConsumerCapability = CrmValidationHelper.required(requiredConsumerCapability,
                "requiredConsumerCapability");
    }


}
