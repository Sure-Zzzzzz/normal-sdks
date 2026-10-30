package io.github.surezzzzzz.sdk.license.client.spi;

import io.github.surezzzzzz.sdk.license.client.constant.ClientProtocolConstant;
import io.github.surezzzzzz.sdk.license.client.model.AuthorizationRequest;
import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.model.ClientDecisionBinding;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

/**
 * 响应严格校验：未知字段/未知协议版本/对象不关联一律整份拒绝。
 *
 * <p>不尝试部分采用；校验失败抛 {@link ProtocolViolationException}，业务不执行。</p>
 *
 * @author surezzzzzz
 */
public final class ClientResponseValidator {

    private static final Set<String> DECISION_FIELDS = new HashSet<String>(Arrays.asList(
            "allowed", "action", "modelVersion", "commercialPhase", "connectivityPhase", "evaluatedAt",
            "recheckAt", "reasonCategory", "requestId", "reservationFenceReferences",
            "productId", "grantId", "subjectBinding", "grantRevision", "grantStateVersion"));
    private static final Set<String> COMMERCIAL_PHASES = new HashSet<String>(Arrays.asList(
            "NOT_STARTED", "ACTIVE", "COMMERCIAL_GRACE", "ENDED"));
    private static final Set<String> CONNECTIVITY_PHASES = new HashSet<String>(Arrays.asList(
            "NOT_APPLICABLE", "NORMAL", "OUTAGE", "ENDED"));
    private static final Set<String> REASONS = new HashSet<String>(Arrays.asList(
            "ENTITLED", "RULE_DENIED", "FACT_MISSING", "COMMERCIAL_PHASE", "CONNECTIVITY_PHASE",
            "SUBJECT_INELIGIBLE", "GRANT_SUSPENDED", "REVOKED", "CAPABILITY_MISSING"));

    private ClientResponseValidator() {
    }

    /**
     * 四个官方传输件共享的判定解析。DENY 可在授权尚未识别时声明商业阶段为 null；
     * ALLOW 必须携带有效阶段和 ENTITLED，不能将缺字段或类型错误降级为业务拒绝。
     */
    public static ClientDecision parseDecision(Map<String, Object> response) {
        if (response == null) {
            throw new ProtocolViolationException(ClientProtocolConstant.EMPTY_RESPONSE);
        }
        if (!DECISION_FIELDS.containsAll(response.keySet())) {
            throw new ProtocolViolationException(ClientProtocolConstant.UNKNOWN_FIELD);
        }
        Object outcome = response.get("allowed");
        if (!(outcome instanceof Boolean)) {
            throw invalid("allowed");
        }
        String action = requiredText(response, "action");
        long modelVersion = integer(response.get("modelVersion"), "modelVersion");
        if (modelVersion < 0L || !response.containsKey("commercialPhase")) {
            throw invalid(modelVersion < 0L ? "modelVersion" : "commercialPhase");
        }
        Object phaseValue = response.get("commercialPhase");
        String phase = phaseValue == null ? null : requiredText(response, "commercialPhase");
        if (phase != null && !COMMERCIAL_PHASES.contains(phase)) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_PHASE);
        }
        if (response.containsKey("connectivityPhase")) {
            String connectivity = requiredText(response, "connectivityPhase");
            if (!CONNECTIVITY_PHASES.contains(connectivity)) {
                throw new ProtocolViolationException(ClientProtocolConstant.INVALID_PHASE);
            }
        }
        long evaluatedAt = integer(response.get("evaluatedAt"), "evaluatedAt");
        if (evaluatedAt < 0L || evaluatedAt > ClientProtocolConstant.EPOCH_SECONDS_MAX) {
            throw invalid("evaluatedAt");
        }
        requiredText(response, "requestId");
        String reason = requiredText(response, "reasonCategory");
        if (!REASONS.contains(reason)) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_REASON);
        }
        if (!response.containsKey("recheckAt") || !response.containsKey("reservationFenceReferences")) {
            throw invalid("recheckAt/reservationFenceReferences");
        }
        Long recheckAt = response.get("recheckAt") == null ? null
                : integer(response.get("recheckAt"), "recheckAt");
        if (recheckAt != null && (recheckAt < 0 || recheckAt > ClientProtocolConstant.EPOCH_SECONDS_MAX)) {
            throw invalid("recheckAt");
        }
        boolean allowed = (Boolean) outcome;
        if (allowed && (phase == null || !"ENTITLED".equals(reason))
                || !allowed && "ENTITLED".equals(reason)) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_OUTCOME);
        }
        boolean hasMetadata = response.containsKey("productId") || response.containsKey("grantId")
                || response.containsKey("subjectBinding") || response.containsKey("grantRevision")
                || response.containsKey("grantStateVersion");
        String productId = null;
        String grantId = null;
        ClientDecisionBinding binding = null;
        Long grantRevision = null;
        Long grantStateVersion = null;
        if (hasMetadata) {
            productId = requiredIdentifier(response, "productId");
            grantId = requiredIdentifier(response, "grantId");
            binding = parseBinding(response.get("subjectBinding"));
            grantRevision = positiveInteger(response.get("grantRevision"), "grantRevision");
            grantStateVersion = positiveInteger(response.get("grantStateVersion"), "grantStateVersion");
        }
        if (allowed && !hasMetadata) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        java.util.List<String> fences = new java.util.ArrayList<String>();
        Object rawFences = response.get("reservationFenceReferences");
        if (!(rawFences instanceof java.util.List)) {
            throw invalid("reservationFenceReferences");
        }
        java.util.List<?> rawFenceList = (java.util.List<?>) rawFences;
        if (rawFenceList.size() > ClientProtocolConstant.RESERVATION_REFERENCES_MAX) {
            throw invalid("reservationFenceReferences");
        }
        java.util.Set<String> uniqueFences = new java.util.HashSet<String>();
        for (Object fence : rawFenceList) {
            if (!(fence instanceof String) || ((String) fence).isEmpty()
                    || ((String) fence).length() > 128 || !uniqueFences.add((String) fence)) {
                throw invalid("reservationFenceReferences");
            }
            fences.add((String) fence);
        }
        String connectivity = response.containsKey("connectivityPhase")
                ? (String) response.get("connectivityPhase") : null;
        String requestId = (String) response.get("requestId");
        if (allowed) {
            return ClientDecision.allow(action, modelVersion, phase, connectivity, evaluatedAt, requestId,
                    recheckAt, fences, productId, grantId, binding, grantRevision, grantStateVersion);
        }
        return hasMetadata
                ? ClientDecision.deny(action, modelVersion, phase, connectivity, evaluatedAt, requestId,
                recheckAt, reason, productId, grantId, binding, grantRevision, grantStateVersion)
                : ClientDecision.deny(action, modelVersion, phase, connectivity, evaluatedAt, requestId,
                recheckAt, reason);
    }

    private static String requiredText(Map<String, Object> response, String field) {
        Object value = response.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw invalid(field);
        }
        return (String) value;
    }

    private static String requiredIdentifier(Map<String, Object> response, String field) {
        String value = requiredText(response, field);
        if (value.length() > ClientProtocolConstant.IDENTIFIER_MAX_LENGTH
                || value.getBytes(StandardCharsets.UTF_8).length > ClientProtocolConstant.IDENTIFIER_MAX_LENGTH
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid(field);
        }
        return value;
    }

    /** 不截断浮点数或溢出值，不把坏版本或截止时刻伪装成整数。 */
    private static long integer(Object value, String field) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof java.math.BigInteger) {
            java.math.BigInteger integer = new java.math.BigInteger(value.toString());
            if (integer.abs().compareTo(java.math.BigInteger.valueOf(ClientProtocolConstant.SAFE_INTEGER_MAX)) <= 0) {
                return integer.longValue();
            }
        }
        throw invalid(field);
    }

    private static Long positiveInteger(Object value, String field) {
        long result = integer(value, field);
        if (result < 1L) {
            throw invalid(field);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static ClientDecisionBinding parseBinding(Object raw) {
        if (!(raw instanceof Map)) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        Map<String, Object> binding = (Map<String, Object>) raw;
        Object rawKind = binding.get("kind");
        if (!(rawKind instanceof String)
                || !("DEPLOYMENT".equals(rawKind) || "PERSON".equals(rawKind) || "SERVICE".equals(rawKind))) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        String kind = (String) rawKind;
        if ("DEPLOYMENT".equals(kind)) {
            if (!keysOnly(binding, "kind", "activationId", "activationGeneration")) {
                throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
            }
            String activationId = requiredIdentifier(binding, "activationId");
            if (!binding.containsKey("activationGeneration") || binding.get("activationGeneration") == null) {
                throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
            }
            Long generation = positiveInteger(binding.get("activationGeneration"), "activationGeneration");
            return ClientDecisionBinding.deployment(activationId, generation);
        }
        if (!keysOnly(binding, "kind", "subjectRef", "assignmentId", "assignmentGeneration")
                || !(binding.get("subjectRef") instanceof Map)) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        Map<String, Object> subjectRef = (Map<String, Object>) binding.get("subjectRef");
        if (!keysOnly(subjectRef, "kind", "sourceId", "subjectId")
                || !kind.equals(subjectRef.get("kind"))) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        String sourceId = requiredIdentifier(subjectRef, "sourceId");
        String subjectId = requiredIdentifier(subjectRef, "subjectId");
        boolean hasAssignmentId = binding.containsKey("assignmentId") && binding.get("assignmentId") != null;
        boolean hasAssignmentGeneration = binding.containsKey("assignmentGeneration")
                && binding.get("assignmentGeneration") != null;
        if ((binding.containsKey("assignmentId") && binding.get("assignmentId") == null)
                || (binding.containsKey("assignmentGeneration")
                && binding.get("assignmentGeneration") == null)) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        if (hasAssignmentId != hasAssignmentGeneration) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        String assignmentId = hasAssignmentId ? requiredIdentifier(binding, "assignmentId") : null;
        Long assignmentGeneration = hasAssignmentGeneration
                ? positiveInteger(binding.get("assignmentGeneration"), "assignmentGeneration") : null;
        return ClientDecisionBinding.personService(kind, sourceId, subjectId, assignmentId, assignmentGeneration);
    }

    private static boolean keysOnly(Map<String, Object> values, String... keys) {
        java.util.Set<String> allowed = new java.util.HashSet<String>(Arrays.asList(keys));
        return allowed.containsAll(values.keySet());
    }

    private static ProtocolViolationException invalid(String field) {
        return new ProtocolViolationException(ClientProtocolConstant.INVALID_FIELD + field);
    }

    /**
     * 校验判定响应动作与请求一致；线形态和已知原因分类由 parseDecision 先行校验。
     *
     * <p>这是兼容入口，但仍要求 ALLOW 已带完整授权关联元数据；需要防止跨产品、授权或主体
     * 串用时，必须调用 {@link #validateDecision(ClientDecision, AuthorizationRequest)}。
     * 官方传输件只走后者。</p>
     */
    public static ClientDecision validateDecision(ClientDecision decision, String expectedAction) {
        if (decision == null) {
            throw new ProtocolViolationException("空响应");
        }
        if (expectedAction == null || !expectedAction.equals(decision.getAction())) {
            throw new ProtocolViolationException("响应动作与请求不一致");
        }
        if (decision.isAllowed()
                && (!"ENTITLED".equals(decision.getReasonCategory())
                || decision.getCommercialPhase() == null
                || decision.getProductId() == null || decision.getGrantId() == null
                || decision.getSubjectBinding() == null || decision.getGrantRevision() == null
                || decision.getGrantStateVersion() == null)) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        return decision;
    }

    /** 校验判定响应与完整请求上下文关联，避免把其他产品或授权的允许结果串用。 */
    public static ClientDecision validateDecision(ClientDecision decision, AuthorizationRequest request) {
        if (request == null) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        if (request.getProductId() == null || request.getProductId().isEmpty()
                || request.getGrantId() == null || request.getGrantId().isEmpty()
                || request.getSubjectKind() == null || request.getSubjectKind().isEmpty()
                || request.getAction() == null || request.getAction().isEmpty()) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        validateDecision(decision, request.getAction());
        if (!decision.isAllowed() && decision.getProductId() == null && decision.getGrantId() == null
                && decision.getSubjectBinding() == null && decision.getGrantRevision() == null
                && decision.getGrantStateVersion() == null) {
            return decision;
        }
        if (decision.getProductId() == null || decision.getGrantId() == null
                || decision.getSubjectBinding() == null || decision.getGrantRevision() == null
                || decision.getGrantStateVersion() == null
                || !request.getProductId().equals(decision.getProductId())
                || !request.getGrantId().equals(decision.getGrantId())) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        ClientDecisionBinding binding = decision.getSubjectBinding();
        if (!request.getSubjectKind().equals(binding.getKind())) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        if ("DEPLOYMENT".equals(request.getSubjectKind())) {
            if (request.getActivationId() == null
                    || !request.getActivationId().equals(binding.getActivationId())
                    || binding.getSourceId() != null || binding.getSubjectId() != null
                    || binding.getAssignmentId() != null || binding.getAssignmentGeneration() != null) {
                throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
            }
        } else if (!java.util.Objects.equals(request.getAssignmentId(), binding.getAssignmentId())
                || binding.getActivationId() != null || binding.getActivationGeneration() != null
                || binding.getSourceId() == null || binding.getSubjectId() == null) {
            throw new ProtocolViolationException(ClientProtocolConstant.INVALID_BINDING);
        }
        return decision;
    }

    /** 响应协议异常：调用方不得以本地旧结论替代。 */
    public static final class ProtocolViolationException extends RuntimeException {

        public ProtocolViolationException(String message) {
            super(message);
        }
    }
}
