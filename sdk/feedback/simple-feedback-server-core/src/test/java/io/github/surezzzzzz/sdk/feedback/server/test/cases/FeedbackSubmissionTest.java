package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackKind;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackStatus;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackType;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.server.support.OwnerHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 反馈提交聚合测试：字段校验边界、环境快照键白名单、状态机处理留痕。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication.class)
class FeedbackSubmissionTest {

    private static FeedbackSubmission buildValid() {
        return FeedbackSubmission.builder()
                .id(UUID.randomUUID().toString())
                .applicationCode("demo-app")
                .kind(FeedbackKind.PROACTIVE.getCode())
                .type(FeedbackType.BUG.getCode())
                .score(8)
                .content("示例反馈内容")
                .contactEmail("user@example.com")
                .submitterSubjectId("subject-1")
                .submitterDisplayName("示例用户")
                .owner(OwnerHelper.buildIamOwner("subject-1"))
                .contextPath("/app/demo/home")
                .environment("{\"ua\":\"demo\",\"viewport\":\"1920x1080\"}")
                .status(FeedbackStatus.SUBMITTED.getCode())
                .rowVersion(0)
                .idempotencyKey(UUID.randomUUID().toString())
                .idempotencyDigest("digest-1")
                .createdTime(Instant.now())
                .updatedTime(Instant.now())
                .build();
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    @Test
    @DisplayName("全字段合法的提交通过校验")
    void testValidatePass() {
        FeedbackSubmission submission = buildValid();
        log.info("输入: score={}, contentLength={}", submission.getScore(), submission.getContent().length());
        assertDoesNotThrow(submission::validate);
        log.info("输出: 校验通过");
    }

    @Test
    @DisplayName("评分越界 0 与 11 均被拒绝")
    void testScoreOutOfRange() {
        FeedbackSubmission low = buildValid();
        low.setScore(SimpleFeedbackServerConstant.SCORE_MIN - 1);
        FeedbackSubmission high = buildValid();
        high.setScore(SimpleFeedbackServerConstant.SCORE_MAX + 1);
        log.info("输入: scoreLow={}, scoreHigh={}", low.getScore(), high.getScore());
        assertEquals(ErrorCode.SCORE_OUT_OF_RANGE, assertThrows(SimpleFeedbackServerException.class,
                low::validate).getErrorCode());
        assertEquals(ErrorCode.SCORE_OUT_OF_RANGE, assertThrows(SimpleFeedbackServerException.class,
                high::validate).getErrorCode());
        log.info("输出: 两端越界均返回 SCORE_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("文本为空或超长被拒绝，长度上限恰好通过")
    void testContentBoundary() {
        FeedbackSubmission empty = buildValid();
        empty.setContent("");
        FeedbackSubmission tooLong = buildValid();
        tooLong.setContent(repeat('x', SimpleFeedbackServerConstant.CONTENT_MAX_LENGTH + 1));
        FeedbackSubmission atLimit = buildValid();
        atLimit.setContent(repeat('x', SimpleFeedbackServerConstant.CONTENT_MAX_LENGTH));
        atLimit.setContactEmail(null);
        log.info("输入: empty=0, tooLong={}, atLimit={}", tooLong.getContent().length(),
                atLimit.getContent().length());
        assertEquals(ErrorCode.CONTENT_INVALID,
                assertThrows(SimpleFeedbackServerException.class, empty::validate).getErrorCode());
        assertEquals(ErrorCode.CONTENT_INVALID,
                assertThrows(SimpleFeedbackServerException.class, tooLong::validate).getErrorCode());
        assertDoesNotThrow(atLimit::validate);
        log.info("输出: 空与超长拒绝，上限值通过");
    }

    @Test
    @DisplayName("邮箱非法格式拒绝，合法与空值通过")
    void testEmailValidation() {
        FeedbackSubmission invalid = buildValid();
        invalid.setContactEmail("not-an-email");
        FeedbackSubmission valid = buildValid();
        valid.setContactEmail("a@b.cn");
        FeedbackSubmission blank = buildValid();
        blank.setContactEmail("");
        log.info("输入: invalid={}, valid={}, blank空串", invalid.getContactEmail(), valid.getContactEmail());
        assertEquals(ErrorCode.EMAIL_INVALID,
                assertThrows(SimpleFeedbackServerException.class, invalid::validate).getErrorCode());
        assertDoesNotThrow(valid::validate);
        assertDoesNotThrow(blank::validate);
        log.info("输出: 非法拒绝，合法与空通过");
    }

    @Test
    @DisplayName("上下文路径与环境快照超长被拒绝")
    void testLengthBoundaries() {
        FeedbackSubmission contextTooLong = buildValid();
        contextTooLong.setContextPath(repeat('p', SimpleFeedbackServerConstant.CONTEXT_PATH_MAX_LENGTH + 1));
        FeedbackSubmission envTooLong = buildValid();
        envTooLong.setEnvironment(repeat('e', SimpleFeedbackServerConstant.ENVIRONMENT_MAX_LENGTH + 1));
        log.info("输入: contextPath={}, environment={}", contextTooLong.getContextPath().length(),
                envTooLong.getEnvironment().length());
        assertEquals(ErrorCode.FIELD_TOO_LONG,
                assertThrows(SimpleFeedbackServerException.class, contextTooLong::validate).getErrorCode());
        assertEquals(ErrorCode.FIELD_TOO_LONG,
                assertThrows(SimpleFeedbackServerException.class, envTooLong::validate).getErrorCode());
        log.info("输出: 两字段超长均返回 FIELD_TOO_LONG");
    }

    @Test
    @DisplayName("kind/type 非法枚举值被拒绝")
    void testEnumInvalid() {
        FeedbackSubmission badKind = buildValid();
        badKind.setKind("self-claimed");
        FeedbackSubmission badType = buildValid();
        badType.setType("urgent");
        log.info("输入: kind={}, type={}", badKind.getKind(), badType.getType());
        assertEquals(ErrorCode.ENUM_VALUE_INVALID,
                assertThrows(SimpleFeedbackServerException.class, badKind::validate).getErrorCode());
        assertEquals(ErrorCode.ENUM_VALUE_INVALID,
                assertThrows(SimpleFeedbackServerException.class, badType::validate).getErrorCode());
        log.info("输出: 非法枚举均返回 ENUM_VALUE_INVALID");
    }

    @Test
    @DisplayName("环境快照键白名单：四键通过，白名单外键拒绝")
    void testEnvironmentKeys() {
        log.info("输入: allowed={}, rejected=token", Arrays.toString(
                SimpleFeedbackServerConstant.ENVIRONMENT_ALLOWED_KEYS));
        assertDoesNotThrow(() -> FeedbackSubmission.validateEnvironmentKeys(
                Arrays.asList(SimpleFeedbackServerConstant.ENVIRONMENT_ALLOWED_KEYS)));
        assertEquals(ErrorCode.ENVIRONMENT_KEY_REJECTED, assertThrows(SimpleFeedbackServerException.class,
                () -> FeedbackSubmission.validateEnvironmentKeys(Arrays.asList("ua", "token"))).getErrorCode());
        log.info("输出: 白名单四键通过，额外键拒绝");
    }

    @Test
    @DisplayName("处理迁移：合法迁移成功并覆盖留痕，终态直迁与自迁移拒绝")
    void testApplyHandling() {
        FeedbackSubmission submission = buildValid();
        Instant now = Instant.now();
        log.info("输入: status={}", submission.getStatus());
        submission.applyHandling(FeedbackStatus.RESOLVED, "handler-1", "已修复", "感谢反馈", now);
        assertEquals(FeedbackStatus.RESOLVED.getCode(), submission.getStatus());
        assertEquals("handler-1", submission.getHandledBy());
        assertNotNull(submission.getHandledAt());
        assertEquals("感谢反馈", submission.getReply());
        assertEquals("handler-1", submission.getRepliedBy());
        log.info("输出: 处理后 status={}, repliedBy={}", submission.getStatus(), submission.getRepliedBy());

        assertEquals(ErrorCode.ILLEGAL_STATUS_TRANSITION, assertThrows(SimpleFeedbackServerException.class,
                () -> submission.applyHandling(FeedbackStatus.DISMISSED, "handler-1", null, null, now))
                .getErrorCode());
        assertEquals(ErrorCode.ILLEGAL_STATUS_TRANSITION, assertThrows(SimpleFeedbackServerException.class,
                () -> submission.applyHandling(FeedbackStatus.RESOLVED, "handler-1", null, null, now))
                .getErrorCode());
        log.info("输出: 终态直迁与自迁移均返回 ILLEGAL_STATUS_TRANSITION");

        submission.applyHandling(FeedbackStatus.SUBMITTED, "handler-2", "重开", null, now);
        assertEquals(FeedbackStatus.SUBMITTED.getCode(), submission.getStatus());
        assertEquals("handler-1", submission.getRepliedBy());
        assertEquals("handler-2", submission.getHandledBy());
        log.info("输出: 终态重开成功，reply 未新增时首次答复留痕保持（repliedBy=handler-1）");
    }
}
