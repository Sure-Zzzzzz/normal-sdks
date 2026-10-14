package io.github.surezzzzzz.sdk.crm.client.command;

/**
 * CRM 客户端命令元数据：幂等键必填，关联 ID 缺失由传输层生成并回显。
 *
 * @author surezzzzzz
 */
public final class CrmClientCommand {

    private final String idempotencyKey;
    private final String correlationId;

    /**
     * 创建命令元数据。
     *
     * @param idempotencyKey 幂等键（每次业务写命令唯一）
     * @param correlationId  关联 ID（可空，空时传输层生成）
     */
    public CrmClientCommand(String idempotencyKey, String correlationId) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("idempotencyKey");
        }
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId == null || correlationId.trim().isEmpty()
                ? null : correlationId.trim();
    }

    /**
     * 获取幂等键。
     *
     * @return 幂等键
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * 获取关联 ID。
     *
     * @return 关联 ID（可空）
     */
    public String getCorrelationId() {
        return correlationId;
    }
}
