package io.github.surezzzzzz.sdk.mail.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.mail.MailApplication;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.constant.MailFlag;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.response.MessageCountResponse;
import io.github.surezzzzzz.sdk.mail.client.MailReader;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.mail.Message;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/10 18:13
 */
@Slf4j
@SpringBootTest(classes = MailApplication.class)
public class ReadMailTest {

    @Autowired
    private MailReader mailReader;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @EnabledIfEnvironmentVariable(named = "run.local.tests", matches = "zs")
    public void smokeTest() throws Exception {
        MessageCountResponse messageCountResponse = mailReader.getInboxMessageCount();
        assertNotNull(messageCountResponse);

        List<Message> messages = mailReader.fetchEmails(1, MailFlag.UNREAD);
        for (Message message : messages) {
            play(message);
        }
        log.info("idMessage");
        Message idMessage = mailReader.fetchInboxEmailByMessageId("<tencent_57652BDCCF8F7D68F55718CD6CA50821E408@qq.com>");
        play(idMessage);
        List<Message> testMails = mailReader.fetchInboxEmailsBySubjectKeyword("测试主题", 2);
        log.info("testMails");
        for (Message message : testMails) {
            play(message);
        }
        log.info("emailsByCustomHeader");
        List<Message> emailsByCustomHeader = mailReader.fetchInboxEmailsByCustomHeaderAndValue("X-my-header", "b", 1, 1);
        for (Message message : emailsByCustomHeader) {
            play(message);
        }
        log.info("X-Schumann-Message-ID");
        List<Message> emailsByCustomSchumannHeader = mailReader.fetchInboxEmailsByCustomHeader("X-Schumann-Message-ID", 1, 1);
        for (Message message : emailsByCustomSchumannHeader) {
            play(message);
        }

    }

    private void play(Message message) throws Exception {
        String content = mailReader.extractContent(message);
        List<String> attachments = mailReader.extractAttachments(message);
        mailReader.markAsRead(message);
        mailReader.markAsRead(message);
        mailReader.markAsUnread(message);
        mailReader.markAsUnread(message);
        log.info("content:{}", content);
        log.info("attachments:{}", attachments);
        List<String> references = mailReader.getReferences(message);
        log.info(mailReader.getMessageId(message));
        log.info(references.toString());
        log.info("In-Reply-To:{}", objectMapper.writeValueAsString(message.getHeader("In-Reply-To")));
        log.info("References:{}", objectMapper.writeValueAsString(message.getHeader("References")));
        log.info("Message-Id:{}", objectMapper.writeValueAsString(message.getHeader("Message-Id")));
        log.info("X-my-header:{}", objectMapper.writeValueAsString(message.getHeader("X-my-header")));
        log.info("X-Schumann-Message-ID:{}", objectMapper.writeValueAsString(message.getHeader("X-Schumann-Message-ID")));
        mailReader.markAsUnread(message);
    }

}
