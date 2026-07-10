package io.github.surezzzzzz.sdk.mail.test.cases;

import io.github.surezzzzzz.sdk.mail.builder.MailMessageBuilder;
import io.github.surezzzzzz.sdk.mail.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mail.constant.MailContentType;
import io.github.surezzzzzz.sdk.mail.exception.MailValidationException;
import io.github.surezzzzzz.sdk.mail.model.request.MailSendRequest;
import io.github.surezzzzzz.sdk.mail.model.value.MailContent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mail 消息构建器测试
 *
 * @author surezzzzzz
 */
@Slf4j
class MailMessageBuilderTest {

    private final MailMessageBuilder builder = new MailMessageBuilder();

    @Test
    @DisplayName("测试 MIME 消息构建")
    void testBuildMimeMessage() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        MailSendRequest request = MailSendRequest.builder()
                .to(Arrays.asList("to@example.test"))
                .cc(Arrays.asList("cc@example.test"))
                .bcc(Arrays.asList("bcc@example.test"))
                .subject("test subject")
                .content(MailContent.builder().type(MailContentType.TEXT).text("test content").build())
                .build();

        builder.build(message, request, "from@example.test");

        log.info("消息主题: {}", message.getSubject());
        log.info("收件人数: {}", message.getRecipients(Message.RecipientType.TO).length);
        assertEquals("test subject", message.getSubject(), "消息主题应正确");
        assertEquals("from@example.test", message.getFrom()[0].toString(), "发件人应正确");
        Object content = message.getContent();
        assertTrue(content instanceof Multipart, "正文应为 Multipart");
        assertEquals("test content", extractText((Multipart) content), "正文应正确");
        assertEquals(1, message.getRecipients(Message.RecipientType.TO).length, "收件人数量应正确");
        assertEquals("to@example.test", message.getRecipients(Message.RecipientType.TO)[0].toString(), "收件人应正确");
        assertEquals(1, message.getRecipients(Message.RecipientType.CC).length, "抄送人数量应正确");
        assertEquals("cc@example.test", message.getRecipients(Message.RecipientType.CC)[0].toString(), "抄送人应正确");
        assertEquals(1, message.getRecipients(Message.RecipientType.BCC).length, "密送人数量应正确");
        assertEquals("bcc@example.test", message.getRecipients(Message.RecipientType.BCC)[0].toString(), "密送人应正确");
        assertNotNull(message.getContentType(), "内容类型不应为空");
    }

    private String extractText(Multipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            Object content = bodyPart.getContent();
            if (content instanceof String) {
                return (String) content;
            }
            if (content instanceof Multipart) {
                String text = extractText((Multipart) content);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("测试缺少收件人抛校验异常")
    void testValidateMissingRecipient() {
        MailSendRequest request = MailSendRequest.builder()
                .subject("test subject")
                .content(MailContent.builder().type(MailContentType.TEXT).text("test content").build())
                .build();

        MailValidationException exception = assertThrows(MailValidationException.class, () -> builder.validate(request), "缺少收件人应抛校验异常");

        log.info("异常错误码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode(), "错误码应为参数校验错误");
    }
}
