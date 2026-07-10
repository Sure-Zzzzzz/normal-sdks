package io.github.surezzzzzz.sdk.mail.test.cases;

import io.github.surezzzzzz.sdk.mail.constant.MailContentType;
import io.github.surezzzzzz.sdk.mail.model.result.MailReadResult;
import io.github.surezzzzzz.sdk.mail.parser.DefaultMailMessageParser;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 默认 Mail 消息解析器测试
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultMailMessageParserTest {

    private final DefaultMailMessageParser parser = new DefaultMailMessageParser();

    @Test
    @DisplayName("测试文本邮件解析")
    void testParseTextMessage() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom("from@example.test");
        message.setRecipients(MimeMessage.RecipientType.TO, "to@example.test");
        message.setSubject("test subject");
        message.setText("test content");
        message.setSentDate(new Date());
        message.saveChanges();

        MailReadResult result = parser.parse(message);

        log.info("解析主题: {}", result.getSubject());
        log.info("解析内容类型: {}", result.getContent().getType());
        assertNotNull(result.getMessageId(), "Message-ID 不应为空");
        assertEquals("test subject", result.getSubject(), "主题应解析正确");
        assertEquals("from@example.test", result.getFrom(), "发件人应解析正确");
        assertEquals(1, result.getTo().size(), "收件人数量应解析正确");
        assertEquals("to@example.test", result.getTo().get(0), "收件人应解析正确");
        assertNotNull(result.getContent(), "内容不应为空");
        assertEquals(MailContentType.TEXT, result.getContent().getType(), "内容类型应为 TEXT");
        assertEquals("test content", result.getContent().getText(), "正文应解析正确");
        assertFalse(result.getHeaders().isEmpty(), "Header 不应为空");
    }
}
