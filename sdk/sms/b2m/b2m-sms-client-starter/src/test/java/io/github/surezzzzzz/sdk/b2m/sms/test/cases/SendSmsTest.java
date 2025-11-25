package io.github.surezzzzzz.sdk.b2m.sms.test.cases;

import io.github.surezzzzzz.sdk.b2m.sms.client.SmsClient;
import io.github.surezzzzzz.sdk.b2m.sms.test.SmsTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/11 9:26
 */
@SpringBootTest(classes = SmsTestApplication.class)
public class SendSmsTest {

    @Autowired
    private SmsClient smsClient;

    @Test
    public void smokeTest() throws Exception {
        smsClient.sendSingleSms("17710290654", "你好");
        Thread.sleep(61000);
    }
}
