package io.github.surezzzzzz.sdk.mail.test.cases;

import io.github.surezzzzzz.sdk.mail.configuration.MailProperties;
import io.github.surezzzzzz.sdk.mail.engine.MailReadEngine;
import io.github.surezzzzzz.sdk.mail.engine.MailSendEngine;
import io.github.surezzzzzz.sdk.mail.model.request.MailPageRequest;
import io.github.surezzzzzz.sdk.mail.model.request.MailSearchRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailPageResult;
import io.github.surezzzzzz.sdk.mail.model.result.MailReadResult;
import io.github.surezzzzzz.sdk.mail.model.result.MailSendResult;
import io.github.surezzzzzz.sdk.mail.test.MailClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mail 本地端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = MailClientTestApplication.class)
class MailLocalSendTest {

    private static final int READ_RETRY_TIMES = 6;
    private static final long READ_RETRY_INTERVAL_MILLIS = 5000L;
    private static final int READ_PAGE_SIZE = 5;

    @Autowired
    private MailSendEngine mailSendEngine;
    @Autowired
    private MailReadEngine mailReadEngine;
    @Autowired
    private MailProperties mailProperties;

    @Test
    @DisplayName("测试本地 SMTP 发送并从收件箱读回")
    void testLocalSmtpSendAndRead() throws Exception {
        String to = mailProperties.getSend().getNormal().getUsername();
        String uniqueToken = String.valueOf(System.currentTimeMillis());
        String subject = "mail-client-starter 2.0.0 端到端测试邮件-" + uniqueToken;
        String content = "这是一封 mail-client-starter 2.0.0 本地端到端测试邮件。" + uniqueToken;

        MailSendResult sendResult = mailSendEngine.send(subject, content, to);
        MailReadResult readResult = waitForMail(subject);

        log.info("发送目标数量: {}", sendResult.getToCount());
        log.info("发送 Message-ID: {}", sendResult.getMessageId());
        log.info("读取 Message-ID: {}", readResult == null ? null : readResult.getMessageId());
        assertTrue(sendResult.isSuccess(), "邮件发送结果应为成功");
        assertEquals(1, sendResult.getToCount(), "收件人数应为 1");
        assertNotNull(sendResult.getMessageId(), "发送 Message-ID 不应为空");
        assertNotNull(readResult, "应能从收件箱读回刚发送的邮件");
        assertEquals(subject, readResult.getSubject(), "读回邮件主题应一致");
        assertNotNull(readResult.getContent(), "读回邮件内容不应为空");
        assertTrue(readResult.getContent().getText().contains(content), "读回邮件正文应包含发送内容");
    }

    private MailReadResult waitForMail(String subject) throws Exception {
        for (int i = 0; i < READ_RETRY_TIMES; i++) {
            MailPageResult pageResult = mailReadEngine.search(MailSearchRequest.builder()
                    .subjectKeyword(subject)
                    .page(MailPageRequest.builder().pageNo(1).pageSize(READ_PAGE_SIZE).build())
                    .build());
            log.info("第 {} 次读取，扫描数量: {}，匹配数量: {}", i + 1, pageResult.getScanned(), pageResult.getMatched());
            if (pageResult.getRecords() != null && !pageResult.getRecords().isEmpty()) {
                return pageResult.getRecords().get(0);
            }
            Thread.sleep(READ_RETRY_INTERVAL_MILLIS);
        }
        return null;
    }
}
