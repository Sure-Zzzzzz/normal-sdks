package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.SimpleOwnerAuthorizationCollaborationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.support.OwnerAuthorizationModelHelper;
import lombok.Getter;

import java.util.Map;

/**
 * 所属人授权最终态变更。
 *
 * <p>一条可幂等、可乱序保护的最终态授权变更。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class OwnerAuthorizationChange {

    /**
     * 源端不透明顺序位点。
     */
    private final Long sourceSequence;
    /**
     * 事件标识。
     */
    private final String eventId;
    /**
     * 变更类型。
     */
    private final String changeType;
    /**
     * 中性最终态载荷。
     */
    private final Map<String, Object> payload;

    /**
     * 创建最终态变更。
     *
     * @param sourceSequence 源端不透明顺序位点
     * @param eventId        事件标识
     * @param changeType     变更类型
     * @param payload        中性最终态载荷
     */
    public OwnerAuthorizationChange(Long sourceSequence, String eventId, String changeType,
                                    Map<String, Object> payload) {
        this.sourceSequence = OwnerAuthorizationModelHelper.requireNonNegative(sourceSequence,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_SOURCE_SEQUENCE);
        this.eventId = OwnerAuthorizationModelHelper.requireText(eventId,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_EVENT_ID);
        this.changeType = OwnerAuthorizationModelHelper.requireText(changeType,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_CHANGE_TYPE);
        this.payload = OwnerAuthorizationModelHelper.freezePayload(payload);
    }

}
