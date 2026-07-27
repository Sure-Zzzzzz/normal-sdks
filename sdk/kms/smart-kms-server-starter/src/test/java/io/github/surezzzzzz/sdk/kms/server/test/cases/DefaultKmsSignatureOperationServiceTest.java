package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.service.CryptoOperationService;
import io.github.surezzzzzz.sdk.kms.server.service.DefaultKmsSignatureOperationService;
import io.github.surezzzzzz.sdk.kms.server.service.KmsAuditPublisher;
import io.github.surezzzzzz.sdk.kms.server.service.KmsKeyLock;
import io.github.surezzzzzz.sdk.kms.server.service.KmsSignatureOperationResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * REST 签名结果服务测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultKmsSignatureOperationServiceTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String PRINCIPAL_ID = "test-principal";
    private static final String KEY_REF = "test-key-ref";
    private static final String REQUEST_ID = "test-request-id-000000000001";

    /**
     * 验证缺省版本在锁定视图内确定，并将同一版本传递给实际签名。
     */
    @Test
    void shouldResolveDefaultVersionUnderKeyLock() {
        KmsKeyLock keyLock = mock(KmsKeyLock.class);
        KmsKeyRepository keyRepository = mock(KmsKeyRepository.class);
        CryptoOperationService cryptoOperationService = mock(CryptoOperationService.class);
        KmsAuditPublisher auditPublisher = mock(KmsAuditPublisher.class);
        KmsPrincipal principal = new KmsPrincipal(PRINCIPAL_ID, TENANT_ID, Collections.<String>emptySet());
        byte[] input = new byte[]{1, 2, 3};
        byte[] signature = new byte[]{4, 5, 6};
        when(keyLock.lock(TENANT_ID, KEY_REF)).thenReturn(true);
        when(keyRepository.findByKeyRef(TENANT_ID, KEY_REF)).thenReturn(Optional.of(KmsKey.builder()
                .tenantId(TENANT_ID).keyRef(KEY_REF).activeVersion(2).build()));
        when(cryptoOperationService.sign(principal, KEY_REF, Integer.valueOf(2), input, REQUEST_ID))
                .thenReturn(signature);

        KmsSignatureOperationResult result = new DefaultKmsSignatureOperationService(keyLock, keyRepository,
                cryptoOperationService, auditPublisher).sign(principal, KEY_REF, null, input, REQUEST_ID);

        log.info("缺省签名版本在锁定视图内解析为: {}", result.getVersion());
        assertEquals(2, result.getVersion(), "响应版本必须是实际签名版本");
        assertArrayEquals(signature, result.getSignature(), "签名结果必须原样返回");
        verify(keyLock).lock(TENANT_ID, KEY_REF);
        verify(keyRepository).findByKeyRef(TENANT_ID, KEY_REF);
        verify(cryptoOperationService).sign(principal, KEY_REF, Integer.valueOf(2), input, REQUEST_ID);
    }

    /**
     * 验证缺省版本在当前 tenant 不可见时记录授权拒绝审计。
     */
    @Test
    void shouldAuditUnauthorizedDefaultVersionSelection() {
        KmsKeyLock keyLock = mock(KmsKeyLock.class);
        KmsKeyRepository keyRepository = mock(KmsKeyRepository.class);
        CryptoOperationService cryptoOperationService = mock(CryptoOperationService.class);
        KmsAuditPublisher auditPublisher = mock(KmsAuditPublisher.class);
        KmsPrincipal principal = new KmsPrincipal(PRINCIPAL_ID, TENANT_ID, Collections.<String>emptySet());
        when(keyLock.lock(TENANT_ID, KEY_REF)).thenReturn(false);

        assertThrows(KmsAuthorizationException.class, () -> new DefaultKmsSignatureOperationService(keyLock,
                keyRepository, cryptoOperationService, auditPublisher).sign(principal, KEY_REF, null,
                new byte[]{1}, REQUEST_ID), "不可见逻辑密钥必须按资源级授权拒绝");

        log.info("缺省版本选择被资源级授权拒绝");
        verify(auditPublisher).rejected(eq(principal), eq(KEY_REF), eq(null), eq(KmsOperation.SIGN), eq(REQUEST_ID),
                eq(SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_AUTHORIZATION));
        verify(cryptoOperationService, never()).sign(any(), anyString(), any(), any(), anyString());
    }

    /**
     * 验证适配层拒绝无效显式版本时记录一次校验拒绝审计。
     */
    @Test
    void shouldAuditInvalidExplicitVersionBeforeCryptoService() {
        KmsKeyLock keyLock = mock(KmsKeyLock.class);
        KmsKeyRepository keyRepository = mock(KmsKeyRepository.class);
        CryptoOperationService cryptoOperationService = mock(CryptoOperationService.class);
        KmsAuditPublisher auditPublisher = mock(KmsAuditPublisher.class);
        KmsPrincipal principal = new KmsPrincipal(PRINCIPAL_ID, TENANT_ID, Collections.<String>emptySet());

        assertThrows(io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException.class,
                () -> new DefaultKmsSignatureOperationService(keyLock, keyRepository, cryptoOperationService,
                        auditPublisher).sign(principal, KEY_REF, Integer.valueOf(0), new byte[]{1}, REQUEST_ID),
                "无效显式版本必须在适配层拒绝");

        log.info("无效显式版本被适配层拒绝");
        verify(auditPublisher).rejected(eq(principal), eq(KEY_REF), eq(null), eq(KmsOperation.SIGN), eq(REQUEST_ID),
                eq(SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION));
        verify(cryptoOperationService, never()).sign(any(), anyString(), any(), any(), anyString());
    }

    /**
     * 验证签名结果不暴露可变字节数组。
     */
    @Test
    void shouldDefensivelyCopySignatureBytes() {
        byte[] signature = new byte[]{4, 5, 6};
        KmsSignatureOperationResult result = new KmsSignatureOperationResult(2, signature);
        signature[0] = 9;
        byte[] exposed = result.getSignature();
        exposed[1] = 8;

        assertArrayEquals(new byte[]{4, 5, 6}, result.getSignature(), "签名结果不得暴露内部可变数组");
    }

    /**
     * 验证显式版本不读取活动版本，避免不必要的锁外版本选择。
     */
    @Test
    void shouldUseExplicitVersionWithoutResolvingActiveVersion() {
        KmsKeyLock keyLock = mock(KmsKeyLock.class);
        KmsKeyRepository keyRepository = mock(KmsKeyRepository.class);
        CryptoOperationService cryptoOperationService = mock(CryptoOperationService.class);
        KmsAuditPublisher auditPublisher = mock(KmsAuditPublisher.class);
        KmsPrincipal principal = new KmsPrincipal(PRINCIPAL_ID, TENANT_ID, Collections.<String>emptySet());
        byte[] input = new byte[]{1};
        byte[] signature = new byte[]{2};
        when(cryptoOperationService.sign(principal, KEY_REF, Integer.valueOf(3), input, REQUEST_ID))
                .thenReturn(signature);

        KmsSignatureOperationResult result = new DefaultKmsSignatureOperationService(keyLock, keyRepository,
                cryptoOperationService, auditPublisher).sign(principal, KEY_REF, Integer.valueOf(3), input, REQUEST_ID);

        assertEquals(3, result.getVersion(), "显式版本必须直接作为实际签名版本");
        assertArrayEquals(signature, result.getSignature(), "签名结果必须原样返回");
        verify(keyLock, never()).lock(anyString(), anyString());
        verify(keyRepository, never()).findByKeyRef(anyString(), anyString());
        verify(cryptoOperationService).sign(eq(principal), eq(KEY_REF), eq(Integer.valueOf(3)), eq(input),
                eq(REQUEST_ID));
    }
}
