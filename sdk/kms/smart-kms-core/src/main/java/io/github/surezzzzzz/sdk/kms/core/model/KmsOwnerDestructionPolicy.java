package io.github.surezzzzzz.sdk.kms.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * owner 级销毁窗口政策。
 *
 * <p>窗口政策属于 owner（人或业务应用）的选择：无行 = 不限制（组件能力态默认）；
 * 有行时 {@code scheduleDestruction} 的 dueAt 必须落在 {@code [now+min, now+max]} 区间。
 * min/max 均可空（空侧不设限）。该模型不包含密钥细节或任何材料。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsOwnerDestructionPolicy {

    /**
     * 政策归属的 owner 主体标识（iam:/app:/aksk: 前缀形态）。
     */
    private final String ownerPrincipalId;
    /**
     * 最短提前量（秒）；null = 不设下限。
     */
    private final Long minScheduleAheadSeconds;
    /**
     * 最长提前量（秒）；null = 不设上限。
     */
    private final Long maxScheduleAheadSeconds;
    /**
     * 最近更新时间。
     */
    private final Instant updatedAt;
    /**
     * 乐观锁版本。
     */
    private final long rowVersion;
}
