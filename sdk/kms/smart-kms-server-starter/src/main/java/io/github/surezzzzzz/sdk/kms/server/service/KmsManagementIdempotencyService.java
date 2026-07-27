package io.github.surezzzzzz.sdk.kms.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.*;
import io.github.surezzzzzz.sdk.kms.core.model.KmsIdempotencyRecord;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsIdempotencyRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsIdempotencyHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsIdempotencyResponseSnapshotRepository;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsIdempotencyScopeLock;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KMS 管理写操作幂等执行器。
 *
 * @author surezzzzzz
 */
public class KmsManagementIdempotencyService {

    private static final String SHA_256 = "SHA-256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final KmsClock clock;
    private final KmsIdempotencyRepository idempotencyRepository;
    private final KmsIdempotencyResponseSnapshotRepository snapshotRepository;
    private final KmsIdempotencyScopeLock scopeLock;
    private final SmartKmsServerProperties properties;
    private final KmsAuditPublisher auditPublisher;

    /**
     * 创建管理幂等执行器。
     */
    public KmsManagementIdempotencyService(KmsClock clock, KmsIdempotencyRepository idempotencyRepository,
                                           KmsIdempotencyResponseSnapshotRepository snapshotRepository,
                                           KmsIdempotencyScopeLock scopeLock, SmartKmsServerProperties properties,
                                           KmsAuditPublisher auditPublisher) {
        this.clock = clock;
        this.idempotencyRepository = idempotencyRepository;
        this.snapshotRepository = snapshotRepository;
        this.scopeLock = scopeLock;
        this.properties = properties;
        this.auditPublisher = auditPublisher;
    }

    /**
     * 生成不包含任何原始作用域字段的固定长度锁标识。
     */
    private static String scopeHash(KmsPrincipal principal, String endpoint, String idempotencyKey) {
        return sha256(principal.getTenantId() + "\n" + principal.getPrincipalId() + "\n" + endpoint + "\n"
                + idempotencyKey);
    }

    /**
     * 将内部稳定端点映射为唯一的管理审计操作。
     */
    private static KmsOperation operation(String endpoint) {
        if (endpoint.startsWith("POST:/api/v1/kms/keys/") && endpoint.endsWith("/versions")) {
            return KmsOperation.ROTATE_KEY;
        }
        if (endpoint.startsWith("PATCH:/api/v1/kms/keys/") && endpoint.endsWith("/state")) {
            return KmsOperation.CHANGE_KEY_STATE;
        }
        if (endpoint.startsWith("PUT:/api/v1/kms/keys/") && endpoint.endsWith("/destruction")) {
            return KmsOperation.SCHEDULE_KEY_DESTRUCTION;
        }
        if (endpoint.startsWith("DELETE:/api/v1/kms/keys/") && endpoint.endsWith("/destruction")) {
            return KmsOperation.CANCEL_KEY_DESTRUCTION;
        }
        if (endpoint.startsWith("POST:/api/v1/kms/keys/") && endpoint.endsWith("/policies")) {
            return KmsOperation.CREATE_KEY_POLICY;
        }
        if (endpoint.startsWith("DELETE:/api/v1/kms/keys/") && endpoint.contains("/policies/")) {
            return KmsOperation.REVOKE_KEY_POLICY;
        }
        if ("POST:/api/v1/kms/keys".equals(endpoint)) {
            return KmsOperation.CREATE_KEY;
        }
        throw new KmsPersistenceException();
    }

    /**
     * 返回各管理操作审计事件使用的资源类型。
     */
    private static String resourceType(KmsOperation operation) {
        if (operation == KmsOperation.CREATE_KEY_POLICY || operation == KmsOperation.REVOKE_KEY_POLICY) {
            return SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_POLICY;
        }
        if (operation == KmsOperation.ROTATE_KEY) {
            return SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION;
        }
        return SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY;
    }

    /**
     * 从内部稳定端点提取可审计的逻辑密钥标识。
     */
    private static String keyRef(String endpoint) {
        String prefix = "/api/v1/kms/keys/";
        int start = endpoint.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        int valueStart = start + prefix.length();
        int valueEnd = endpoint.indexOf('/', valueStart);
        return valueEnd < 0 ? endpoint.substring(valueStart) : endpoint.substring(valueStart, valueEnd);
    }

    /**
     * 使用规范化文本计算 SHA-256 十六进制摘要。
     */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(value.getBytes(StandardCharsets.UTF_8));
            char[] characters = new char[digest.length * 2];
            for (int index = 0; index < digest.length; index++) {
                int unsigned = digest[index] & 255;
                characters[index * 2] = HEX[unsigned >>> 4];
                characters[index * 2 + 1] = HEX[unsigned & 15];
            }
            return new String(characters);
        } catch (NoSuchAlgorithmException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 将固定字段编码为模块私有 JSON 快照。
     */
    private static byte[] writeSnapshot(KmsManagementIdempotencyResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("status", Integer.valueOf(result.getStatus()));
        snapshot.put("resourceRef", result.getResourceRef());
        snapshot.put("location", result.getLocation());
        snapshot.put("responseBody", result.getResponseBody());
        return KmsHttpJson.write(snapshot).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 解码并校验固定字段的历史快照。
     */
    private static KmsManagementIdempotencyResult readSnapshot(byte[] snapshot) {
        if (snapshot == null || snapshot.length == 0) {
            throw new KmsPersistenceException();
        }
        try {
            ObjectNode value = KmsHttpJson.parseSnapshotObject(new String(snapshot, StandardCharsets.UTF_8));
            JsonNode status = value.get("status");
            JsonNode resourceRef = value.get("resourceRef");
            JsonNode location = value.get("location");
            JsonNode responseBody = value.get("responseBody");
            if (status == null || !status.isInt() || resourceRef == null || !resourceRef.isTextual()
                    || (location != null && !location.isNull() && !location.isTextual())
                    || (responseBody != null && !responseBody.isNull() && !responseBody.isTextual())) {
                throw new KmsPersistenceException();
            }
            KmsManagementIdempotencyResult result = new KmsManagementIdempotencyResult(status.intValue(),
                    responseBody == null || responseBody.isNull() ? null : responseBody.textValue(),
                    resourceRef.textValue(), location == null || location.isNull() ? null : location.textValue(), true);
            validateResult(result);
            return result;
        } catch (KmsPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 验证可安全持久化的成功响应形态。
     */
    private static void validateResult(KmsManagementIdempotencyResult result) {
        if (result == null || result.getResourceRef() == null
                || (result.getStatus() != 200 && result.getStatus() != 201 && result.getStatus() != 204)
                || (result.getStatus() == 204 && result.getResponseBody() != null)
                || (result.getStatus() != 204 && result.getResponseBody() == null)) {
            throw new KmsPersistenceException();
        }
        if (result.getResponseBody() != null) {
            KmsHttpJson.parseSnapshotObject(result.getResponseBody());
        }
    }

    /**
     * 在单一事务中判定重放、执行首次写入并保存无敏感响应快照。
     */
    @Transactional
    public KmsManagementIdempotencyResult execute(KmsPrincipal principal, String endpoint, String idempotencyKey,
                                                  String requestId, String canonicalRequest,
                                                  KmsManagementWriteAction action) {
        if (principal == null || canonicalRequest == null || action == null) {
            throw new KmsPersistenceException();
        }
        KmsOperation operation = operation(endpoint);
        String keyRef = keyRef(endpoint);
        try {
            KmsValidationHelper.requireIdempotencyKey(idempotencyKey);
            String requestHash = sha256(canonicalRequest);
            lockScope(scopeHash(principal, endpoint, idempotencyKey));
            Instant now = clock.now();
            KmsIdempotencyRecord existing = idempotencyRepository.find(principal.getTenantId(),
                    principal.getPrincipalId(), endpoint, idempotencyKey).orElse(null);
            if (existing != null) {
                if (existing.getExpiresAt() == null || !existing.getExpiresAt().isAfter(now)) {
                    snapshotRepository.deleteExpired(principal.getTenantId(), principal.getPrincipalId(), endpoint,
                            idempotencyKey, now);
                } else if (KmsIdempotencyHelper.isReplayable(existing, principal.getTenantId(),
                        principal.getPrincipalId(), endpoint, idempotencyKey, requestHash)) {
                    byte[] snapshot = snapshotRepository.findResponseSnapshot(principal.getTenantId(),
                                    principal.getPrincipalId(), endpoint, idempotencyKey)
                            .orElseThrow(KmsPersistenceException::new);
                    KmsManagementIdempotencyResult replayed = readSnapshot(snapshot);
                    auditPublisher.replayed(principal, keyRef == null ? replayed.getResourceRef() : keyRef, operation,
                            requestId, resourceType(operation));
                    return replayed;
                }
            }
            KmsManagementIdempotencyResult result = action.execute();
            validateResult(result);
            KmsIdempotencyRecord record = KmsIdempotencyRecord.builder().tenantId(principal.getTenantId())
                    .principalId(principal.getPrincipalId()).endpoint(endpoint).idempotencyKey(idempotencyKey)
                    .requestHash(requestHash).resourceRef(result.getResourceRef()).httpStatus(result.getStatus())
                    .expiresAt(now.plusSeconds(retentionSeconds())).build();
            snapshotRepository.saveResponseSnapshot(record, writeSnapshot(result));
            return result;
        } catch (KmsValidationException exception) {
            auditPublisher.rejected(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION);
            throw exception;
        } catch (KmsAuthorizationException exception) {
            auditPublisher.rejected(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_AUTHORIZATION);
            throw exception;
        } catch (KmsNotFoundException exception) {
            auditPublisher.rejected(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_NOT_FOUND);
            throw exception;
        } catch (KmsStateConflictException exception) {
            auditPublisher.rejected(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_STATE_CONFLICT);
            throw exception;
        } catch (KmsPolicyConflictException exception) {
            auditPublisher.rejected(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_POLICY_CONFLICT);
            throw exception;
        } catch (KmsIdempotencyConflictException exception) {
            auditPublisher.rejected(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_IDEMPOTENCY_CONFLICT);
            throw exception;
        } catch (KmsPersistenceException exception) {
            auditPublisher.failed(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_PERSISTENCE);
            throw exception;
        } catch (KmsServiceUnavailableException exception) {
            auditPublisher.failed(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
            throw exception;
        } catch (RuntimeException exception) {
            auditPublisher.failed(principal, keyRef, null, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
            throw exception;
        }
    }

    /**
     * 在事务完成前独占同一管理幂等作用域，避免两个首次请求重复执行业务写入。
     */
    private void lockScope(final String scopeHash) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new KmsPersistenceException();
        }
        if (!scopeLock.tryLock(scopeHash)) {
            throw new KmsServiceUnavailableException();
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    scopeLock.unlock(scopeHash);
                } catch (RuntimeException exception) {
                    // 锁释放失败不得覆盖已完成的管理结果。
                }
            }
        });
    }

    /**
     * 返回经配置确认的幂等保留时间。
     */
    private long retentionSeconds() {
        if (properties.getIdempotency() == null || properties.getIdempotency().getRetentionSeconds() == null
                || properties.getIdempotency().getRetentionSeconds().longValue() <= 0) {
            throw new KmsPersistenceException();
        }
        return properties.getIdempotency().getRetentionSeconds().longValue();
    }

    /**
     * 首次管理写入回调。
     */
    public interface KmsManagementWriteAction {

        /**
         * 执行首次管理写入并构造安全响应快照。
         */
        KmsManagementIdempotencyResult execute();
    }
}
