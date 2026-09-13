package io.github.surezzzzzz.sdk.auth.iam.server.constant;

/**
 * Portal PAGE 的宿主展示模式。
 *
 * <p>IMMERSIVE 仅移除 Portal 壳的顶栏与侧栏，不调用浏览器 Fullscreen API，
 * 不改变 IAM 会话、路由或子应用权限边界。
 *
 * @author surezzzzzz
 */
public enum PortalPresentationMode {

    /**
     * 当前标准 Portal 壳布局。
     */
    STANDARD,

    /**
     * 无 Portal 顶栏、无侧栏的沉浸展示布局。
     */
    IMMERSIVE
}
