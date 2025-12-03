package io.github.surezzzzzz.sdk.mail.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.mail.MailApplication;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.SendMimeMailMessageRequest;
import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request.SendSimpleMailMessageRequest;
import io.github.surezzzzzz.sdk.mail.client.NormalMailSender;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import javax.mail.internet.MimeMessage;
import java.util.HashMap;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/8/16 14:37
 */
@Slf4j
@SpringBootTest(classes = MailApplication.class)
public class SendMailTest {

    @Autowired
    private NormalMailSender normalMailSender;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @EnabledIfEnvironmentVariable(named = "run.local.tests", matches = "zs")
    public void smokeTest() throws Exception {

        SendSimpleMailMessageRequest sendSimpleMailMessageRequest = new SendSimpleMailMessageRequest();
        sendSimpleMailMessageRequest.setTo("xxx@qq.com");
        sendSimpleMailMessageRequest.setSubject("测试主题");
        sendSimpleMailMessageRequest.setText("测试内容");
        normalMailSender.sendSimpleMail(sendSimpleMailMessageRequest);


        SendMimeMailMessageRequest sendMimeMailMessageRequest = SendMimeMailMessageRequest.builder()
                .filePaths(new String[]{new ClassPathResource("application.yaml").getFile().getAbsolutePath()})
                .to(new String[]{"xxx@qq.com"})
                .subject("测试主题")
                .text("测试内容")
                .headers(new HashMap<>())
                .build();
        sendMimeMailMessageRequest.addHeader("X-my-header", "b");
        MimeMessage message = (MimeMessage) normalMailSender.sendMimeMail(sendMimeMailMessageRequest);
        log.info(message.getMessageID());

    }
}
