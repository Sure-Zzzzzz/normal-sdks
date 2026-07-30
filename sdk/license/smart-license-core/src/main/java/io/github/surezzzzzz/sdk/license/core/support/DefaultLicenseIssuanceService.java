package io.github.surezzzzzz.sdk.license.core.support;

import io.github.surezzzzzz.sdk.kms.client.model.KmsSigningResult;
import io.github.surezzzzzz.sdk.kms.client.port.TenantSignerPort;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.LicenseKeyStatus;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseIssuanceException;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseSerializationException;
import io.github.surezzzzzz.sdk.license.core.model.IssuedLicense;
import io.github.surezzzzzz.sdk.license.core.model.LicenseIssueCommand;
import io.github.surezzzzzz.sdk.license.core.model.LicenseKeyMapping;
import io.github.surezzzzzz.sdk.license.core.repository.LicenseKeyMappingRepository;
import io.github.surezzzzzz.sdk.license.core.service.LicenseIssuanceService;
import io.github.surezzzzzz.sdk.license.core.spi.LicensePayloadCodec;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 默认 License Compact JWS 签发服务。
 *
 * @author surezzzzzz
 */
public final class DefaultLicenseIssuanceService implements LicenseIssuanceService {

    private final LicenseKeyMappingRepository repository;
    private final TenantSignerPort signerPort;
    private final LicensePayloadCodec payloadCodec;

    /**
     * 创建默认签发服务。
     *
     * @param repository   映射仓储端口
     * @param signerPort   KMS 签名端口
     * @param payloadCodec payload 编码端口
     */
    public DefaultLicenseIssuanceService(LicenseKeyMappingRepository repository, TenantSignerPort signerPort,
                                         LicensePayloadCodec payloadCodec) {
        if (repository == null || signerPort == null || payloadCodec == null) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_CANNOT_BE_NULL);
        }
        this.repository = repository;
        this.signerPort = signerPort;
        this.payloadCodec = payloadCodec;
    }

    private static void assertSigningResult(LicenseKeyMapping mapping, KmsSigningResult result) {
        if (result == null || result.getVersion() == null || result.getVersion().intValue() != mapping.getKmsKeyVersion()
                || !SmartLicenseCoreConstant.ALGORITHM_ES256.equals(result.getAlgorithm())
                || result.getSignature() == null
                || result.getSignature().length != SmartLicenseCoreConstant.ES256_JOSE_SIGNATURE_LENGTH) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_KMS_RESULT_INVALID);
        }
    }

    private static LicenseIssuanceException issuanceException(String detail) {
        return new LicenseIssuanceException(ErrorCode.ISSUANCE_FAILED,
                String.format(ErrorMessage.ISSUANCE_FAILED, detail));
    }

    @Override
    public IssuedLicense issue(LicenseIssueCommand command) {
        if (command == null) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_CANNOT_BE_NULL);
        }
        LicenseKeyMapping mapping = activeMapping(command);
        String payload = encode(command);
        String headerSegment = LicenseBase64UrlHelper.encode(
                LicenseJwsHelper.header(mapping.getKid()).getBytes(StandardCharsets.UTF_8));
        String payloadSegment = LicenseBase64UrlHelper.encode(payload.getBytes(StandardCharsets.UTF_8));
        byte[] signingInput = LicenseJwsHelper.signingInput(headerSegment, payloadSegment);
        KmsSigningResult result;
        try {
            result = signerPort.sign(mapping.getKmsKeyRef(), mapping.getKmsKeyVersion(), signingInput);
        } catch (RuntimeException exception) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_KMS_RESULT_INVALID);
        }
        assertSigningResult(mapping, result);
        return new IssuedLicense(LicenseJwsHelper.compact(headerSegment, payloadSegment, result.getSignature()),
                mapping.getKid(), mapping.getKmsKeyVersion());
    }

    private LicenseKeyMapping activeMapping(LicenseIssueCommand command) {
        Optional<LicenseKeyMapping> optional;
        try {
            optional = repository.findByTenantIdAndKid(command.getTenantId(), command.getKid());
        } catch (RuntimeException exception) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_KEY_MAPPING_NOT_FOUND);
        }
        if (optional == null) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_KMS_RESULT_INVALID);
        }
        if (!optional.isPresent()) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_KEY_MAPPING_NOT_FOUND);
        }
        LicenseKeyMapping mapping = optional.get();
        if (!command.getTenantId().equals(mapping.getTenantId()) || !command.getKid().equals(mapping.getKid())) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_MISMATCH);
        }
        if (mapping.getStatus() != LicenseKeyStatus.ACTIVE) {
            throw issuanceException(SmartLicenseCoreConstant.DETAIL_KEY_MAPPING_NOT_ACTIVE);
        }
        return mapping;
    }

    private String encode(LicenseIssueCommand command) {
        String payload;
        try {
            payload = payloadCodec.encodeV1(command.getClaims());
        } catch (RuntimeException exception) {
            throw new LicenseSerializationException(ErrorCode.SERIALIZATION_FAILED,
                    String.format(ErrorMessage.SERIALIZATION_FAILED, SmartLicenseCoreConstant.FIELD_PAYLOAD));
        }
        if (payload == null || payload.isEmpty()) {
            throw new LicenseSerializationException(ErrorCode.SERIALIZATION_FAILED,
                    String.format(ErrorMessage.SERIALIZATION_FAILED, SmartLicenseCoreConstant.FIELD_PAYLOAD));
        }
        return payload;
    }
}
