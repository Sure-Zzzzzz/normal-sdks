package io.github.surezzzzzz.sdk.kms.client.model;

import lombok.Builder;
import lombok.Getter;

/**
 * owner 级销毁窗口政策（当前凭证身份自身的政策）。
 *
 * <p>exists=false 或秒数为 null 表示该侧不限制。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsOwnerDestructionPolicy {

    /**
     * 是否已设置政策（false = 不限制）。
     */
    private final boolean exists;
    /**
     * 最短提前量（秒）；null = 不设下限。
     */
    private final Long minScheduleAheadSeconds;
    /**
     * 最长提前量（秒）；null = 不设上限。
     */
    private final Long maxScheduleAheadSeconds;
}
