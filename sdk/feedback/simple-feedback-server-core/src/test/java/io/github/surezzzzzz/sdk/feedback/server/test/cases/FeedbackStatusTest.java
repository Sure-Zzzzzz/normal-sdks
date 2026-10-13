package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反馈状态机迁移矩阵测试（三态，无伪中间态；终态可重开）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication.class)
class FeedbackStatusTest {

    @Test
    @DisplayName("迁移矩阵：处理两向、终态重开两向合法，其余全部拒绝")
    void testTransitionMatrix() {
        assertTrue(FeedbackStatus.SUBMITTED.canTransitionTo(FeedbackStatus.RESOLVED));
        assertTrue(FeedbackStatus.SUBMITTED.canTransitionTo(FeedbackStatus.DISMISSED));
        assertTrue(FeedbackStatus.RESOLVED.canTransitionTo(FeedbackStatus.SUBMITTED));
        assertTrue(FeedbackStatus.DISMISSED.canTransitionTo(FeedbackStatus.SUBMITTED));
        log.info("输出: 四条合法迁移（处理两向+重开两向）通过");

        assertFalse(FeedbackStatus.RESOLVED.canTransitionTo(FeedbackStatus.DISMISSED));
        assertFalse(FeedbackStatus.DISMISSED.canTransitionTo(FeedbackStatus.RESOLVED));
        assertFalse(FeedbackStatus.SUBMITTED.canTransitionTo(FeedbackStatus.SUBMITTED));
        assertFalse(FeedbackStatus.RESOLVED.canTransitionTo(FeedbackStatus.RESOLVED));
        assertFalse(FeedbackStatus.DISMISSED.canTransitionTo(FeedbackStatus.DISMISSED));
        assertFalse(FeedbackStatus.SUBMITTED.canTransitionTo(null));
        log.info("输出: 终态直迁、自迁移与 null 目标全部拒绝");
    }

    @Test
    @DisplayName("fromCode 大小写不敏感，非法值返回 null")
    void testFromCode() {
        log.info("输入: submitted / SUBMITTED / unknown / null");
        assertEquals(FeedbackStatus.SUBMITTED, FeedbackStatus.fromCode("submitted"));
        assertEquals(FeedbackStatus.SUBMITTED, FeedbackStatus.fromCode("SUBMITTED"));
        assertNull(FeedbackStatus.fromCode("unknown"));
        assertNull(FeedbackStatus.fromCode(null));
        assertTrue(FeedbackStatus.isValid("resolved"));
        assertFalse(FeedbackStatus.isValid("processing"));
        log.info("输出: fromCode/isValid 语义符合规范");
    }
}
