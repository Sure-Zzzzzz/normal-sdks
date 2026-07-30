package io.github.surezzzzzz.sdk.crm.server.core.domain.commercial;


/**
 * 不依赖外部状态的商业能力。
 *
 * @author surezzzzzz
 */
public interface CommercialCapability {

    /**
     * 获取本实现支持的商业能力类型。
     *
     * @return 处理后的领域事实或校验结果。
     */
    CommercialCapabilityType getType();

    /**
     * 计算冻结商业结果和履约义务。
     *
     * @param request 商业能力计算请求
     * @return 处理后的领域事实或校验结果。
     */
    CommercialCapabilityResult evaluate(CommercialCapabilityRequest request);
}
