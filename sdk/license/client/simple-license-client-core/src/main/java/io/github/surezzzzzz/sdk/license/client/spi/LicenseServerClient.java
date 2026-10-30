package io.github.surezzzzzz.sdk.license.client.spi;

import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.model.ClientLease;
import io.github.surezzzzzz.sdk.license.client.model.ClientReservation;
import io.github.surezzzzzz.sdk.license.client.model.GrantCandidate;
import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionPage;
import io.github.surezzzzzz.sdk.license.client.model.GrantSelectionRequest;
import io.github.surezzzzzz.sdk.license.client.model.TrustMaterialSnapshot;
import java.util.List;
import java.util.Map;

/**
 * License Server 公开契约的消费侧接口（client-core 三件套之核心）。
 *
 * <p>判定类调用（authorize/lease/reserve/release/consume）失败即抛协议异常且业务不执行，
 * 无 last-known-good；仅信任资料同步沿用最近有效快照。传输与认证由传输 starter 装配，
 * 本接口零 HTTP、零认证、零 Spring。</p>
 *
 * @author surezzzzzz
 */
public interface LicenseServerClient {

    /**
     * 在线判定（值对象形态）：明确选定的一份 grant，单动作三权交集。
     * 字段演进经 {@link io.github.surezzzzzz.sdk.license.client.model.AuthorizationRequest} 新增
     * 构建方法表达，本签名保持二进制稳定。
     */
    ClientDecision authorize(io.github.surezzzzzz.sdk.license.client.model.AuthorizationRequest request);

    /**
     * 在线判定（位置参数便捷形态）：语义同 {@link #authorize(AuthorizationRequest)}。
     *
     * @param activationId 仅部署请求携带；人员/服务传 null
     * @param assignmentId 固定资格索引（可空）
     * @param businessOperationId 消费动作必填（绑定消费引用去重）
     */
    ClientDecision authorize(String productId, String grantId, String subjectKind, String action,
                             String activationId, String assignmentId, String businessOperationId,
                             Map<String, Object> facts);

    /** 候选发现：权限/资格交集内候选与版本；0 候选明确无授权，多候选由调用方明确选一份。 */
    List<GrantCandidate> discoverGrants(String productId, String subjectKind, String action);

    /** 显式分页并携带部署激活绑定；旧自定义实现未提供分页能力时明确拒绝。 */
    default GrantSelectionPage discoverGrants(GrantSelectionRequest request) {
        throw new UnsupportedOperationException("当前客户端不支持完整候选分页契约");
    }

    /** 历史租约便捷签名；官方传输因缺完整修订与对象上下文明确拒绝。 */
    ClientLease leaseRenew(String grantId, String subjectKind);

    /** 并发预占（值对象形态，业务操作编号去重）。 */
    ClientReservation reserve(io.github.surezzzzzz.sdk.license.client.model.QuotaRequest request);

    /** 历史额度便捷签名；官方传输拒绝缺上下文请求，请使用完整值对象。 */
    ClientReservation reserve(String grantId, String metric, long quantity, String businessOperationId);

    /** 并发释放（累计扣减不可释放）。 */
    void release(String reservationId);

    /** 以当前围栏续占；既有自定义实现没有此能力时明确拒绝，不能冒充新建占用。 */
    default ClientReservation renewReservation(String reservationId, long expectedFence) {
        throw new UnsupportedOperationException("当前客户端不支持并发续占");
    }

    /** 累计扣减提交（值对象形态，业务操作编号去重）。 */
    ClientReservation consume(io.github.surezzzzzz.sdk.license.client.model.QuotaRequest request);

    /** 历史消费便捷签名；官方传输拒绝缺上下文请求，请使用完整值对象。 */
    ClientReservation consume(String grantId, String metric, long quantity, String businessOperationId);

    /** 信任资料同步（吊销分片与公钥档案；唯一允许 last-known-good 的调用）。 */
    TrustMaterialSnapshot fetchTrustMaterial(String issuerId, String productId, long knownSequence);
}
