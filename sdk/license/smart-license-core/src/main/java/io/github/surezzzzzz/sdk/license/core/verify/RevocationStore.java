package io.github.surezzzzzz.sdk.license.core.verify;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 本地吊销集合读取端口（Verifier 侧）：已安装累计快照的只读查询。
 *
 * <p>累计条目只增不减；安装由受控导入流程完成（批次核验在 codec 层，原子合并在实现层）。</p>
 *
 * @author surezzzzzz
 */
public interface RevocationStore {

    /** 条目是否已被吊销（按类型/编号）。 */
    boolean isRevoked(String targetKind, String targetId);

    /** 作用域查询；旧实现默认回落到全局查询，支持多产品实现应覆写以避免编号碰撞。 */
    default boolean isRevoked(String issuerId, String productId, String targetKind, String targetId) {
        return isRevoked(targetKind, targetId);
    }

    /** 已安装快照序号。 */
    OptionalLong installedSequence(String issuerId, String productId);

    /** 已安装快照 issuedAt（新鲜度判断）。 */
    Optional<Long> installedIssuedAt(String issuerId, String productId);
}
