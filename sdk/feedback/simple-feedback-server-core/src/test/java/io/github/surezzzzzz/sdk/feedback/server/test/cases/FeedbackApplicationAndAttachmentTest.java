package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 应用注册表与附件校验测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication.class)
class FeedbackApplicationAndAttachmentTest {

    private static FeedbackApplication buildValidApplication() {
        return FeedbackApplication.builder()
                .applicationCode("demo-app")
                .displayName("示例应用")
                .description("示例说明")
                .enabled(true)
                .ownerGroup("demo-group")
                .rowVersion(0)
                .createdBy("subject-1")
                .createdTime(Instant.now())
                .updatedTime(Instant.now())
                .build();
    }

    @Test
    @DisplayName("应用编码正则：合法形态通过，非法形态拒绝")
    void testApplicationCode() {
        List<String> validCodes = Arrays.asList("ab", "a-b", "demo-app", "app1", repeat('x', 64));
        List<String> invalidCodes = Arrays.asList(
                "a", "ab-", "-ab", "A-b", "demo_app", "demo app", "demo.app", null,
                repeat('x', 65));
        log.info("输入: valid={}, invalid 数={}", validCodes.size(), invalidCodes.size());
        for (String code : validCodes) {
            assertDoesNotThrow(() -> FeedbackApplication.validateCode(code), "合法编码被拒: " + code);
        }
        for (String code : invalidCodes) {
            assertEquals(ErrorCode.APPLICATION_CODE_INVALID, assertThrows(SimpleFeedbackServerException.class,
                    () -> FeedbackApplication.validateCode(code), "非法编码未拒: " + code).getErrorCode());
        }
        log.info("输出: 编码正则正反例全部符合预期");
    }

    @Test
    @DisplayName("应用登记字段：全字段通过，显示名超长拒绝")
    void testApplicationValidate() {
        FeedbackApplication valid = buildValidApplication();
        assertDoesNotThrow(valid::validate);
        FeedbackApplication tooLong = buildValidApplication();
        tooLong.setDisplayName(repeat('名', SimpleFeedbackServerConstant.APPLICATION_DISPLAY_NAME_MAX_LENGTH + 1));
        log.info("输入: displayNameLength={}", tooLong.getDisplayName().length());
        assertEquals(ErrorCode.FIELD_TOO_LONG,
                assertThrows(SimpleFeedbackServerException.class, tooLong::validate).getErrorCode());
        log.info("输出: 显示名超长返回 FIELD_TOO_LONG");
    }

    @Test
    @DisplayName("附件校验：null 与空集合通过，数量/单文件/总量/类型/文件名越界各返回对应错误码")
    void testAttachmentSpecs() {
        assertDoesNotThrow(() -> FeedbackAttachment.validateSpecs(null));
        assertDoesNotThrow(() -> FeedbackAttachment.validateSpecs(new ArrayList<>()));
        assertDoesNotThrow(() -> FeedbackAttachment.validateSpecs(Arrays.asList(spec("a.png", "image/png", 1024))));
        log.info("输出: null/空集合/单个合法附件通过");

        List<FeedbackAttachment.UploadSpec> tooMany = Arrays.asList(
                spec("1.png", "image/png", 1), spec("2.png", "image/png", 1), spec("3.png", "image/png", 1),
                spec("4.png", "image/png", 1));
        assertEquals(ErrorCode.ATTACHMENT_COUNT_EXCEEDED, assertThrows(SimpleFeedbackServerException.class,
                () -> FeedbackAttachment.validateSpecs(tooMany)).getErrorCode());

        assertEquals(ErrorCode.ATTACHMENT_SIZE_EXCEEDED, assertThrows(SimpleFeedbackServerException.class,
                () -> FeedbackAttachment.validateSpecs(Arrays.asList(
                        spec("big.png", "image/png", SimpleFeedbackServerConstant.ATTACHMENT_MAX_SIZE_BYTES + 1))))
                .getErrorCode());

        long each = 4 * 1024 * 1024L;
        assertEquals(ErrorCode.ATTACHMENT_TOTAL_SIZE_EXCEEDED, assertThrows(SimpleFeedbackServerException.class,
                () -> FeedbackAttachment.validateSpecs(Arrays.asList(
                        spec("1.png", "image/png", each), spec("2.png", "image/png", each),
                        spec("3.png", "image/png", each)))).getErrorCode());

        assertEquals(ErrorCode.ATTACHMENT_MEDIA_TYPE_REJECTED, assertThrows(SimpleFeedbackServerException.class,
                () -> FeedbackAttachment.validateSpecs(Arrays.asList(
                        spec("a.pdf", "application/pdf", 10)))).getErrorCode());

        assertEquals(ErrorCode.FIELD_TOO_LONG, assertThrows(SimpleFeedbackServerException.class,
                () -> FeedbackAttachment.validateSpecs(Arrays.asList(
                        spec(repeat('f', SimpleFeedbackServerConstant.ATTACHMENT_FILENAME_MAX_LENGTH + 1),
                                "image/png", 10)))).getErrorCode());
        log.info("输出: 数量/单文件/总量/类型/文件名五个维度错误码逐一正确");
    }

    private static FeedbackAttachment.UploadSpec spec(String filename, String contentType, long sizeBytes) {
        return FeedbackAttachment.UploadSpec.builder()
                .filename(filename)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .build();
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
