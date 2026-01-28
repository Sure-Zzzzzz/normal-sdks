package io.github.surezzzzzz.sdk.auth.aksk.resource.core.test.constant;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleAkskResourceConstant 单元测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
class SimpleAkskResourceConstantTest {

    @Test
    void testFieldNames() {
        assertEquals("clientId", SimpleAkskResourceConstant.FIELD_CLIENT_ID);
        assertEquals("clientType", SimpleAkskResourceConstant.FIELD_CLIENT_TYPE);
        assertEquals("userId", SimpleAkskResourceConstant.FIELD_USER_ID);
        assertEquals("username", SimpleAkskResourceConstant.FIELD_USERNAME);
        assertEquals("securityContext", SimpleAkskResourceConstant.FIELD_SECURITY_CONTEXT);
        assertEquals("roles", SimpleAkskResourceConstant.FIELD_ROLES);
        assertEquals("scope", SimpleAkskResourceConstant.FIELD_SCOPE);
    }

    @Test
    void testJwtClaimNames() {
        assertEquals("client_id", SimpleAkskResourceConstant.JWT_CLAIM_CLIENT_ID);
        assertEquals("client_type", SimpleAkskResourceConstant.JWT_CLAIM_CLIENT_TYPE);
        assertEquals("user_id", SimpleAkskResourceConstant.JWT_CLAIM_USER_ID);
        assertEquals("username", SimpleAkskResourceConstant.JWT_CLAIM_USERNAME);
        assertEquals("security_context", SimpleAkskResourceConstant.JWT_CLAIM_SECURITY_CONTEXT);
        assertEquals("scope", SimpleAkskResourceConstant.JWT_CLAIM_SCOPE);
    }

    @Test
    void testHttpHeaderNames() {
        assertEquals("x-sure-auth-aksk-", SimpleAkskResourceConstant.DEFAULT_HEADER_PREFIX);
        assertEquals("x-sure-auth-aksk-user-id", SimpleAkskResourceConstant.HEADER_USER_ID);
        assertEquals("x-sure-auth-aksk-username", SimpleAkskResourceConstant.HEADER_USERNAME);
        assertEquals("x-sure-auth-aksk-client-id", SimpleAkskResourceConstant.HEADER_CLIENT_ID);
        assertEquals("x-sure-auth-aksk-client-type", SimpleAkskResourceConstant.HEADER_CLIENT_TYPE);
        assertEquals("x-sure-auth-aksk-security-context", SimpleAkskResourceConstant.HEADER_SECURITY_CONTEXT);
        assertEquals("x-sure-auth-aksk-roles", SimpleAkskResourceConstant.HEADER_ROLES);
        assertEquals("x-sure-auth-aksk-scope", SimpleAkskResourceConstant.HEADER_SCOPE);
    }

    @Test
    void testFieldToJwtClaimMapping() {
        assertNotNull(SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM);
        assertEquals(6, SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.size());

        assertEquals("client_id", SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.get("clientId"));
        assertEquals("client_type", SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.get("clientType"));
        assertEquals("user_id", SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.get("userId"));
        assertEquals("username", SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.get("username"));
        assertEquals("security_context", SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.get("securityContext"));
        assertEquals("scope", SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.get("scope"));
    }

    @Test
    void testJwtClaimToFieldMapping() {
        assertNotNull(SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD);
        assertEquals(6, SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.size());

        assertEquals("clientId", SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.get("client_id"));
        assertEquals("clientType", SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.get("client_type"));
        assertEquals("userId", SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.get("user_id"));
        assertEquals("username", SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.get("username"));
        assertEquals("securityContext", SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.get("security_context"));
        assertEquals("scope", SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.get("scope"));
    }

    @Test
    void testMappingConsistency() {
        // 验证双向映射的一致性
        SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.forEach((fieldName, jwtClaimName) -> {
            String reverseMappedFieldName = SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.get(jwtClaimName);
            assertEquals(fieldName, reverseMappedFieldName,
                    "Mapping inconsistency: " + fieldName + " -> " + jwtClaimName + " -> " + reverseMappedFieldName);
        });

        SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.forEach((jwtClaimName, fieldName) -> {
            String reverseMappedJwtClaimName = SimpleAkskResourceConstant.FIELD_TO_JWT_CLAIM.get(fieldName);
            assertEquals(jwtClaimName, reverseMappedJwtClaimName,
                    "Mapping inconsistency: " + jwtClaimName + " -> " + fieldName + " -> " + reverseMappedJwtClaimName);
        });
    }
}
