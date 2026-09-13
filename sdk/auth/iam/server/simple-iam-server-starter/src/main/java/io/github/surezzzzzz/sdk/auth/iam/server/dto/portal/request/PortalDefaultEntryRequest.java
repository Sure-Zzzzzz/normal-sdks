package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

/**
 * 应用 Portal 默认入口。
 */
@Data
public class PortalDefaultEntryRequest {

    /**
     * 同应用 PAGE 菜单 code。
     */
    private String pageMenuCode;

    /**
     * 相对应用 routePrefix 的静态 history 路由。
     */
    private String entryPath;
}
