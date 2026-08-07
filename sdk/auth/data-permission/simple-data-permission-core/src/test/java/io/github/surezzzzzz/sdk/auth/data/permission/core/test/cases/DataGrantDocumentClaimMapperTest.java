package io.github.surezzzzzz.sdk.auth.data.permission.core.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.claim.DataGrantDocumentClaimMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionValidationException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据授权结构化 Claim 映射器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DataGrantDocumentClaimMapperTest {

    @Test
    void shouldRoundTripCanonicalImmutableClaim() {
        DataGrantDocument document = document();
        Map<String, Object> claim = DataGrantDocumentClaimMapper.toClaim(document);
        DataGrantDocument restored = DataGrantDocumentClaimMapper.fromClaim(claim);

        log.info("结构化Claim：{}", claim);
        assertEquals(Arrays.asList(SimpleDataPermissionConstant.FIELD_PROTOCOL,
                        SimpleDataPermissionConstant.FIELD_VERSION, SimpleDataPermissionConstant.FIELD_GRANTS),
                new ArrayList<String>(claim.keySet()), "文档字段必须保持协议顺序");
        assertEquals(document, restored, "结构化Claim必须无损还原授权文档");
        assertThrows(UnsupportedOperationException.class,
                () -> claim.put("extra", Boolean.TRUE), "Claim根对象必须不可修改");
        @SuppressWarnings("unchecked")
        List<Object> grants = (List<Object>) claim.get(SimpleDataPermissionConstant.FIELD_GRANTS);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstGrant = (Map<String, Object>) grants.get(0);
        assertEquals(Arrays.asList(SimpleDataPermissionConstant.FIELD_RESOURCE,
                        SimpleDataPermissionConstant.FIELD_ACTIONS, SimpleDataPermissionConstant.FIELD_ALL,
                        SimpleDataPermissionConstant.FIELD_CONSTRAINTS), new ArrayList<String>(firstGrant.keySet()),
                "授权项字段必须保持协议顺序");
        @SuppressWarnings("unchecked")
        List<Object> firstConstraints = (List<Object>) firstGrant.get(SimpleDataPermissionConstant.FIELD_CONSTRAINTS);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstConstraint = (Map<String, Object>) firstConstraints.get(0);
        assertEquals(Arrays.asList(SimpleDataPermissionConstant.FIELD_DIMENSION,
                        SimpleDataPermissionConstant.FIELD_OPERATOR, SimpleDataPermissionConstant.FIELD_VALUES),
                new ArrayList<String>(firstConstraint.keySet()), "约束字段必须保持协议顺序");
        assertThrows(UnsupportedOperationException.class,
                () -> grants.clear(), "Claim授权项集合必须不可修改");
        @SuppressWarnings("unchecked")
        Map<String, Object> grant = (Map<String, Object>) grants.get(0);
        assertThrows(UnsupportedOperationException.class,
                () -> grant.put("extra", Boolean.TRUE), "Claim嵌套对象必须不可修改");
        @SuppressWarnings("unchecked")
        List<Object> actions = (List<Object>) grant.get(SimpleDataPermissionConstant.FIELD_ACTIONS);
        assertThrows(UnsupportedOperationException.class,
                () -> actions.clear(), "Claim动作集合必须不可修改");
        @SuppressWarnings("unchecked")
        List<Object> constraints = (List<Object>) grant.get(SimpleDataPermissionConstant.FIELD_CONSTRAINTS);
        assertThrows(UnsupportedOperationException.class,
                () -> constraints.clear(), "Claim约束集合必须不可修改");
        @SuppressWarnings("unchecked")
        Map<String, Object> constraint = (Map<String, Object>) constraints.get(0);
        assertThrows(UnsupportedOperationException.class,
                () -> constraint.put("extra", Boolean.TRUE), "Claim约束对象必须不可修改");
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) constraint.get(SimpleDataPermissionConstant.FIELD_VALUES);
        assertThrows(UnsupportedOperationException.class,
                () -> values.clear(), "Claim约束值集合必须不可修改");
    }

    @Test
    void shouldRejectAmbiguousClaimShapes() {
        Map<String, Object> valid = new LinkedHashMap<String, Object>(DataGrantDocumentClaimMapper.toClaim(document()));
        assertInvalid("not-a-map", "根节点不是Map必须拒绝");
        valid.put("extra", Boolean.TRUE);
        assertInvalid(valid, "未知字段必须拒绝");

        Map<String, Object> missing = new LinkedHashMap<String, Object>(DataGrantDocumentClaimMapper.toClaim(document()));
        missing.remove(SimpleDataPermissionConstant.FIELD_GRANTS);
        assertInvalid(missing, "缺失字段必须拒绝");

        Map<String, Object> wrongType = new LinkedHashMap<String, Object>(DataGrantDocumentClaimMapper.toClaim(document()));
        wrongType.put(SimpleDataPermissionConstant.FIELD_GRANTS, Collections.singleton("not-a-list"));
        assertInvalid(wrongType, "Set不能替代List");

        Map<Object, Object> nonStringKey = new LinkedHashMap<Object, Object>();
        nonStringKey.put(Integer.valueOf(1), "value");
        assertInvalid(nonStringKey, "非字符串字段名必须拒绝");

        assertInvalid(new Object[]{valid}, "数组不能替代Map");
        assertInvalid(null, "null根节点必须拒绝");

        Map<String, Object> nullList = mutableClaim();
        nullList.put(SimpleDataPermissionConstant.FIELD_GRANTS, null);
        assertInvalid(nullList, "null不能替代List");
    }

    @Test
    void shouldRejectNestedWrongTypesAndUnsupportedOperator() {
        Map<String, Object> claim = mutableClaim();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grants = (List<Map<String, Object>>) claim.get(SimpleDataPermissionConstant.FIELD_GRANTS);
        grants.get(0).put(SimpleDataPermissionConstant.FIELD_ALL, "true");
        assertInvalid(claim, "字符串布尔值必须拒绝");

        claim = mutableClaim();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operatorGrants = (List<Map<String, Object>>) claim.get(
                SimpleDataPermissionConstant.FIELD_GRANTS);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) operatorGrants.get(0).get(
                SimpleDataPermissionConstant.FIELD_CONSTRAINTS);
        constraints.get(0).put(SimpleDataPermissionConstant.FIELD_OPERATOR, "UNKNOWN");
        assertInvalid(claim, "不支持操作符必须拒绝");

        claim = mutableClaim();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> textGrants = (List<Map<String, Object>>) claim.get(
                SimpleDataPermissionConstant.FIELD_GRANTS);
        textGrants.get(0).put(SimpleDataPermissionConstant.FIELD_RESOURCE, Integer.valueOf(1));
        assertInvalid(claim, "数字文本字段必须拒绝");
    }

    @Test
    void shouldRejectUnsupportedProtocolVersionAndInvalidGrantSemantics() {
        Map<String, Object> claim = mutableClaim();
        claim.put(SimpleDataPermissionConstant.FIELD_PROTOCOL, "other-protocol");
        assertInvalid(claim, "未知协议必须拒绝");

        claim = mutableClaim();
        claim.put(SimpleDataPermissionConstant.FIELD_VERSION, "2.0");
        assertInvalid(claim, "未知版本必须拒绝");

        claim = mutableClaim();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grants = (List<Map<String, Object>>) claim.get(
                SimpleDataPermissionConstant.FIELD_GRANTS);
        grants.get(0).put(SimpleDataPermissionConstant.FIELD_ALL, Boolean.TRUE);
        assertInvalid(claim, "全量授权与约束必须互斥");
    }

    @Test
    void shouldRejectClaimBudgetOverflow() {
        Map<String, Object> nodeOverflow = mutableClaim();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodeOverflowGrants = (List<Map<String, Object>>) nodeOverflow.get(
                SimpleDataPermissionConstant.FIELD_GRANTS);
        List<String> excessiveActions = new ArrayList<String>();
        for (int index = 0; index < SimpleDataPermissionConstant.MAX_CLAIM_NODE_COUNT; index++) {
            excessiveActions.add("read");
        }
        nodeOverflowGrants.get(0).put(SimpleDataPermissionConstant.FIELD_ACTIONS, excessiveActions);
        assertInvalid(nodeOverflow, "超过结构化Claim节点预算必须拒绝");

        Map<String, Object> claim = mutableClaim();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grants = (List<Map<String, Object>>) claim.get(SimpleDataPermissionConstant.FIELD_GRANTS);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) grants.get(0).get(
                SimpleDataPermissionConstant.FIELD_CONSTRAINTS);
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) constraints.get(0).get(SimpleDataPermissionConstant.FIELD_VALUES);
        StringBuilder value = new StringBuilder();
        for (int index = 0; index <= SimpleDataPermissionConstant.MAX_CLAIM_TEXT_BYTE_COUNT; index++) {
            value.append('a');
        }
        values.set(0, value.toString());

        assertInvalid(claim, "超过结构化Claim文本预算必须拒绝");

        Map<String, Object> unicodeClaim = mutableClaim();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unicodeGrants = (List<Map<String, Object>>) unicodeClaim.get(
                SimpleDataPermissionConstant.FIELD_GRANTS);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unicodeConstraints = (List<Map<String, Object>>) unicodeGrants.get(0).get(
                SimpleDataPermissionConstant.FIELD_CONSTRAINTS);
        @SuppressWarnings("unchecked")
        List<String> unicodeValues = (List<String>) unicodeConstraints.get(0).get(
                SimpleDataPermissionConstant.FIELD_VALUES);
        unicodeValues.set(0, repeatedText("中", SimpleDataPermissionConstant.MAX_CLAIM_TEXT_BYTE_COUNT / 3 + 1));
        assertInvalid(unicodeClaim, "UTF-8多字节文本超过预算必须拒绝");
    }

    private String repeatedText(String text, int count) {
        StringBuilder result = new StringBuilder(text.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(text);
        }
        return result.toString();
    }

    private Map<String, Object> mutableClaim() {
        Map<String, Object> constraint = new LinkedHashMap<String, Object>();
        constraint.put(SimpleDataPermissionConstant.FIELD_DIMENSION, "scope_a");
        constraint.put(SimpleDataPermissionConstant.FIELD_OPERATOR, DataConstraintOperator.IN.getCode());
        constraint.put(SimpleDataPermissionConstant.FIELD_VALUES, new ArrayList<String>(Collections.singletonList("value-a")));

        Map<String, Object> grant = new LinkedHashMap<String, Object>();
        grant.put(SimpleDataPermissionConstant.FIELD_RESOURCE, "test_resource");
        grant.put(SimpleDataPermissionConstant.FIELD_ACTIONS, new ArrayList<String>(Collections.singletonList("read")));
        grant.put(SimpleDataPermissionConstant.FIELD_ALL, Boolean.FALSE);
        grant.put(SimpleDataPermissionConstant.FIELD_CONSTRAINTS,
                new ArrayList<Map<String, Object>>(Collections.singletonList(constraint)));

        Map<String, Object> claim = new LinkedHashMap<String, Object>();
        claim.put(SimpleDataPermissionConstant.FIELD_PROTOCOL, SimpleDataPermissionConstant.PROTOCOL);
        claim.put(SimpleDataPermissionConstant.FIELD_VERSION, SimpleDataPermissionConstant.VERSION);
        claim.put(SimpleDataPermissionConstant.FIELD_GRANTS,
                new ArrayList<Map<String, Object>>(Collections.singletonList(grant)));
        return claim;
    }

    private DataGrantDocument document() {
        DataConstraint constraint = new DataConstraint("scope_a", DataConstraintOperator.IN,
                Arrays.asList("value-b", "value-a"));
        DataGrant grant = new DataGrant("test_resource", Arrays.asList("export", "read"), false,
                Collections.singletonList(constraint));
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(grant));
    }

    private void assertInvalid(Object claim, String message) {
        DataPermissionValidationException exception = assertThrows(DataPermissionValidationException.class,
                () -> DataGrantDocumentClaimMapper.fromClaim(claim), message);
        log.info("Claim拒绝结果：{}", exception.getMessage());
        assertEquals(ErrorCode.INVALID_DOCUMENT, exception.getErrorCode(), message);
        assertTrue(exception.getMessage().contains("授权文档无效"), message);
    }
}
