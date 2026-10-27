package io.github.surezzzzzz.sdk.ticket.server.adaptor.test.cases;

import io.github.surezzzzzz.sdk.iam.client.IamMessageClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamMessageSendRequest;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import io.github.surezzzzzz.sdk.ticket.server.adaptor.configuration.SimpleTicketIamAdaptorAutoConfiguration;
import io.github.surezzzzzz.sdk.ticket.server.adaptor.notification.IamInboxNotificationProvider;
import io.github.surezzzzzz.sdk.ticket.server.adaptor.profile.IamUserProfileProvider;
import io.github.surezzzzzz.sdk.ticket.server.spi.NotificationProvider;
import io.github.surezzzzzz.sdk.ticket.server.spi.SubmitterProfileProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IAM 适配装配、错误透传与出站请求契约测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class TicketIamAdaptorTest {

    @Test
    void registersBothProvidersWhenClientsExist() {
        IamMessageClient messageClient = mock(IamMessageClient.class);
        IamUserClient userClient = mock(IamUserClient.class);
        new ApplicationContextRunner()
                .withBean(IamMessageClient.class, () -> messageClient)
                .withBean(IamUserClient.class, () -> userClient)
                .withUserConfiguration(SimpleTicketIamAdaptorAutoConfiguration.class)
                .run(context -> {
                    log.info("IAM 客户端齐备，检查工单两个 SPI 的唯一装配");
                    assertThat(context).hasSingleBean(NotificationProvider.class);
                    assertThat(context).hasSingleBean(SubmitterProfileProvider.class);
                });
    }

    @Test
    void failsStartupWhenRequiredClientIsMissing() {
        new ApplicationContextRunner()
                .withBean(IamMessageClient.class, () -> mock(IamMessageClient.class))
                .withUserConfiguration(SimpleTicketIamAdaptorAutoConfiguration.class)
                .run(context -> {
                    log.info("缺少用户客户端，检查引用即装配的启动失败边界");
                    assertThat(context.getStartupFailure()).isNotNull();
                });
    }

    @Test
    void resolvesNameAndPropagatesUpstreamFailure() {
        IamUserClient userClient = mock(IamUserClient.class);
        IamUserProfileProvider provider = new IamUserProfileProvider(userClient);
        when(userClient.getUser("subject-1"))
                .thenReturn(IamUser.builder().displayName(" 用户一 ").build());
        log.info("用户资料存在，检查显示名规范化");
        assertThat(provider.displayNameOf("subject-1")).isEqualTo("用户一");

        when(userClient.getUser("subject-2"))
                .thenReturn(IamUser.builder().displayName(" ").build());
        log.info("用户资料缺显示名，检查主体标识回落");
        assertThat(provider.displayNameOf("subject-2")).isEqualTo("subject-2");

        RuntimeException upstreamFailure = new RuntimeException("upstream failed");
        when(userClient.getUser("subject-3")).thenThrow(upstreamFailure);
        log.info("IAM 上游失败，检查错误不被伪装成正常资料");
        assertThatThrownBy(() -> provider.displayNameOf("subject-3"))
                .isSameAs(upstreamFailure);
    }

    @Test
    void forwardsNotificationRecipientsAndContent() {
        IamMessageClient messageClient = mock(IamMessageClient.class);
        IamInboxNotificationProvider provider = new IamInboxNotificationProvider(messageClient);
        provider.notify(Arrays.asList("subject-1", "subject-2"), "工单状态", "已受理");
        ArgumentCaptor<IamMessageSendRequest> request = ArgumentCaptor.forClass(IamMessageSendRequest.class);
        verify(messageClient).sendMessage(request.capture());
        log.info("检查通知接收人和内容由适配器原样转交 IAM 客户端");
        assertThat(request.getValue().getRecipientSubjectIds())
                .containsExactly("subject-1", "subject-2");
        assertThat(request.getValue().getTitle()).isEqualTo("工单状态");
        assertThat(request.getValue().getContent()).isEqualTo("已受理");
    }
}
