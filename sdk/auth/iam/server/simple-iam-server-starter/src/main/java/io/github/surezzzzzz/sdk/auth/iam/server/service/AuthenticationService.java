package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务（本地账号密码验证）
 *
 * <p>只负责本地密码链路；外部身份源登录由 ExternalLoginService 承担。
 * 禁用/锁定检查与失败计数策略委托 LoginFailurePolicySupport。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class AuthenticationService {

    private final IamUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginFailurePolicySupport failurePolicySupport;

    /**
     * 验证用户名 + 密码，通过则清零失败计数并返回用户实体
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 通过认证的用户实体
     */
    @Transactional
    public IamUserEntity authenticate(String username, String password) {
        IamUserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    // 爆破不存在的用户名同样计数，渐进触发人机验证
                    long failureCount = failurePolicySupport.recordLocalFailureForUnknownUsername(username);
                    return badCredentialsException(failureCount);
                });

        // 外部身份源账号无本地密码，禁止走本地密码登录（计入本地密码维度失败计数）
        if (user.getIdentitySource() != null) {
            throw badCredentialsException(failurePolicySupport.recordLocalFailure(user));
        }

        failurePolicySupport.assertLocalLoginAllowed(user);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw badCredentialsException(failurePolicySupport.recordLocalFailure(user));
        }

        failurePolicySupport.recordLocalSuccess(user);
        return user;
    }

    /**
     * 凭据失败响应：未达锁定上限时文案携带剩余尝试次数，达上限（当次已锁）用锁定文案。
     */
    private SimpleIamServerException badCredentialsException(long failureCount) {
        int remaining = failurePolicySupport.remainingAttempts(failureCount);
        if (remaining <= 0) {
            return new SimpleIamServerException(ErrorCode.BAD_CREDENTIALS,
                    ServerErrorMessage.ACCOUNT_LOCKED);
        }
        return new SimpleIamServerException(ErrorCode.BAD_CREDENTIALS,
                String.format(ServerErrorMessage.BAD_CREDENTIALS_REMAINING, remaining));
    }
}
