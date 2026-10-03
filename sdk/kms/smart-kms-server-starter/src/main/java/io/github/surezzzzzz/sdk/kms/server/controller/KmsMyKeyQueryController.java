package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsNotFoundException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyMetadata;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyPage;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyQueryRepository;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 当前主体的密钥工作区，只读且始终固定到认证主体的 ownerPrincipalId。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping(SmartKmsServerConstant.API_BASE_PATH + "/me/keys")
public class KmsMyKeyQueryController extends KmsHttpControllerSupport {

    private final KmsKeyQueryRepository keyQueryRepository;

    public KmsMyKeyQueryController(KmsPrincipalResolver principalResolver, SmartKmsServerProperties properties,
                                   KmsKeyQueryRepository keyQueryRepository) {
        super(principalResolver, properties);
        this.keyQueryRepository = keyQueryRepository;
    }

    @GetMapping(produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_READ)
    public ResponseEntity<String> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String alias,
                                       @RequestParam(required = false) String purpose,
                                       @RequestParam(required = false) String algorithm,
                                       @RequestParam(required = false) String state,
                                       HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_READ);
        int resolvedSize = size == null ? pageDefaultSize() : size.intValue();
        if (page < 1 || resolvedSize < 1 || resolvedSize > pageMaxSize(pageDefaultSize())
                || (purpose != null && KmsKeyPurpose.fromCode(purpose) == null)
                || (algorithm != null && KmsAlgorithm.fromCode(algorithm) == null)
                || (state != null && KmsKeyState.fromCode(state) == null)) {
            throw new KmsValidationException();
        }
        KmsKeyPage result = keyQueryRepository.findPage(context.getPrincipal().getOwnerPrincipalId(), alias, purpose,
                algorithm, state, ((long) page - 1L) * (long) resolvedSize, resolvedSize);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (KmsKeyMetadata metadata : result.getItems()) {
            items.add(key(metadata));
        }
        Map<String, Object> response = map();
        response.put("items", items);
        response.put("page", Integer.valueOf(page));
        response.put("size", Integer.valueOf(resolvedSize));
        response.put("total", Long.valueOf(result.getTotal()));
        return json(200, response);
    }

    @GetMapping(value = "/{keyRef}", produces = JSON_UTF8)
    @RequireApiPermission(SmartKmsServerConstant.API_PERMISSION_KEY_READ)
    public ResponseEntity<String> get(@PathVariable String keyRef, HttpServletRequest request) {
        KmsRequestContext context = requireApiPermission(context(request), SmartKmsServerConstant.API_PERMISSION_KEY_READ);
        KmsKeyMetadata metadata = keyQueryRepository.findMetadata(context.getPrincipal().getOwnerPrincipalId(), keyRef)
                .orElseThrow(KmsNotFoundException::new);
        return json(200, key(metadata));
    }

    private Map<String, Object> key(KmsKeyMetadata metadata) {
        KmsKey key = metadata.getKey();
        Map<String, Object> response = map();
        response.put("keyRef", key.getKeyRef());
        response.put("keyAlias", key.getKeyAlias());
        response.put("purpose", key.getPurpose().getCode());
        response.put("algorithm", key.getAlgorithm().getCode());
        response.put("state", key.getState().getCode());
        response.put("activeVersion", key.getActiveVersion());
        response.put("rowVersion", Long.valueOf(key.getRowVersion()));
        response.put("createdAt", KmsHttpJson.utcMillis(metadata.getCreatedAt()));
        response.put("updatedAt", KmsHttpJson.utcMillis(metadata.getUpdatedAt()));
        return response;
    }
}
