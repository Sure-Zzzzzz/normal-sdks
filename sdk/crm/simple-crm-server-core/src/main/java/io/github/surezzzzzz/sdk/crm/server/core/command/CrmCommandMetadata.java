package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 管理命令的传输无关元数据。
 *
 * @author surezzzzzz
 */
@Getter
public final class CrmCommandMetadata {

    private final String correlationId;
    private final String idempotencyKey;

    /**
     * 创建CrmCommandMetadata。
     *
     * @param correlationId  命令关联标识
     * @param idempotencyKey 幂等键
     *
     */
    public CrmCommandMetadata(String correlationId, String idempotencyKey) {
        this.correlationId = CrmValidationHelper.required(correlationId, "correlationId");
        this.idempotencyKey = CrmValidationHelper.required(idempotencyKey, "idempotencyKey");
    }


}
