package io.github.surezzzzzz.sdk.auth.authorization.application.core.claim;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.exception.ApplicationAuthorizationException;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.claim.DataGrantDocumentClaimMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.*;

/**
 * 应用授权上下文结构化 Claim 映射器。
 *
 * @author surezzzzzz
 */
public final class ApplicationAuthorizationContextClaimMapper {

    private static final Set<String> CONTEXT_FIELDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL,
            SimpleApplicationAuthorizationConstant.FIELD_VERSION,
            SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_TYPE,
            SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_ID,
            SimpleApplicationAuthorizationConstant.FIELD_APPLICATION_CODE,
            SimpleApplicationAuthorizationConstant.FIELD_ADMITTED,
            SimpleApplicationAuthorizationConstant.FIELD_ROLES,
            SimpleApplicationAuthorizationConstant.FIELD_PAGE_PERMISSIONS,
            SimpleApplicationAuthorizationConstant.FIELD_API_PERMISSIONS,
            SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT,
            SimpleApplicationAuthorizationConstant.FIELD_AUTHORIZATION_VERSION,
            SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_VERSION,
            SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_DIGEST,
            SimpleApplicationAuthorizationConstant.FIELD_ISSUED_AT,
            SimpleApplicationAuthorizationConstant.FIELD_EXPIRES_AT)));

    private ApplicationAuthorizationContextClaimMapper() {
        throw new UnsupportedOperationException(SimpleApplicationAuthorizationConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 将授权上下文转换为不可变结构化 Claim。
     *
     * @param context 授权上下文
     * @return 结构化 Claim
     */
    public static Map<String, Object> toClaim(ApplicationAuthorizationContext context) {
        if (context == null) {
            throw invalid(SimpleApplicationAuthorizationConstant.DETAIL_CANNOT_BE_NULL);
        }
        ClaimBudget budget = new ClaimBudget();
        budget.addNode();
        LinkedHashMap<String, Object> claim = new LinkedHashMap<String, Object>();
        putText(claim, SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL, context.getProtocol(), budget);
        putText(claim, SimpleApplicationAuthorizationConstant.FIELD_VERSION, context.getVersion(), budget);
        putText(claim, SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_TYPE, context.getSubjectType().getCode(), budget);
        putText(claim, SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_ID, context.getSubjectId(), budget);
        putText(claim, SimpleApplicationAuthorizationConstant.FIELD_APPLICATION_CODE, context.getApplicationCode(), budget);
        putBoolean(claim, SimpleApplicationAuthorizationConstant.FIELD_ADMITTED, context.isAdmitted(), budget);
        putTexts(claim, SimpleApplicationAuthorizationConstant.FIELD_ROLES, context.getRoles(), budget);
        putTexts(claim, SimpleApplicationAuthorizationConstant.FIELD_PAGE_PERMISSIONS, context.getPagePermissions(), budget);
        putTexts(claim, SimpleApplicationAuthorizationConstant.FIELD_API_PERMISSIONS, context.getApiPermissions(), budget);
        putDataGrantDocument(claim, context.getDataGrantDocument(), budget);
        putLong(claim, SimpleApplicationAuthorizationConstant.FIELD_AUTHORIZATION_VERSION,
                context.getAuthorizationVersion(), budget);
        putText(claim, SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_VERSION, context.getManifestVersion(), budget);
        putText(claim, SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_DIGEST, context.getManifestDigest(), budget);
        putLong(claim, SimpleApplicationAuthorizationConstant.FIELD_ISSUED_AT, context.getIssuedAt().getEpochSecond(), budget);
        putLong(claim, SimpleApplicationAuthorizationConstant.FIELD_EXPIRES_AT, context.getExpiresAt().getEpochSecond(), budget);
        return Collections.unmodifiableMap(claim);
    }

    /**
     * 将结构化 Claim 还原为授权上下文。
     *
     * @param claim 结构化 Claim
     * @return 授权上下文
     */
    public static ApplicationAuthorizationContext fromClaim(Object claim) {
        ClaimBudget budget = new ClaimBudget();
        Map<?, ?> contextClaim = requireMap(claim, budget);
        requireExactFields(contextClaim);
        String subjectTypeCode = requireText(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_TYPE, budget);
        ApplicationAuthorizationSubjectType subjectType = ApplicationAuthorizationSubjectType.fromCode(subjectTypeCode);
        if (subjectType == null) {
            throw invalid(SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_TYPE);
        }
        return new ApplicationAuthorizationContext(
                requireText(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL, budget),
                requireText(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_VERSION, budget),
                subjectType,
                requireText(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_ID, budget),
                requireText(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_APPLICATION_CODE, budget),
                requireBoolean(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_ADMITTED, budget),
                requireTexts(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_ROLES, budget),
                requireTexts(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_PAGE_PERMISSIONS, budget),
                requireTexts(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_API_PERMISSIONS, budget),
                requireDataGrantDocument(contextClaim, budget),
                requireLong(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_AUTHORIZATION_VERSION, budget),
                requireText(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_VERSION, budget),
                requireText(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_DIGEST, budget),
                requireInstant(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_ISSUED_AT, budget),
                requireInstant(contextClaim, SimpleApplicationAuthorizationConstant.FIELD_EXPIRES_AT, budget));
    }

    private static void putText(Map<String, Object> claim, String fieldName, String value, ClaimBudget budget) {
        budget.addNode();
        budget.addText(value);
        claim.put(fieldName, value);
    }

    private static void putBoolean(Map<String, Object> claim, String fieldName, boolean value, ClaimBudget budget) {
        budget.addNode();
        claim.put(fieldName, Boolean.valueOf(value));
    }

    private static void putLong(Map<String, Object> claim, String fieldName, long value, ClaimBudget budget) {
        budget.addNode();
        claim.put(fieldName, Long.valueOf(value));
    }

    private static void putTexts(Map<String, Object> claim, String fieldName, List<String> values, ClaimBudget budget) {
        List<Object> texts = new ArrayList<Object>();
        budget.addNode();
        for (String value : values) {
            budget.addNode();
            budget.addText(value);
            texts.add(value);
        }
        claim.put(fieldName, Collections.unmodifiableList(texts));
    }

    private static void putDataGrantDocument(Map<String, Object> claim, DataGrantDocument document, ClaimBudget budget) {
        budget.addNode();
        if (document == null) {
            claim.put(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT, null);
            return;
        }
        Map<String, Object> dataGrantClaim = DataGrantDocumentClaimMapper.toClaim(document);
        budget.addValue(dataGrantClaim);
        claim.put(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT, dataGrantClaim);
    }

    private static Map<?, ?> requireMap(Object claim, ClaimBudget budget) {
        if (!(claim instanceof Map)) {
            throw invalid(SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL);
        }
        budget.addNode();
        return (Map<?, ?>) claim;
    }

    private static void requireExactFields(Map<?, ?> claim) {
        Set<String> actualFields = new HashSet<String>();
        for (Object key : claim.keySet()) {
            if (!(key instanceof String)) {
                throw invalid(SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL);
            }
            actualFields.add((String) key);
        }
        if (!CONTEXT_FIELDS.equals(actualFields)) {
            throw invalid(SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL);
        }
    }

    private static String requireText(Map<?, ?> claim, String fieldName, ClaimBudget budget) {
        Object value = claim.get(fieldName);
        if (!(value instanceof String)) {
            throw invalid(fieldName);
        }
        budget.addNode();
        budget.addText((String) value);
        return (String) value;
    }

    private static boolean requireBoolean(Map<?, ?> claim, String fieldName, ClaimBudget budget) {
        Object value = claim.get(fieldName);
        if (!(value instanceof Boolean)) {
            throw invalid(fieldName);
        }
        budget.addNode();
        return ((Boolean) value).booleanValue();
    }

    private static long requireLong(Map<?, ?> claim, String fieldName, ClaimBudget budget) {
        Object value = claim.get(fieldName);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw invalid(fieldName);
        }
        budget.addNode();
        return ((Number) value).longValue();
    }

    private static Instant requireInstant(Map<?, ?> claim, String fieldName, ClaimBudget budget) {
        long epochSecond = requireLong(claim, fieldName, budget);
        try {
            return Instant.ofEpochSecond(epochSecond);
        } catch (DateTimeException exception) {
            throw invalid(fieldName, exception);
        }
    }

    private static List<String> requireTexts(Map<?, ?> claim, String fieldName, ClaimBudget budget) {
        Object value = claim.get(fieldName);
        if (!(value instanceof List)) {
            throw invalid(fieldName);
        }
        budget.addNode();
        List<String> texts = new ArrayList<String>();
        for (Object text : (List<?>) value) {
            if (!(text instanceof String)) {
                throw invalid(fieldName);
            }
            budget.addNode();
            budget.addText((String) text);
            texts.add((String) text);
        }
        return texts;
    }

    private static DataGrantDocument requireDataGrantDocument(Map<?, ?> claim, ClaimBudget budget) {
        Object value = claim.get(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT);
        budget.addNode();
        if (value == null) {
            return null;
        }
        budget.addValue(value);
        try {
            return DataGrantDocumentClaimMapper.fromClaim(value);
        } catch (RuntimeException exception) {
            throw invalid(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT, exception);
        }
    }

    private static ApplicationAuthorizationException invalid(String detail) {
        return invalid(detail, null);
    }

    private static ApplicationAuthorizationException invalid(String detail, Throwable cause) {
        return new ApplicationAuthorizationException(ErrorCode.INVALID_CONTEXT,
                String.format(ErrorMessage.INVALID_CONTEXT, detail), cause);
    }

    private static final class ClaimBudget {

        private int nodeCount;
        private int textByteCount;

        private void addNode() {
            nodeCount++;
            if (nodeCount > SimpleApplicationAuthorizationConstant.MAX_CLAIM_NODE_COUNT) {
                throw invalid(SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL);
            }
        }

        private void addText(String value) {
            if (value == null) {
                throw invalid(SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL);
            }
            textByteCount += value.getBytes(StandardCharsets.UTF_8).length;
            if (textByteCount > SimpleApplicationAuthorizationConstant.MAX_CLAIM_TEXT_BYTE_COUNT) {
                throw invalid(SimpleApplicationAuthorizationConstant.FIELD_PROTOCOL);
            }
        }

        private void addValue(Object value) {
            if (value instanceof String) {
                addNode();
                addText((String) value);
                return;
            }
            if (value instanceof Boolean || value instanceof Long || value == null) {
                addNode();
                return;
            }
            if (value instanceof List) {
                addNode();
                for (Object element : (List<?>) value) {
                    addValue(element);
                }
                return;
            }
            if (value instanceof Map) {
                addNode();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw invalid(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT);
                    }
                    addText((String) entry.getKey());
                    addValue(entry.getValue());
                }
                return;
            }
            throw invalid(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT);
        }
    }
}
