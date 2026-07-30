package io.github.surezzzzzz.sdk.crm.server.core.domain.commercial;

/**
 * 已冻结的商业条款快照。
 *
 * @author surezzzzzz
 */
public interface CommercialTermsSnapshot {

    /**
     * 获取快照对应的商业能力类型。
     *
     * @return 处理后的领域事实或校验结果。
     */
    CommercialCapabilityType getCapabilityType();
}
