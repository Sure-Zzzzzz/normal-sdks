package io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.account.response.WebPhoneStatusResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.PhoneNormalizationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 手机号绑定业务：PUT=验证通过整体替换（无绑定写入/有绑定原子覆盖即换绑，撞已占用 409）；
 * DELETE=显式释放（仅作用于已验证绑定——管理员登记的未验证 phone 属资料，本人不可解）。
 * 审计：绑定覆盖带新旧双哈希，解绑带号哈希。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPhoneBindingService {

    private static final String STATUS_REGISTERED = "registered";
    private static final String STATUS_VERIFIED = "verified";
    private static final DateTimeFormatter BOUND_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final IamUserRepository userRepository;
    private final IamAuditEventPublisher auditEventPublisher;
    private final IamPhoneChallengeService challengeService;

    /**
     * 绑定/换绑：验证码消费通过后整体替换 phone+bound_at（同号重验证=覆盖刷新，无害）。
     *
     * @param userId      当前用户
     * @param challengeId 挑战 ID（purpose=bind）
     * @param code        验证码
     * @param rawPhone    新号原始输入（与挑战同号校验）
     */
    @Transactional
    public void bindPhone(Long userId, String challengeId, String code, String rawPhone) {
        String normalized = PhoneNormalizationHelper.normalize(rawPhone);
        if (!challengeService.consumeChallenge(challengeId, code,
                SimpleIamServerConstant.PHONE_CHALLENGE_PURPOSE_BIND, normalized)) {
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED, "验证码错误或已失效");
        }
        IamUserEntity occupiedByPhone = userRepository.findByPhone(normalized).orElse(null);
        if (occupiedByPhone != null && !occupiedByPhone.getId().equals(userId)) {
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED, "该手机号已被使用");
        }
        IamUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new SimpleIamServerException(
                        String.format(ServerErrorMessage.USER_NOT_FOUND, userId)));
        String oldPhoneHash = user.getPhone() == null ? null : challengeService.phoneHash(user.getPhone());
        user.setPhone(normalized);
        user.setPhoneBoundAt(Instant.now());
        userRepository.save(user);
        auditEventPublisher.publishAdminAction(AdminActionType.PHONE_BOUND, AdminSubjectType.USER,
                user.getSubjectId(), user.getUsername(),
                "newPhoneHash=" + challengeService.phoneHash(normalized)
                        + (oldPhoneHash != null ? ",oldPhoneHash=" + oldPhoneHash : ",replace=false"));
        log.info("手机号绑定/换绑: userId={}, phoneHash={}, replace={}",
                userId, challengeService.phoneHash(normalized), oldPhoneHash != null);
    }

    /**
     * 解绑：仅作用于已验证绑定；解绑=phone+bound_at 双置 NULL（号码即释放）。
     *
     * @param userId 当前用户
     */
    @Transactional
    public void unbindPhone(Long userId) {
        IamUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new SimpleIamServerException(
                        String.format(ServerErrorMessage.USER_NOT_FOUND, userId)));
        if (user.getPhoneBoundAt() == null) {
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED,
                    "未验证登记的手机号属资料，本人不可解绑（请联系管理员修改）");
        }
        String releasedHash = challengeService.phoneHash(user.getPhone());
        user.setPhone(null);
        user.setPhoneBoundAt(null);
        userRepository.save(user);
        auditEventPublisher.publishAdminAction(AdminActionType.PHONE_UNBOUND, AdminSubjectType.USER,
                user.getSubjectId(), user.getUsername(), "phoneHash=" + releasedHash);
        log.info("手机号解绑: userId={}, phoneHash={}", userId, releasedHash);
    }

    /**
     * 手机号状态视图（脱敏号+状态+绑定时间）。
     *
     * @param user 用户实体
     * @return 状态视图（未登记返回 null 由调用方表达）
     */
    public WebPhoneStatusResponse statusOf(IamUserEntity user) {
        if (user == null || user.getPhone() == null) {
            return null;
        }
        return WebPhoneStatusResponse.builder()
                .maskedPhone(mask(user.getPhone()))
                .status(user.getPhoneBoundAt() == null ? STATUS_REGISTERED : STATUS_VERIFIED)
                .boundAt(user.getPhoneBoundAt() == null ? null : BOUND_AT_FORMAT.format(user.getPhoneBoundAt()))
                .build();
    }

    /**
     * 脱敏：保留前 3 后 4（国际号保留前 5 后 3）。
     */
    private String mask(String normalizedPhone) {
        String digits = normalizedPhone.startsWith("+") ? normalizedPhone.substring(1) : normalizedPhone;
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "****" + digits.substring(7);
        }
        return "+" + digits.substring(0, Math.min(5, digits.length() - 3))
                + "***" + digits.substring(digits.length() - 3);
    }
}
