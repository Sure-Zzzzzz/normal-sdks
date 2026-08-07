package io.github.surezzzzzz.sdk.auth.authorization.application.core.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.exception.ApplicationAuthorizationException;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 应用授权上下文结构化 Claim 映射器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class ApplicationAuthorizationContextClaimMapperTest {

    @Test
    void shouldRoundTripCanonicalImmutableClaim() {
        ApplicationAuthorizationContext context = context(true, document());

        Map<String, Object> claim = ApplicationAuthorizationContextClaimMapper.toClaim(context);
        ApplicationAuthorizationContext restored = ApplicationAuthorizationContextClaimMapper.fromClaim(claim);

        assertEquals(Arrays.asList(
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
                        SimpleApplicationAuthorizationConstant.FIELD_EXPIRES_AT),
                new ArrayList<String>(claim.keySet()), "Claim字段必须保持协议顺序");
        assertEquals(context, restored, "结构化Claim必须无损还原应用授权上下文");
        assertThrows(UnsupportedOperationException.class, () -> claim.put("extra", Boolean.TRUE),
                "Claim根对象必须不可修改");
        @SuppressWarnings("unchecked")
        List<Object> apiPermissions = (List<Object>) claim.get(
                SimpleApplicationAuthorizationConstant.FIELD_API_PERMISSIONS);
        assertThrows(UnsupportedOperationException.class, () -> apiPermissions.clear(),
                "Claim权限集合必须不可修改");
        @SuppressWarnings("unchecked")
        Map<String, Object> dataGrantDocument = (Map<String, Object>) claim.get(
                SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT);
        assertThrows(UnsupportedOperationException.class, () -> dataGrantDocument.clear(),
                "Claim数据授权文档必须不可修改");
        @SuppressWarnings("unchecked")
        List<Object> grants = (List<Object>) dataGrantDocument.get("grants");
        assertThrows(UnsupportedOperationException.class, () -> grants.clear(),
                "Claim数据授权项集合必须不可修改");
        @SuppressWarnings("unchecked")
        Map<String, Object> grant = (Map<String, Object>) grants.get(0);
        assertThrows(UnsupportedOperationException.class, () -> grant.put("extra", Boolean.TRUE),
                "Claim数据授权项必须不可修改");
        @SuppressWarnings("unchecked")
        List<Object> constraints = (List<Object>) grant.get("constraints");
        assertThrows(UnsupportedOperationException.class, () -> constraints.clear(),
                "Claim数据约束集合必须不可修改");
        @SuppressWarnings("unchecked")
        Map<String, Object> constraint = (Map<String, Object>) constraints.get(0);
        assertThrows(UnsupportedOperationException.class, () -> constraint.clear(),
                "Claim数据约束必须不可修改");
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) constraint.get("values");
        assertThrows(UnsupportedOperationException.class, () -> values.clear(),
                "Claim数据约束值集合必须不可修改");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectAmbiguousClaimShapesAndNestedDataGrant() {
        Map<String, Object> valid = mutableClaim();
        valid.put("extra", Boolean.TRUE);
        assertInvalid(valid, "未知字段必须拒绝");

        valid = mutableClaim();
        valid.remove(SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_DIGEST);
        assertInvalid(valid, "缺失字段必须拒绝");

        valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_ROLES, Collections.singleton("role-a"));
        assertInvalid(valid, "Set不能替代List权限集合");

        valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_ROLES, new String[]{"role-a"});
        assertInvalid(valid, "数组不能替代List权限集合");

        valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_ROLES, Collections.<Object>singletonList(Integer.valueOf(1)));
        assertInvalid(valid, "非字符串权限必须拒绝");

        valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_ADMITTED, "true");
        assertInvalid(valid, "字符串不能替代准入布尔值");

        valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_ADMITTED, Boolean.FALSE);
        assertInvalid(valid, "未准入Claim必须拒绝");

        valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_AUTHORIZATION_VERSION, Double.valueOf(1D));
        assertInvalid(valid, "浮点数不能替代授权版本");

        valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_ISSUED_AT, "100");
        assertInvalid(valid, "字符串不能替代签发时间");

        valid = mutableClaim();
        @SuppressWarnings("unchecked")
        Map<String, Object> dataGrant = (Map<String, Object>) valid.get(
                SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT);
        dataGrant.put("extra", Boolean.TRUE);
        assertInvalid(valid, "嵌套数据授权未知字段必须拒绝");

        valid = mutableClaim();
        dataGrant = (Map<String, Object>) valid.get(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT);
        @SuppressWarnings("unchecked")
        List<Object> grants = (List<Object>) dataGrant.get("grants");
        @SuppressWarnings("unchecked")
        Map<String, Object> grant = (Map<String, Object>) grants.get(0);
        grant.put("constraints", Collections.<Object>singletonList(Collections.singletonMap("dimension", "scope_a")));
        assertInvalid(valid, "不完整嵌套数据授权必须拒绝");
    }

    @Test
    void shouldAcceptJsonIntegerValues() {
        Map<String, Object> valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_AUTHORIZATION_VERSION, Integer.valueOf(1));
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_ISSUED_AT, Integer.valueOf(100));
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_EXPIRES_AT, Integer.valueOf(200));

        ApplicationAuthorizationContext restored = ApplicationAuthorizationContextClaimMapper.fromClaim(valid);

        assertEquals(1L, restored.getAuthorizationVersion(), "JSON Integer授权版本必须还原为Long");
        assertEquals(Instant.ofEpochSecond(100L), restored.getIssuedAt(), "JSON Integer签发时间必须还原");
        assertEquals(Instant.ofEpochSecond(200L), restored.getExpiresAt(), "JSON Integer过期时间必须还原");
    }

    @Test
    void shouldRejectBudgetOverflowAndInvalidSubjectType() {
        Map<String, Object> valid = mutableClaim();
        StringBuilder oversized = new StringBuilder();
        for (int index = 0; index <= SimpleApplicationAuthorizationConstant.MAX_CLAIM_TEXT_BYTE_COUNT; index++) {
            oversized.append('a');
        }
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_ID, oversized.toString());
        assertInvalid(valid, "超过Claim文本预算必须拒绝");

        valid = mutableClaim();
        StringBuilder utf8Oversized = new StringBuilder();
        for (int index = 0; index <= SimpleApplicationAuthorizationConstant.MAX_CLAIM_TEXT_BYTE_COUNT / 3; index++) {
            utf8Oversized.append('中');
        }
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_ID, utf8Oversized.toString());
        assertInvalid(valid, "超过Claim UTF-8文本预算必须拒绝");

        valid = mutableClaim();
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_TYPE, "UNKNOWN");
        assertInvalid(valid, "未知主体类型必须拒绝");
    }

    @Test
    void shouldRejectNodeBudgetOverflow() {
        Map<String, Object> valid = mutableClaim();
        List<Object> roles = new ArrayList<Object>();
        for (int index = 0; index < SimpleApplicationAuthorizationConstant.MAX_CLAIM_NODE_COUNT; index++) {
            roles.add("");
        }
        valid.put(SimpleApplicationAuthorizationConstant.FIELD_ROLES, roles);

        assertInvalid(valid, "超过Claim节点预算必须拒绝");
    }

    private Map<String, Object> mutableClaim() {
        Map<String, Object> claim = new LinkedHashMap<String, Object>(
                ApplicationAuthorizationContextClaimMapper.toClaim(context(true, document())));
        claim.put(SimpleApplicationAuthorizationConstant.FIELD_ROLES,
                new ArrayList<Object>((List<?>) claim.get(SimpleApplicationAuthorizationConstant.FIELD_ROLES)));
        claim.put(SimpleApplicationAuthorizationConstant.FIELD_PAGE_PERMISSIONS,
                new ArrayList<Object>((List<?>) claim.get(SimpleApplicationAuthorizationConstant.FIELD_PAGE_PERMISSIONS)));
        claim.put(SimpleApplicationAuthorizationConstant.FIELD_API_PERMISSIONS,
                new ArrayList<Object>((List<?>) claim.get(SimpleApplicationAuthorizationConstant.FIELD_API_PERMISSIONS)));
        claim.put(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT,
                mutableMap((Map<?, ?>) claim.get(SimpleApplicationAuthorizationConstant.FIELD_DATA_GRANT_DOCUMENT)));
        return claim;
    }

    private Map<String, Object> mutableMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = mutableMap((Map<?, ?>) value);
            } else if (value instanceof List) {
                value = mutableList((List<?>) value);
            }
            result.put((String) entry.getKey(), value);
        }
        return result;
    }

    private List<Object> mutableList(List<?> source) {
        List<Object> result = new ArrayList<Object>();
        for (Object value : source) {
            if (value instanceof Map) {
                result.add(mutableMap((Map<?, ?>) value));
            } else if (value instanceof List) {
                result.add(mutableList((List<?>) value));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private ApplicationAuthorizationContext context(boolean admitted, DataGrantDocument dataGrantDocument) {
        return new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN,
                "subject-a",
                "application-a",
                admitted,
                Arrays.asList("role-b", "role-a"),
                Collections.singletonList("page.read"),
                Arrays.asList("api.write", "api.read"),
                dataGrantDocument,
                1L,
                "manifest-1",
                "digest-a",
                Instant.ofEpochSecond(100L),
                Instant.ofEpochSecond(200L));
    }

    private DataGrantDocument document() {
        DataConstraint constraint = new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.singletonList("scope-value"));
        DataGrant grant = new DataGrant("test_resource", Collections.singletonList("read"), false,
                Collections.singletonList(constraint));
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(grant));
    }

    private void assertInvalid(Object claim, String message) {
        ApplicationAuthorizationException exception = assertThrows(ApplicationAuthorizationException.class,
                () -> ApplicationAuthorizationContextClaimMapper.fromClaim(claim), message);
        log.info("Claim拒绝结果：{}", exception.getMessage());
        assertEquals(ErrorCode.INVALID_CONTEXT, exception.getErrorCode(), message);
        assertTrue(exception.getMessage().contains("应用授权上下文无效"), message);
    }
}
