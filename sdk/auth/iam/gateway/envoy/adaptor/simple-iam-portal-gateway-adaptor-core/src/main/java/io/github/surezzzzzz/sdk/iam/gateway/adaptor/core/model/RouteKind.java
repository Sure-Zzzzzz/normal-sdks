package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Getter;

/**
 * 可信应用公开路由类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum RouteKind {
    /**
     * 页面、静态资源和页面入口。
     */
    PAGE("page"),
    /**
     * 业务 API。
     */
    API("api");

    private final String code;

    RouteKind(String code) {
        this.code = code;
    }
}
