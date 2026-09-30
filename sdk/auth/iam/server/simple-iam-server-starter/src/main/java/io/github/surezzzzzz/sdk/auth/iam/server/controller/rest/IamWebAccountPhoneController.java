package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.account.request.WebPhoneBindChallengeRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.account.request.WebPhoneBindRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.account.response.WebPhoneStatusResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response.WebPhoneChallengeResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamPhoneBindingService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamPhoneChallengeService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamUserDetails;
import io.github.surezzzzzz.sdk.auth.iam.server.support.PhoneNormalizationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 账户安全手机号端点（登录态+CSRF 由既有安全链保障）：
 * 挑战创建/绑定整体替换（PUT，与改密=PUT /auth/password 同构）/解绑（DELETE）。
 * RESTful 资源化，状态只由 HTTP status 表达。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/iam/web/account")
public class IamWebAccountPhoneController {

    private final IamPhoneChallengeService challengeService;
    private final IamPhoneBindingService bindingService;
    private final IamUserService userService;

    /**
     * 当前账号手机号状态（脱敏号+状态+绑定时间；未登记时 body 为 null；短信能力未装配 404）。
     */
    @GetMapping("/phone")
    public ResponseEntity<WebPhoneStatusResponse> phoneStatus(
            @AuthenticationPrincipal IamUserDetails userDetails) {
        requireDeliveryAvailable();
        return ResponseEntity.ok(bindingService.statusOf(
                userService.getById(userDetails.getUserId())));
    }

    /**
     * 创建绑定挑战（验证新号；与登录挑战同状态机，purpose=bind）。
     *
     * @param request        新号请求
     * @param userDetails    当前用户
     * @param servletRequest 请求
     * @return 201+challengeId
     */
    @PostMapping("/phone-challenges")
    public ResponseEntity<WebPhoneChallengeResponse> createBindChallenge(
            @RequestBody WebPhoneBindChallengeRequest request,
            @AuthenticationPrincipal IamUserDetails userDetails,
            HttpServletRequest servletRequest) {
        requireDeliveryAvailable();
        String phone = PhoneNormalizationHelper.normalize(request.getPhone());
        String challengeId = challengeService.createChallenge(phone,
                SimpleIamServerConstant.PHONE_CHALLENGE_PURPOSE_BIND,
                servletRequest.getRemoteAddr(), true);
        return ResponseEntity.status(201).body(new WebPhoneChallengeResponse(challengeId, challengeService.sendCooldownSeconds()));
    }

    /**
     * 绑定/换绑：验证通过整体替换（无绑定写入/有绑定原子覆盖；撞已占用 409）。
     *
     * @param request        挑战+验证码
     * @param userDetails    当前用户
     * @param servletRequest 请求（新号原始输入从挑战侧校验同号）
     * @return 204
     */
    @PutMapping("/phone")
    public ResponseEntity<Void> bindPhone(@RequestBody WebPhoneBindRequest request,
                                          @AuthenticationPrincipal IamUserDetails userDetails) {
        bindingService.bindPhone(userDetails.getUserId(), request.getChallengeId(), request.getCode(),
                request.getPhone());
        return ResponseEntity.noContent().build();
    }

    /**
     * 解绑：显式释放号码（仅作用于已验证绑定）。
     *
     * @param userDetails 当前用户
     * @return 204
     */
    @DeleteMapping("/phone")
    public ResponseEntity<Void> unbindPhone(@AuthenticationPrincipal IamUserDetails userDetails) {
        bindingService.unbindPhone(userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    private void requireDeliveryAvailable() {
        if (!challengeService.deliveryAvailable()) {
            throw new SimpleIamServerException(
                    io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.VALIDATION_FAILED,
                    "短信能力未装配");
        }
    }

}
