package io.github.surezzzzzz.sdk.mail.test.cases;

import io.github.surezzzzzz.sdk.mail.configuration.MailProperties;
import io.github.surezzzzzz.sdk.mail.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mail.constant.MailContentType;
import io.github.surezzzzzz.sdk.mail.constant.MailProviderType;
import io.github.surezzzzzz.sdk.mail.engine.DefaultMailSendEngine;
import io.github.surezzzzzz.sdk.mail.exception.MailSendException;
import io.github.surezzzzzz.sdk.mail.model.request.MailSendRequest;
import io.github.surezzzzzz.sdk.mail.model.result.MailSendResult;
import io.github.surezzzzzz.sdk.mail.model.value.MailContent;
import io.github.surezzzzzz.sdk.mail.provider.MailSenderProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认 Mail 发送引擎测试
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultMailSendEngineTest {

    @Test
    @DisplayName("测试默认 Provider 发送")
    void testSendWithDefaultProvider() {
        MailProperties properties = new MailProperties();
        properties.getSend().setDefaultProvider(MailProviderType.NORMAL.getCode());
        DefaultMailSendEngine engine = new DefaultMailSendEngine(Collections.singletonList(new StubProvider()), properties);

        MailSendResult result = engine.send("test subject", "test content", "to@example.test");

        log.info("发送结果 provider: {}", result.getProvider());
        assertTrue(result.isSuccess(), "发送结果应为成功");
        assertEquals(MailProviderType.NORMAL.getCode(), result.getProvider(), "应使用默认 Provider");
        assertEquals(1, result.getToCount(), "收件人数量应正确");
    }

    @Test
    @DisplayName("测试 Provider 不存在抛异常")
    void testProviderNotFound() {
        MailProperties properties = new MailProperties();
        DefaultMailSendEngine engine = new DefaultMailSendEngine(Collections.<MailSenderProvider>emptyList(), properties);
        MailSendRequest request = MailSendRequest.builder()
                .to(Arrays.asList("to@example.test"))
                .subject("test subject")
                .content(MailContent.builder().type(MailContentType.TEXT).text("test content").build())
                .build();

        MailSendException exception = assertThrows(MailSendException.class, () -> engine.send(request), "Provider 不存在应抛异常");

        log.info("异常错误码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.PROVIDER_NOT_SUPPORTED, exception.getErrorCode(), "错误码应为 Provider 不支持");
    }

    private static class StubProvider implements MailSenderProvider {

        @Override
        public MailProviderType providerType() {
            return MailProviderType.NORMAL;
        }

        @Override
        public boolean supports(MailSendRequest request) {
            return true;
        }

        @Override
        public MailSendResult send(MailSendRequest request) {
            return MailSendResult.builder()
                    .success(true)
                    .provider(providerType().getCode())
                    .toCount(request.getTo().size())
                    .build();
        }
    }
}
