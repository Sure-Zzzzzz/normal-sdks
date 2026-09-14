package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalThemePreferenceRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalThemePreferenceResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamThemePreferenceService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * IAM Web 当前用户主题偏好 API。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/web/theme-preference")
@RequiredArgsConstructor
public class IamThemePreferenceRestController {

    private final IamThemePreferenceService iamThemePreferenceService;

    /**
     * 用户主题偏好
     */
    @GetMapping
    public ResponseEntity<PortalThemePreferenceResponse> getThemePreference(
            @AuthenticationPrincipal IamUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(iamThemePreferenceService.getPreference(principal.getUserId()));
    }

    /**
     * 保存用户主题偏好
     */
    @PutMapping
    public ResponseEntity<PortalThemePreferenceResponse> saveThemePreference(
            @AuthenticationPrincipal IamUserDetails principal,
            @RequestBody PortalThemePreferenceRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(iamThemePreferenceService.savePreference(principal.getUserId(), request));
    }
}
