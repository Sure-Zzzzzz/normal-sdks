package io.github.surezzzzzz.sdk.mail.test.cases;

import io.github.surezzzzzz.sdk.mail.support.MailHeaderHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mail Header Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
class MailHeaderHelperTest {

    @Test
    @DisplayName("测试 Header 缺失返回 null")
    void testFirstHeaderMissing() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));

        String value = MailHeaderHelper.firstHeader(message, "X-Test-Header");

        log.info("缺失 Header 结果: {}", value);
        assertNull(value, "缺失 Header 应返回 null");
    }

    @Test
    @DisplayName("测试 References 拆分")
    void testReferences() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setHeader("References", "<a@example.test> <b@example.test>");

        List<String> references = MailHeaderHelper.references(message);

        log.info("References 数量: {}", references.size());
        assertEquals(2, references.size(), "References 应按空格拆分");
        assertEquals("<a@example.test>", references.get(0), "第一个 References 应正确");
    }
}
