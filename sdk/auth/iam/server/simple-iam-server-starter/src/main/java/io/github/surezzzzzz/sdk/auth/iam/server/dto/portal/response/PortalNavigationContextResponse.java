package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 当前用户权限裁剪后的 Portal 导航上下文。
 */
@Getter
@Builder
public class PortalNavigationContextResponse {

    private final List<PortalAccessibleApplication> applications;
    private final String loginLandingApplicationCode;
}
