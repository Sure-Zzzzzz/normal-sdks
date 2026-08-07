package io.github.surezzzzzz.sdk.auth.authorization.application.core.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.exception.ApplicationAuthorizationException;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationRevokedEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 应用授权模型测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class ApplicationAuthorizationModelTest {

    @Test
    void shouldNormalizeImmutablePermissionCollections() {
        ApplicationAuthorizationContext context = context(Arrays.asList("api.write", "api.read", "api.read"));

        log.info("规范化后的API权限：{}", context.getApiPermissions());
        assertEquals(Arrays.asList("api.read", "api.write"), context.getApiPermissions(),
                "权限集合必须去重并按Unicode码点排序");
        assertThrows(UnsupportedOperationException.class, () -> context.getApiPermissions().clear(),
                "授权上下文权限集合必须不可修改");
    }

    @Test
    void shouldRejectInvalidProtocolVersionTimeWindowAndUnadmittedContext() {
        assertContextInvalid("other", SimpleApplicationAuthorizationConstant.VERSION, true,
                Instant.ofEpochSecond(100L), Instant.ofEpochSecond(200L), ErrorCode.INVALID_PROTOCOL,
                "未知协议必须拒绝");
        assertContextInvalid(SimpleApplicationAuthorizationConstant.PROTOCOL, "2.0", true,
                Instant.ofEpochSecond(100L), Instant.ofEpochSecond(200L), ErrorCode.UNSUPPORTED_VERSION,
                "未知协议版本必须拒绝");
        assertContextInvalid(SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION, true, Instant.ofEpochSecond(200L), Instant.ofEpochSecond(200L),
                ErrorCode.INVALID_CONTEXT, "无效时间窗必须拒绝");
        assertContextInvalid(SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION, false, Instant.ofEpochSecond(100L), Instant.ofEpochSecond(200L),
                ErrorCode.INVALID_CONTEXT, "未准入上下文必须在构造阶段拒绝");
    }

    @Test
    void shouldValidateRevocationEventWithoutAuthorizationPayload() {
        ApplicationAuthorizationRevokedEvent event = new ApplicationAuthorizationRevokedEvent(
                "event-a", "iam", ApplicationAuthorizationSubjectType.HUMAN, "subject-a", "application-a",
                1L, Instant.ofEpochSecond(100L), "authorization-change");

        assertEquals("event-a", event.getEventId(), "撤销事件必须保留事件标识");
        ApplicationAuthorizationException exception = assertThrows(ApplicationAuthorizationException.class,
                () -> new ApplicationAuthorizationRevokedEvent("event-a", "iam", ApplicationAuthorizationSubjectType.HUMAN,
                        "subject-a", "application-a", 0L, Instant.ofEpochSecond(100L), "authorization-change"),
                "非正撤销前授权版本必须拒绝");
        assertEquals(ErrorCode.INVALID_REVOCATION_EVENT, exception.getErrorCode(), "撤销事件必须使用专属错误码");
    }

    private ApplicationAuthorizationContext context(java.util.Collection<String> apiPermissions) {
        return new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN,
                "subject-a",
                "application-a",
                true,
                Collections.singletonList("role-reader"),
                Collections.singletonList("page.read"),
                apiPermissions,
                null,
                1L,
                "manifest-1",
                "digest-a",
                Instant.ofEpochSecond(100L),
                Instant.ofEpochSecond(200L));
    }

    private void assertContextInvalid(String protocol, String version, boolean admitted, Instant issuedAt,
                                      Instant expiresAt, String expectedErrorCode, String message) {
        ApplicationAuthorizationException exception = assertThrows(ApplicationAuthorizationException.class,
                () -> new ApplicationAuthorizationContext(
                        protocol,
                        version,
                        ApplicationAuthorizationSubjectType.HUMAN,
                        "subject-a",
                        "application-a",
                        admitted,
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        null,
                        1L,
                        "manifest-1",
                        "digest-a",
                        issuedAt,
                        expiresAt), message);
        log.info("应用授权上下文拒绝结果：{}", exception.getMessage());
        assertEquals(expectedErrorCode, exception.getErrorCode(), message);
    }
}
