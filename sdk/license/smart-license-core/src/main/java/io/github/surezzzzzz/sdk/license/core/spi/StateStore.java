package io.github.surezzzzzz.sdk.license.core.spi;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 本地校验状态存储端口：T_max、吊销序号、已知否定水位、激活/资格/恢复代数及本地额度水位。
 *
 * <p>键为 issuer/product/customer/grant 加稳定对象键；代数、凭据编号或修订变化不创建新状态目录。
 * 没有成功持久化必要状态时不得返回 ALLOW。</p>
 *
 * @author surezzzzzz
 */
public interface StateStore {

    /** 持久化可信时间最大值（仅通过完整基础校验的使用事实才推进）。 */
    void persistMaxTrustedTime(String stateKey, long epochSeconds);

    /** 读取可信时间最大值。 */
    OptionalLong loadMaxTrustedTime(String stateKey);

    /** 持久化吊销序号（只增不减）。 */
    void persistRevocationSequence(String issuerId, String productId, long sequence);

    /** 读取已安装吊销序号。 */
    OptionalLong loadRevocationSequence(String issuerId, String productId);

    /** 持久化已知否定水位（grant 暂停、成员迁出、资格撤回等）。 */
    void persistNegativeWatermark(String stateKey, String scope, long version);

    /** 读取否定水位（scope 例：grantState/assignmentGeneration/membershipObservation）。 */
    OptionalLong loadNegativeWatermark(String stateKey, String scope);

    /** 持久化恢复代数。 */
    void persistRecoveryGeneration(String stateKey, long generation);

    /** 读取恢复代数。 */
    OptionalLong loadRecoveryGeneration(String stateKey);

    /** 构建稳定状态键。 */
    String buildStateKey(String issuerId, String productId, String customerId, String grantId,
                         String stableObjectKey);
}
