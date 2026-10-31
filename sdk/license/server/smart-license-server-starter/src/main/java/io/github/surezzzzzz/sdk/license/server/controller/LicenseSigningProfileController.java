package io.github.surezzzzzz.sdk.license.server.controller;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.SigningProfileRegistrationService;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import java.time.Instant;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签名档案 HTTP 面：GET/POST /signing-profiles、GET/PATCH /signing-profiles/{id}
 * 与产品用途选择 PUT /products/{id}/signing-profiles/{purpose}。
 *
 * <p>档案为不可变历史：登记/轮换经 {@link SigningProfileRegistrationService}（严格钥材料校验+
 * 事务内 CAS 切换），轮换后旧档案 RETIRED 永久保留；协议面只见 kid 与公钥指纹，
 * keyRef 不出现在任何响应或日志。退役档案的钥销毁治理走 key-disposal-advice 判定，
 * 不由本面代理 KMS 销毁操作。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@RestController
@RequestMapping("/api/license/v1")
public class LicenseSigningProfileController {

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final SigningProfileRepository profiles;
    private final SigningOperationRepository operations;
    private final SigningProfileRegistrationService registration;

    public LicenseSigningProfileController(SigningProfileRepository profiles,
                                           SigningOperationRepository operations,
                                           SigningProfileRegistrationService registration) {
        this.profiles = profiles;
        this.operations = operations;
        this.registration = registration;
    }

    @RequireApiPermission("license.signing-profile.read")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_SIGNING_PROFILE, action = "read")
    @GetMapping("/signing-profiles")
    public ResponseEntity<String> list(HttpServletRequest request,
                                       @CurrentDataAccessPlan
                                               DataAccessPlan plan,
                                       @RequestParam(defaultValue = "ACTIVE") String status) {
        require(request, LicenseApi.SIGNING_PROFILE_READ);
        if (!("ACTIVE".equals(status) || "RETIRED".equals(status) || "ALL".equals(status))) {
            throw LicenseHttpException.unprocessable("签名档案状态筛选值无效");
        }
        LicenseAccessScope scope =
                LicenseAccessScope.from(plan, "profileId");
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        java.util.List<SigningProfileEntity> all = scope.resolve(
                profiles::findAll,
                profiles::findByProfileIdIn);
        // 默认只看在用档案；status=RETIRED/ALL 看不可变历史（验收与审计面）
        java.util.List<SigningProfileEntity> result = new java.util.ArrayList<SigningProfileEntity>();
        for (SigningProfileEntity profile : all) {
            if ("ALL".equals(status) || status.equals(profile.getStatus())) {
                result.add(profile);
            }
        }
        result.forEach(profile -> data.add(view(profile)));
        body.put("total", result.size());
        body.put("page", 1);
        body.put("size", Math.max(1, data.size()));
        body.put("totalPages", 1);
        return ResponseEntity.ok(body.toString());
    }

    @RequireApiPermission("license.signing-profile.manage")
    @PostMapping("/signing-profiles")
    public ResponseEntity<String> create(@RequestBody String body, HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.SIGNING_PROFILE_MANAGE);
        support.requireDataAll(context, LicenseApi.RESOURCE_SIGNING_PROFILE, "manage");
        ObjectNode input = support.object(body, "productId", "purpose", "kid", "keyRef", "keyVersion",
                "expectedProfileId", "expectedRowVersion");
        return respond(registration.register(
                support.text(input, "productId", true),
                support.text(input, "purpose", true),
                support.text(input, "kid", true),
                support.text(input, "keyRef", true),
                requirePositiveVersion(input),
                nullableText(input, "expectedProfileId"),
                nullableVersion(input, "expectedRowVersion")));
    }

    /** 产品用途选择（设计 §4）：为产品的指定用途登记档案并设为在用。 */
    @RequireApiPermission("license.signing-profile.manage")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_SIGNING_PROFILE, action = "manage")
    @PutMapping("/products/{productId}/signing-profiles/{purpose}")
    public ResponseEntity<String> selectForPurpose(@PathVariable String productId,
                                                   @PathVariable String purpose,
                                                   @RequestBody String body,
                                                   HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.SIGNING_PROFILE_MANAGE);
        // 此入口会生成新的档案标识，须与集合创建入口采用同一治理范围，不能绕过 DATA。
        support.requireDataAll(context, LicenseApi.RESOURCE_SIGNING_PROFILE, "manage");
        ObjectNode input = support.object(body, "kid", "keyRef", "keyVersion",
                "expectedProfileId", "expectedRowVersion");
        return respond(registration.register(productId, purpose,
                support.text(input, "kid", true),
                support.text(input, "keyRef", true),
                requirePositiveVersion(input),
                nullableText(input, "expectedProfileId"),
                nullableVersion(input, "expectedRowVersion")));
    }

    @RequireApiPermission("license.signing-profile.read")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_SIGNING_PROFILE, action = "read")
    @GetMapping("/signing-profiles/{profileId}")
    public ResponseEntity<String> get(@PathVariable String profileId,
                                      @CurrentDataAccessPlan
                                              DataAccessPlan plan,
                                      HttpServletRequest request) {
        require(request, LicenseApi.SIGNING_PROFILE_READ);
        LicenseAccessScope.from(plan, "profileId").requireVisible(profileId);
        SigningProfileEntity entity = profiles.findById(profileId)
                .orElseThrow(LicenseHttpException::notFound);
        return ResponseEntity.ok(view(entity).toString());
    }

    @RequireApiPermission("license.signing-profile.manage")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_SIGNING_PROFILE, action = "manage")
    @PatchMapping("/signing-profiles/{profileId}")
    public ResponseEntity<String> patch(@PathVariable String profileId, @RequestBody String body,
                                        @CurrentDataAccessPlan
                                                DataAccessPlan plan,
                                        HttpServletRequest request) {
        require(request, LicenseApi.SIGNING_PROFILE_MANAGE);
        LicenseAccessScope.from(plan, "profileId").requireAllowed(profileId);
        ObjectNode input = support.object(body, "status", "expectedRowVersion");
        SigningProfileEntity entity = profiles.findById(profileId)
                .orElseThrow(LicenseHttpException::notFound);
        if (!input.has("expectedRowVersion") || !input.get("expectedRowVersion").isInt()
                || input.get("expectedRowVersion").asLong() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
        String status = support.text(input, "status", true);
        if (!"RETIRED".equals(status)) {
            throw LicenseHttpException.unprocessable("仅允许转换到 RETIRED");
        }
        entity.retire();
        return ResponseEntity.ok(view(profiles.save(entity)).toString());
    }

    /**
     * 退役档案钥销毁保护判定：列出仍引用该档案的历史凭据保留窗口，
     * 窗口未过不得安排 KMS 销毁（License 不代理销毁，仅给治理判定）。
     */
    @RequireApiPermission("license.signing-profile.read")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_SIGNING_PROFILE, action = "read")
    @GetMapping("/signing-profiles/{profileId}/key-disposal-advice")
    public ResponseEntity<String> disposalAdvice(@PathVariable String profileId,
                                                 @CurrentDataAccessPlan
                                                         DataAccessPlan plan,
                                                 HttpServletRequest request) {
        require(request, LicenseApi.SIGNING_PROFILE_READ);
        LicenseAccessScope.from(plan, "profileId").requireVisible(profileId);
        SigningProfileEntity entity = profiles.findById(profileId)
                .orElseThrow(LicenseHttpException::notFound);
        if (!"RETIRED".equals(entity.getStatus())) {
            throw LicenseHttpException.unprocessable("仅退役档案需要销毁保护判定");
        }
        java.util.List<SigningOperationEntity> ready = operations
                .findBySigningProfileIdAndState(profileId, "READY");
        long now = Instant.now().getEpochSecond();
        long retainUntil = 0L;
        int referencing = 0;
        for (SigningOperationEntity task : ready) {
            long expiry = credentialRetainEpoch(task.getFrozenPayload());
            if (expiry > now) {
                referencing++;
                retainUntil = Math.max(retainUntil, expiry);
            }
        }
        ObjectNode body = StrictJson.mapper().createObjectNode();
        body.put("profileId", entity.getProfileId());
        body.put("kid", entity.getKid());
        body.put("status", entity.getStatus());
        body.put("retiredAt", entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
        body.put("referencingLiveCredentials", referencing);
        // 永久凭据及无法确认的历史载荷保留无截止；不能将 Long.MAX_VALUE 转成合法日期。
        boolean retainIndefinitely = referencing > 0 && retainUntil == Long.MAX_VALUE;
        body.put("retainIndefinitely", retainIndefinitely);
        if (referencing > 0 && !retainIndefinitely) {
            body.put("retainUntilEpoch", retainUntil);
            body.put("retainUntilIso", Instant.ofEpochSecond(retainUntil).toString());
        } else {
            body.putNull("retainUntilEpoch");
            body.putNull("retainUntilIso");
        }
        body.put("advice", referencing > 0
                ? "RETAIN_UNTIL_WINDOW_PASSED" : "SAFE_TO_SCHEDULE_DESTRUCTION");
        return ResponseEntity.ok(body.toString());
    }

    // ===== internal =====

    private int requirePositiveVersion(ObjectNode input) {
        Integer keyVersion = support.integer(input, "keyVersion", true);
        if (keyVersion == null || keyVersion < 1) {
            throw LicenseHttpException.unprocessable("keyVersion 必须为正整数");
        }
        return keyVersion;
    }

    private Long nullableVersion(ObjectNode input, String field) {
        return input.hasNonNull(field) && input.get(field).isNumber()
                ? input.get(field).asLong() : null;
    }

    private String nullableText(ObjectNode input, String field) {
        return input.hasNonNull(field) ? input.get(field).asText() : null;
    }

    /** 凭据保留窗口：有限截止取最大；永久或无法确认的载荷按无截止保留，不推断已交付凭据失效。 */
    private static long credentialRetainEpoch(String frozenPayload) {
        try {
            com.fasterxml.jackson.databind.JsonNode payload =
                    StrictJson.mapper().readTree(java.util.Base64.getUrlDecoder()
                            .decode(frozenPayload.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            if (payload == null || !payload.isObject()) {
                return Long.MAX_VALUE;
            }
            com.fasterxml.jackson.databind.JsonNode validity = payload.path("validity");
            if (ValidityPolicy.Kind.PERPETUAL.getCode().equals(validity.path("kind").asText())) {
                return Long.MAX_VALUE;
            }
            long expiry = 0L;
            for (com.fasterxml.jackson.databind.JsonNode deadline : new com.fasterxml.jackson.databind.JsonNode[] {
                    payload.path("exp"), validity.path("serviceUntil"), validity.path("commercialGraceUntil") }) {
                if (deadline.isMissingNode()) {
                    continue;
                }
                if (!deadline.isIntegralNumber() || !deadline.canConvertToLong()
                        || deadline.asLong() <= 0 || deadline.asLong() > Instant.MAX.getEpochSecond()) {
                    return Long.MAX_VALUE;
                }
                expiry = Math.max(expiry, deadline.asLong());
            }
            return expiry > 0 ? expiry : Long.MAX_VALUE;
        } catch (Exception failure) {
            // 解析不了按最长保留（保守：不误判可销毁）
            return Long.MAX_VALUE;
        }
    }

    private ResponseEntity<String> respond(SigningProfileRegistrationService.Registration registration) {
        ObjectNode view = view(registration.profile);
        view.put("rotatedFrom", registration.rotatedFrom);
        return ResponseEntity.status(registration.rotatedFrom == null ? 201 : 200).body(view.toString());
    }

    private LicenseRequestContext require(HttpServletRequest request, String permission) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, permission);
    }

    /** 视图不含 keyRef/keyVersion/公钥全文（协议面只见 kid、用途与状态；治理详情另有 DATA 准入）。 */
    private static ObjectNode view(SigningProfileEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("profileId", entity.getProfileId());
        view.put("productId", entity.getProductId());
        view.put("purpose", entity.getPurpose());
        view.put("kid", entity.getKid());
        view.put("status", entity.getStatus());
        view.put("rowVersion", entity.getRowVersion());
        view.put("publicKeyFingerprint", fingerprint(entity.getRegisteredPublicKeyHex()));
        view.put("createdAt", entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
        view.put("retiredAt", "RETIRED".equals(entity.getStatus()) && entity.getUpdatedAt() != null
                ? entity.getUpdatedAt().toString() : null);
        return view;
    }

    private static String fingerprint(String spkiHex) {
        try {
            byte[] spki = hexToBytes(spkiHex);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(spki);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                text.append(String.format("%02x", hashed[i]));
            }
            return text + "…";
        } catch (Exception failure) {
            return "";
        }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
