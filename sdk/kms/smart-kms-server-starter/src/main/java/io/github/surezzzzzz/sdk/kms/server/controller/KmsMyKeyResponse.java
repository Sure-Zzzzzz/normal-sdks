package io.github.surezzzzzz.sdk.kms.server.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyMetadata;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 本人密钥的安全元数据响应；内部快照只用于原字节重放，不进入 JSON。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KmsMyKeyResponse {

    /**
     * 逻辑密钥标识。
     */
    private final String keyRef;
    /**
     * 密钥别名。
     */
    private final String keyAlias;
    /**
     * 用途编码。
     */
    private final String purpose;
    /**
     * 算法编码。
     */
    private final String algorithm;
    /**
     * 状态编码。
     */
    private final String state;
    /**
     * 活动版本；没有活动版本时为空。
     */
    private final Integer activeVersion;
    /**
     * 当前资源版本。
     */
    private final long rowVersion;
    /**
     * UTC 毫秒创建时间。
     */
    private final String createdAt;
    /**
     * UTC 毫秒更新时间。
     */
    private final String updatedAt;
    /**
     * 经白名单验证的历史响应文本。
     */
    @JsonIgnore
    private final String responseSnapshot;

    /**
     * 从持久化元数据建立首次安全响应。
     *
     * @param metadata 当前密钥元数据
     * @return 不包含归属或材料的响应
     */
    public static KmsMyKeyResponse fromMetadata(KmsKeyMetadata metadata) {
        KmsKey key = metadata.getKey();
        return new KmsMyKeyResponse(key.getKeyRef(), key.getKeyAlias(), key.getPurpose().getCode(),
                key.getAlgorithm().getCode(), key.getState().getCode(), key.getActiveVersion(), key.getRowVersion(),
                KmsHttpJson.utcMillis(metadata.getCreatedAt()), KmsHttpJson.utcMillis(metadata.getUpdatedAt()), null);
    }

    /**
     * 验证历史快照的字段和类型，并保留首次响应的原始文本。
     *
     * @param snapshot 幂等执行器返回的响应文本
     * @return 可原字节重放的响应
     */
    public static KmsMyKeyResponse fromSnapshot(String snapshot) {
        try {
            ObjectNode value = KmsHttpJson.parseStrictObject(snapshot, "keyRef", "keyAlias", "purpose", "algorithm",
                    "state", "activeVersion", "rowVersion", "createdAt", "updatedAt");
            String purpose = requiredText(value, "purpose");
            String algorithm = requiredText(value, "algorithm");
            String state = requiredText(value, "state");
            JsonNode version = value.get("activeVersion");
            JsonNode rowVersion = value.get("rowVersion");
            if (KmsKeyPurpose.fromCode(purpose) == null || KmsAlgorithm.fromCode(algorithm) == null
                    || KmsKeyState.fromCode(state) == null || version == null
                    || (!version.isNull() && (!version.isIntegralNumber() || !version.canConvertToInt()
                    || version.intValue() < 1)) || rowVersion == null || !rowVersion.isIntegralNumber()
                    || !rowVersion.canConvertToLong() || rowVersion.longValue() < 0) {
                throw new KmsPersistenceException();
            }
            return new KmsMyKeyResponse(requiredText(value, "keyRef"), requiredText(value, "keyAlias"), purpose,
                    algorithm, state, version.isNull() ? null : Integer.valueOf(version.intValue()),
                    rowVersion.longValue(), requiredTime(value, "createdAt"), requiredTime(value, "updatedAt"), snapshot);
        } catch (RuntimeException exception) {
            throw new KmsPersistenceException();
        }
    }

    private static String requiredText(ObjectNode value, String field) {
        JsonNode node = value.get(field);
        if (node == null || !node.isTextual()) {
            throw new KmsPersistenceException();
        }
        return node.textValue();
    }

    private static String requiredTime(ObjectNode value, String field) {
        String time = requiredText(value, field);
        if (!time.equals(KmsHttpJson.utcMillis(Instant.parse(time)))) {
            throw new KmsPersistenceException();
        }
        return time;
    }
}
