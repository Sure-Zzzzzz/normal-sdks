package io.github.surezzzzzz.sdk.auth.data.permission.core.claim;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionValidationException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 数据授权结构化 Claim 映射器。
 *
 * @author surezzzzzz
 */
public final class DataGrantDocumentClaimMapper {

    private static final Set<String> DOCUMENT_FIELDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            SimpleDataPermissionConstant.FIELD_PROTOCOL,
            SimpleDataPermissionConstant.FIELD_VERSION,
            SimpleDataPermissionConstant.FIELD_GRANTS)));
    private static final Set<String> GRANT_FIELDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            SimpleDataPermissionConstant.FIELD_RESOURCE,
            SimpleDataPermissionConstant.FIELD_ACTIONS,
            SimpleDataPermissionConstant.FIELD_ALL,
            SimpleDataPermissionConstant.FIELD_CONSTRAINTS)));
    private static final Set<String> CONSTRAINT_FIELDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            SimpleDataPermissionConstant.FIELD_DIMENSION,
            SimpleDataPermissionConstant.FIELD_OPERATOR,
            SimpleDataPermissionConstant.FIELD_VALUES)));

    private DataGrantDocumentClaimMapper() {
        throw new UnsupportedOperationException(SimpleDataPermissionConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 将授权文档转换为不可变结构化 Claim。
     *
     * @param document 授权文档
     * @return 结构化 Claim
     */
    public static Map<String, Object> toClaim(DataGrantDocument document) {
        if (document == null) {
            throw invalid(SimpleDataPermissionConstant.DETAIL_DOCUMENT_CANNOT_BE_NULL);
        }
        ClaimBudget budget = new ClaimBudget();
        budget.addNode();
        LinkedHashMap<String, Object> claim = new LinkedHashMap<String, Object>();
        putText(claim, SimpleDataPermissionConstant.FIELD_PROTOCOL, document.getProtocol(), budget);
        putText(claim, SimpleDataPermissionConstant.FIELD_VERSION, document.getVersion(), budget);
        claim.put(SimpleDataPermissionConstant.FIELD_GRANTS, writeGrants(document.getGrants(), budget));
        return Collections.unmodifiableMap(claim);
    }

    /**
     * 将结构化 Claim 还原为授权文档。
     *
     * @param claim 结构化 Claim
     * @return 授权文档
     */
    public static DataGrantDocument fromClaim(Object claim) {
        ClaimBudget budget = new ClaimBudget();
        Map<?, ?> documentClaim = requireMap(claim, budget);
        requireExactFields(documentClaim, DOCUMENT_FIELDS);
        try {
            String protocol = requireText(documentClaim, SimpleDataPermissionConstant.FIELD_PROTOCOL, budget);
            String version = requireText(documentClaim, SimpleDataPermissionConstant.FIELD_VERSION, budget);
            List<?> grantClaims = requireList(documentClaim, SimpleDataPermissionConstant.FIELD_GRANTS, budget);
            List<DataGrant> grants = readGrants(grantClaims, budget);
            return new DataGrantDocument(protocol, version, grants);
        } catch (DataPermissionValidationException exception) {
            if (ErrorCode.INVALID_DOCUMENT.equals(exception.getErrorCode())) {
                throw exception;
            }
            throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID, exception);
        }
    }

    private static List<Object> writeGrants(List<DataGrant> grants, ClaimBudget budget) {
        List<Object> result = new ArrayList<Object>();
        budget.addNode();
        for (DataGrant grant : grants) {
            budget.addNode();
            LinkedHashMap<String, Object> claim = new LinkedHashMap<String, Object>();
            putText(claim, SimpleDataPermissionConstant.FIELD_RESOURCE, grant.getResource(), budget);
            claim.put(SimpleDataPermissionConstant.FIELD_ACTIONS, writeTexts(grant.getActions(), budget));
            budget.addNode();
            claim.put(SimpleDataPermissionConstant.FIELD_ALL, grant.isAll());
            claim.put(SimpleDataPermissionConstant.FIELD_CONSTRAINTS, writeConstraints(grant.getConstraints(), budget));
            result.add(Collections.unmodifiableMap(claim));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Object> writeConstraints(List<DataConstraint> constraints, ClaimBudget budget) {
        List<Object> result = new ArrayList<Object>();
        budget.addNode();
        for (DataConstraint constraint : constraints) {
            budget.addNode();
            LinkedHashMap<String, Object> claim = new LinkedHashMap<String, Object>();
            putText(claim, SimpleDataPermissionConstant.FIELD_DIMENSION, constraint.getDimension(), budget);
            putText(claim, SimpleDataPermissionConstant.FIELD_OPERATOR, constraint.getOperator().getCode(), budget);
            claim.put(SimpleDataPermissionConstant.FIELD_VALUES, writeTexts(constraint.getValues(), budget));
            result.add(Collections.unmodifiableMap(claim));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Object> writeTexts(List<String> texts, ClaimBudget budget) {
        List<Object> result = new ArrayList<Object>();
        budget.addNode();
        for (String text : texts) {
            budget.addNode();
            budget.addText(text);
            result.add(text);
        }
        return Collections.unmodifiableList(result);
    }

    private static void putText(Map<String, Object> claim, String fieldName, String value, ClaimBudget budget) {
        budget.addNode();
        budget.addText(value);
        claim.put(fieldName, value);
    }

    private static List<DataGrant> readGrants(List<?> claims, ClaimBudget budget) {
        List<DataGrant> grants = new ArrayList<DataGrant>();
        for (Object claim : claims) {
            Map<?, ?> grantClaim = requireMap(claim, budget);
            requireExactFields(grantClaim, GRANT_FIELDS);
            String resource = requireText(grantClaim, SimpleDataPermissionConstant.FIELD_RESOURCE, budget);
            List<String> actions = readTexts(requireList(grantClaim, SimpleDataPermissionConstant.FIELD_ACTIONS, budget), budget);
            boolean all = requireBoolean(grantClaim, SimpleDataPermissionConstant.FIELD_ALL, budget);
            List<DataConstraint> constraints = readConstraints(
                    requireList(grantClaim, SimpleDataPermissionConstant.FIELD_CONSTRAINTS, budget), budget);
            grants.add(new DataGrant(resource, actions, all, constraints));
        }
        return grants;
    }

    private static List<DataConstraint> readConstraints(List<?> claims, ClaimBudget budget) {
        List<DataConstraint> constraints = new ArrayList<DataConstraint>();
        for (Object claim : claims) {
            Map<?, ?> constraintClaim = requireMap(claim, budget);
            requireExactFields(constraintClaim, CONSTRAINT_FIELDS);
            String dimension = requireText(constraintClaim, SimpleDataPermissionConstant.FIELD_DIMENSION, budget);
            String operatorCode = requireText(constraintClaim, SimpleDataPermissionConstant.FIELD_OPERATOR, budget);
            DataConstraintOperator operator = DataConstraintOperator.fromCode(operatorCode);
            if (operator == null) {
                throw invalid(String.format(SimpleDataPermissionConstant.DETAIL_UNSUPPORTED_CONSTRAINT_OPERATOR,
                        operatorCode));
            }
            List<String> values = readTexts(requireList(constraintClaim, SimpleDataPermissionConstant.FIELD_VALUES, budget),
                    budget);
            constraints.add(new DataConstraint(dimension, operator, values));
        }
        return constraints;
    }

    private static List<String> readTexts(List<?> claims, ClaimBudget budget) {
        List<String> texts = new ArrayList<String>();
        for (Object claim : claims) {
            if (!(claim instanceof String)) {
                throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID);
            }
            String text = (String) claim;
            budget.addNode();
            budget.addText(text);
            texts.add(text);
        }
        return texts;
    }

    private static Map<?, ?> requireMap(Object claim, ClaimBudget budget) {
        if (!(claim instanceof Map)) {
            throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID);
        }
        budget.addNode();
        return (Map<?, ?>) claim;
    }

    private static List<?> requireList(Map<?, ?> claim, String fieldName, ClaimBudget budget) {
        Object value = claim.get(fieldName);
        if (!(value instanceof List)) {
            throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID);
        }
        budget.addNode();
        return (List<?>) value;
    }

    private static String requireText(Map<?, ?> claim, String fieldName, ClaimBudget budget) {
        Object value = claim.get(fieldName);
        if (!(value instanceof String)) {
            throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID);
        }
        String text = (String) value;
        budget.addNode();
        budget.addText(text);
        return text;
    }

    private static boolean requireBoolean(Map<?, ?> claim, String fieldName, ClaimBudget budget) {
        Object value = claim.get(fieldName);
        if (!(value instanceof Boolean)) {
            throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID);
        }
        budget.addNode();
        return (Boolean) value;
    }

    private static void requireExactFields(Map<?, ?> claim, Set<String> expectedFields) {
        Set<String> actualFields = new HashSet<String>();
        for (Object key : claim.keySet()) {
            if (!(key instanceof String)) {
                throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID);
            }
            actualFields.add((String) key);
        }
        if (!actualFields.equals(expectedFields)) {
            throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID);
        }
    }

    private static DataPermissionValidationException invalid(String detail) {
        return new DataPermissionValidationException(ErrorCode.INVALID_DOCUMENT,
                String.format(ErrorMessage.INVALID_DOCUMENT, detail));
    }

    private static DataPermissionValidationException invalid(String detail, Throwable cause) {
        return new DataPermissionValidationException(ErrorCode.INVALID_DOCUMENT,
                String.format(ErrorMessage.INVALID_DOCUMENT, detail), cause);
    }

    private static final class ClaimBudget {

        private int nodeCount;
        private int textByteCount;

        private void addNode() {
            nodeCount++;
            if (nodeCount > SimpleDataPermissionConstant.MAX_CLAIM_NODE_COUNT) {
                throw invalid(String.format(SimpleDataPermissionConstant.DETAIL_CLAIM_NODE_COUNT_TOO_LARGE,
                        SimpleDataPermissionConstant.MAX_CLAIM_NODE_COUNT));
            }
        }

        private void addText(String value) {
            if (value == null) {
                throw invalid(SimpleDataPermissionConstant.DETAIL_CLAIM_SHAPE_INVALID);
            }
            textByteCount += value.getBytes(StandardCharsets.UTF_8).length;
            if (textByteCount > SimpleDataPermissionConstant.MAX_CLAIM_TEXT_BYTE_COUNT) {
                throw invalid(String.format(SimpleDataPermissionConstant.DETAIL_CLAIM_TEXT_BYTE_COUNT_TOO_LARGE,
                        SimpleDataPermissionConstant.MAX_CLAIM_TEXT_BYTE_COUNT));
            }
        }
    }
}
