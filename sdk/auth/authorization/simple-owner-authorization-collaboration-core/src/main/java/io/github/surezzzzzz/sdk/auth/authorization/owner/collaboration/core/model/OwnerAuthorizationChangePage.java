package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.SimpleOwnerAuthorizationCollaborationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.support.OwnerAuthorizationModelHelper;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 所属人授权变更拉取页。
 *
 * <p>有序变更拉取结果；要求重同步的结果不得被当作空成功页处理。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class OwnerAuthorizationChangePage {

    /**
     * 变更源当前是否可用。
     */
    private final boolean available;
    /**
     * 是否必须重同步。
     */
    private final boolean resyncRequired;
    /**
     * 有序最终态变更列表。
     */
    private final List<OwnerAuthorizationChange> changes;
    /**
     * 最低可用水位。
     */
    private final Long lowWatermark;
    /**
     * 最高水位。
     */
    private final Long highWatermark;
    /**
     * 变更源服务器时间。
     */
    private final Instant serverTime;

    /**
     * 创建变更拉取页。
     *
     * @param available      变更源当前是否可用
     * @param resyncRequired 是否必须重同步
     * @param changes        有序最终态变更列表
     * @param lowWatermark   最低可用水位
     * @param highWatermark  最高水位
     * @param serverTime     变更源服务器时间
     */
    public OwnerAuthorizationChangePage(boolean available, boolean resyncRequired,
                                        List<OwnerAuthorizationChange> changes,
                                        Long lowWatermark, Long highWatermark,
                                        Instant serverTime) {
        if (!available) {
            this.available = false;
            this.resyncRequired = false;
            this.changes = Collections.emptyList();
            this.lowWatermark = null;
            this.highWatermark = null;
            this.serverTime = null;
            return;
        }
        List<OwnerAuthorizationChange> frozenChanges = OwnerAuthorizationModelHelper.freezeChanges(changes);
        Long requiredLowWatermark = OwnerAuthorizationModelHelper.requireNonNegative(lowWatermark,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_LOW_WATERMARK);
        Long requiredHighWatermark = OwnerAuthorizationModelHelper.requireNonNegative(highWatermark,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_HIGH_WATERMARK);
        if (requiredLowWatermark.longValue() > requiredHighWatermark.longValue()) {
            throw OwnerAuthorizationModelHelper.invalidModel(
                    SimpleOwnerAuthorizationCollaborationConstant.DETAIL_LOW_WATERMARK_MUST_NOT_EXCEED_HIGH_WATERMARK);
        }
        if (resyncRequired && !frozenChanges.isEmpty()) {
            throw OwnerAuthorizationModelHelper.invalidModel(
                    SimpleOwnerAuthorizationCollaborationConstant.DETAIL_RESYNC_PAGE_MUST_NOT_CONTAIN_CHANGES);
        }
        this.available = available;
        this.resyncRequired = resyncRequired;
        this.changes = frozenChanges;
        this.lowWatermark = requiredLowWatermark;
        this.highWatermark = requiredHighWatermark;
        if (serverTime == null) {
            throw OwnerAuthorizationModelHelper.invalidModel(String.format(
                    SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_CANNOT_BE_NULL,
                    SimpleOwnerAuthorizationCollaborationConstant.FIELD_SERVER_TIME));
        }
        this.serverTime = serverTime;
    }

    /**
     * 创建变更源不可用的拉取页。
     *
     * @return 不可用拉取页
     */
    public static OwnerAuthorizationChangePage unavailable() {
        return new OwnerAuthorizationChangePage(false, false, null, null, null, null);
    }

    /**
     * 创建必须重同步的拉取页。
     *
     * @param lowWatermark  最低可用水位
     * @param highWatermark 最高水位
     * @param serverTime    变更源服务器时间
     * @return 必须重同步的拉取页
     */
    public static OwnerAuthorizationChangePage resyncRequired(Long lowWatermark, Long highWatermark,
                                                              Instant serverTime) {
        return new OwnerAuthorizationChangePage(true, true, null, lowWatermark, highWatermark, serverTime);
    }

}
