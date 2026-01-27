package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.support.HeaderNameConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Header 名称转换工具测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class HeaderNameConverterTest {

    private static final String PREFIX = "x-sure-auth-aksk-";

    @Test
    public void testToCamelCaseSingleWord() {
        String result = HeaderNameConverter.toCamelCase("x-sure-auth-aksk-username", PREFIX);
        assertEquals("username", result);
    }

    @Test
    public void testToCamelCaseMultipleWords() {
        String result = HeaderNameConverter.toCamelCase("x-sure-auth-aksk-user-id", PREFIX);
        assertEquals("userId", result);
    }

    @Test
    public void testToCamelCaseThreeWords() {
        String result = HeaderNameConverter.toCamelCase("x-sure-auth-aksk-tenant-id", PREFIX);
        assertEquals("tenantId", result);
    }

    @Test
    public void testToCamelCaseFourWords() {
        String result = HeaderNameConverter.toCamelCase("x-sure-auth-aksk-security-context", PREFIX);
        assertEquals("securityContext", result);
    }

    @Test
    public void testToCamelCaseEmptyString() {
        String result = HeaderNameConverter.toCamelCase("", PREFIX);
        assertEquals("", result);
    }

    @Test
    public void testToCamelCaseNullString() {
        String result = HeaderNameConverter.toCamelCase(null, PREFIX);
        assertEquals("", result);
    }

    @Test
    public void testToCamelCaseNoPrefix() {
        String result = HeaderNameConverter.toCamelCase("user-id", PREFIX);
        assertEquals("userId", result);
    }

    @Test
    public void testToCamelCaseNullPrefix() {
        String result = HeaderNameConverter.toCamelCase("x-sure-auth-aksk-user-id", null);
        assertEquals("xSureAuthAkskUserId", result);
    }
}
