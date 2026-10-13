package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackKind;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackType;
import io.github.surezzzzzz.sdk.feedback.server.support.OwnerHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 枚举能力与 Owner 派生 Helper 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication.class)
class FeedbackEnumAndOwnerHelperTest {

    @Test
    @DisplayName("三枚举标准能力：getAllCodes/toString/fromCode 往返一致")
    void testEnumCapabilities() {
        assertArrayEquals(new String[]{"proactive", "intercepted"}, FeedbackKind.getAllCodes());
        assertArrayEquals(new String[]{"bug", "feature", "experience", "other"}, FeedbackType.getAllCodes());
        assertEquals("bug", FeedbackType.BUG.toString());
        assertEquals(FeedbackType.FEATURE, FeedbackType.fromCode("feature"));
        assertEquals(FeedbackKind.INTERCEPTED, FeedbackKind.fromCode("intercepted"));
        assertNull(FeedbackType.fromCode("unknown"));
        log.info("输出: 枚举 code 往返与 getAllCodes 一致");
    }

    @Test
    @DisplayName("Owner 派生：构造与剥离互逆，非 iam 前缀返回 null")
    void testOwnerHelper() {
        String owner = OwnerHelper.buildIamOwner("subject-1");
        log.info("输入: subjectId=subject-1, owner={}", owner);
        assertEquals("iam:subject-1", owner);
        assertEquals("subject-1", OwnerHelper.extractSubjectId(owner));
        assertEquals("aksk:client-1", OwnerHelper.buildAkskOwner("client-1"));
        assertNull(OwnerHelper.extractSubjectId("aksk:client-1"));
        assertNull(OwnerHelper.extractSubjectId(null));
        log.info("输出: iam 前缀构造/剥离互逆，aksk 与 null 正确分流");
    }
}
