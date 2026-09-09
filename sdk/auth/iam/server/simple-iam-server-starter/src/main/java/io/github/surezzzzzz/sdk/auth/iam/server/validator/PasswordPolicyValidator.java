package io.github.surezzzzzz.sdk.auth.iam.server.validator;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import lombok.RequiredArgsConstructor;

/**
 * 密码策略校验器
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class PasswordPolicyValidator {

    private final SimpleIamServerProperties properties;

    /**
     * 校验密码是否符合策略，不符合抛异常
     *
     * @param password 明文密码
     */
    public void validate(String password) {
        SimpleIamServerProperties.PasswordConfig cfg = properties.getPassword();

        if (password == null || password.length() < cfg.getMinLength()) {
            throw new SimpleIamServerException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    String.format(ServerErrorMessage.PASSWORD_TOO_SHORT, cfg.getMinLength()));
        }
        if (password.length() > cfg.getMaxLength()) {
            throw new SimpleIamServerException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    String.format(ServerErrorMessage.PASSWORD_TOO_LONG, cfg.getMaxLength()));
        }
        if (Boolean.TRUE.equals(cfg.getRequireUppercase()) && !password.chars().anyMatch(Character::isUpperCase)) {
            throw new SimpleIamServerException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    ServerErrorMessage.PASSWORD_UPPERCASE_REQUIRED);
        }
        if (Boolean.TRUE.equals(cfg.getRequireLowercase()) && !password.chars().anyMatch(Character::isLowerCase)) {
            throw new SimpleIamServerException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    ServerErrorMessage.PASSWORD_LOWERCASE_REQUIRED);
        }
        if (Boolean.TRUE.equals(cfg.getRequireDigit()) && !password.chars().anyMatch(Character::isDigit)) {
            throw new SimpleIamServerException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    ServerErrorMessage.PASSWORD_DIGIT_REQUIRED);
        }
        if (Boolean.TRUE.equals(cfg.getRequireSpecial())
                && password.chars().allMatch(c -> Character.isLetterOrDigit(c))) {
            throw new SimpleIamServerException(ErrorCode.PASSWORD_POLICY_VIOLATION,
                    ServerErrorMessage.PASSWORD_SPECIAL_REQUIRED);
        }
    }
}
