package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.branding.response.WebBrandingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM Web 品牌信息 API（公开，登录前可读，供前端统一渲染品牌名/Logo/自定义资源）
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/web/branding")
@RequiredArgsConstructor
public class IamBrandingRestController {

    private final SimpleIamServerProperties properties;

    /**
     * 品牌配置（匿名，登录页 / Portal 渲染用）
     */
    @GetMapping
    public ResponseEntity<WebBrandingResponse> branding() {
        SimpleIamServerProperties.ThemeConfig theme = properties.getAdmin().getTheme();
        return ResponseEntity.ok(new WebBrandingResponse(
                theme.getBrandName(),
                theme.getLogoUrl(),
                theme.getCustomCssUrl(),
                theme.getCustomJsUrl()));
    }
}
