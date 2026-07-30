package io.github.surezzzzzz.sdk.crm.server.core.domain.commercial;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;

import java.util.EnumMap;
import java.util.Map;

/**
 * 已注册商业能力的只读注册表。
 *
 * @author surezzzzzz
 */
public final class CommercialCapabilityRegistry {

    private final Map<CommercialCapabilityType, CommercialCapability> capabilities;

    /**
     * 创建CommercialCapabilityRegistry。
     *
     * @param registeredCapabilities 要注册的商业能力集合
     *
     */
    public CommercialCapabilityRegistry(Iterable<CommercialCapability> registeredCapabilities) {
        if (registeredCapabilities == null) {
            throw CrmException.validation("registeredCapabilities");
        }
        this.capabilities = new EnumMap<CommercialCapabilityType, CommercialCapability>(CommercialCapabilityType.class);
        for (CommercialCapability capability : registeredCapabilities) {
            if (capability == null || capability.getType() == null
                    || capabilities.put(capability.getType(), capability) != null) {
                throw CrmException.validation("registeredCapabilities");
            }
        }
    }

    /**
     * 获取Required。
     *
     * @param capabilityType 商业能力类型
     * @return 处理后的领域事实或校验结果。
     *
     */
    public CommercialCapability getRequired(CommercialCapabilityType capabilityType) {
        CommercialCapability capability = capabilities.get(capabilityType);
        if (capability == null) {
            throw new CrmException(CrmErrorCode.COMMERCIAL_CAPABILITY_UNAVAILABLE,
                    "commercial capability is not registered");
        }
        return capability;
    }
}
