package io.github.surezzzzzz.sdk.kms.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.kms.core.service.CryptoOperationService;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import io.github.surezzzzzz.sdk.kms.server.service.KmsSignatureOperationResult;
import io.github.surezzzzzz.sdk.kms.server.service.KmsSignatureOperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * KMS 密码学结果资源 REST 控制器。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH + "/crypto")
public class KmsCryptoController extends KmsHttpControllerSupport {

    private final CryptoOperationService cryptoOperationService;
    private final KmsSignatureOperationService signatureOperationService;

    /**
     * 创建密码学 REST 控制器。
     */
    public KmsCryptoController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                               CryptoOperationService cryptoOperationService,
                               KmsSignatureOperationService signatureOperationService) {
        super(principalResolver, properties);
        this.cryptoOperationService = cryptoOperationService;
        this.signatureOperationService = signatureOperationService;
    }

    /**
     * 创建 ES256 签名结果资源。
     */
    @PostMapping(value = "/signatures", consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> sign(@RequestBody String body, HttpServletRequest request) {
        ObjectNode input = object(body, "keyRef", "version", "input");
        KmsRequestContext context = context(request);
        String keyRef = text(input, "keyRef", true);
        KmsSignatureOperationResult result = signatureOperationService.sign(context.getPrincipal(), keyRef,
                integer(input, "version", false), signingInput(input, "input", true), context.getRequestId());
        Map<String, Object> response = map();
        response.put("keyRef", keyRef);
        response.put("version", Integer.valueOf(result.getVersion()));
        response.put("signature", base64url(result.getSignature()));
        return json(200, response);
    }

    /**
     * 创建 ES256 验签结果资源。
     */
    @PostMapping(value = "/verifications", consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> verify(@RequestBody String body, HttpServletRequest request) {
        ObjectNode input = object(body, "keyRef", "version", "input", "signature");
        KmsRequestContext context = context(request);
        boolean valid = cryptoOperationService.verify(context.getPrincipal(), text(input, "keyRef", true),
                integer(input, "version", false), signingInput(input, "input", true),
                signature(input, "signature", true), context.getRequestId());
        Map<String, Object> response = map();
        response.put("valid", valid);
        return json(200, response);
    }

    /**
     * 创建 AES-GCM 密文封装资源。
     */
    @PostMapping(value = "/envelopes", consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> encrypt(@RequestBody String body, HttpServletRequest request) {
        ObjectNode input = object(body, "keyRef", "plaintext", "aad");
        KmsRequestContext context = context(request);
        byte[] envelope = cryptoOperationService.encrypt(context.getPrincipal(), text(input, "keyRef", true),
                plaintext(input, "plaintext", true), aad(input, "aad", false), context.getRequestId());
        Map<String, Object> response = map();
        response.put("envelope", base64url(envelope));
        return json(200, response);
    }

    /**
     * 创建 AES-GCM 解密结果资源。
     */
    @PostMapping(value = "/decryptions", consumes = JSON, produces = JSON_UTF8)
    public ResponseEntity<String> decrypt(@RequestBody String body, HttpServletRequest request) {
        ObjectNode input = object(body, "envelope", "aad");
        KmsRequestContext context = context(request);
        byte[] plaintext = cryptoOperationService.decrypt(context.getPrincipal(), envelope(input, "envelope", true),
                aad(input, "aad", false), context.getRequestId());
        Map<String, Object> response = map();
        response.put("plaintext", base64url(plaintext));
        return json(200, response);
    }
}
