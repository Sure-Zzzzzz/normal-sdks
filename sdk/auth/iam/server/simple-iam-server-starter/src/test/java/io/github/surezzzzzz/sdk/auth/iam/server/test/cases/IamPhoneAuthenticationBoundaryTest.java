package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaProvider;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.controller.rest.IamAuthRestController;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request.WebPhoneLoginRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.message.IamMessageSseService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.*;
import io.github.surezzzzzz.sdk.auth.iam.server.support.PhoneNormalizationHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.support.ProviderDisplayHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 手机认证边界：登记资料不能替代已验证登录因子，验证码必须绑定首次发送号码。
 *
 * @author surezzzzzz
 */
class IamPhoneAuthenticationBoundaryTest {

    @Test
    @DisplayName("绑定挑战必须校验发送手机号，不能用 A 号验证码绑定 B 号")
    void bindPhoneMustPassNormalizedPhoneToChallengeConsumer() {
        IamPhoneChallengeService challengeService = mock(IamPhoneChallengeService.class);
        IamPhoneBindingService service = new IamPhoneBindingService(mock(IamUserRepository.class),
                mock(IamAuditEventPublisher.class), challengeService);
        String phone = "13800138000";

        when(challengeService.consumeChallenge(eq("challenge-a"), eq("123456"),
                eq(SimpleIamServerConstant.PHONE_CHALLENGE_PURPOSE_BIND), anyString())).thenReturn(false);

        assertThrows(SimpleIamServerException.class,
                () -> service.bindPhone(1L, "challenge-a", "123456", phone));

        verify(challengeService).consumeChallenge("challenge-a", "123456",
                SimpleIamServerConstant.PHONE_CHALLENGE_PURPOSE_BIND, PhoneNormalizationHelper.normalize(phone));
    }

    @Test
    @DisplayName("仅管理员登记且未验证的手机号不得完成短信登录")
    void phoneLoginMustRejectRegisteredButUnverifiedPhone() {
        IamPhoneChallengeService challengeService = mock(IamPhoneChallengeService.class);
        IamUserRepository userRepository = mock(IamUserRepository.class);
        IamUserService userService = mock(IamUserService.class);
        IamUserEntity user = new IamUserEntity();
        user.setId(1L);
        user.setPhone("+8613800138000");
        // phoneBoundAt 保持 null，模拟管理员登记的资料号码。
        when(challengeService.consumeChallenge("challenge-a", "123456",
                SimpleIamServerConstant.PHONE_CHALLENGE_PURPOSE_LOGIN, "+8613800138000")).thenReturn(true);
        when(userRepository.findByPhone("+8613800138000")).thenReturn(Optional.of(user));

        IamAuthRestController controller = new IamAuthRestController(
                mock(IamAuthenticationService.class),
                new io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamPasswordMaxAgeSupport(new SimpleIamServerProperties()),
                mock(IamExternalLoginService.class),
                IamExternalProviderRegistry.create(Collections.emptyList(), Collections.emptyList()),
                mock(ProviderDisplayHelper.class),
                mock(IamUserDetailsService.class), userService, mock(IamMessageSseService.class),
                mock(IamSessionService.class), mock(IamCaptchaVerificationService.class), objectProvider(),
                mock(SimpleIamServerProperties.class), mock(CsrfTokenRepository.class),
                mock(IamAuditEventPublisher.class), challengeService, userRepository,
                mock(IamLoginFailurePolicyService.class));
        WebPhoneLoginRequest request = new WebPhoneLoginRequest();
        request.setPhone("13800138000");
        request.setChallengeId("challenge-a");
        request.setCode("123456");

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> controller.phoneLogin(request, new MockHttpServletRequest()));

        assertEquals("验证码错误或已失效", exception.getMessage());
        verify(userService, never()).getById(1L);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<CaptchaProvider> objectProvider() {
        return (ObjectProvider<CaptchaProvider>) mock(ObjectProvider.class);
    }
}
