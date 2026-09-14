package io.github.surezzzzzz.sdk.auth.iam.server.constant;

import lombok.Getter;

/**
 * Portal 菜单节点类型。
 *
 * <p>GROUP 只承载分组，PAGE 才是可路由的叶子，禁止混合两种语义。
 *
 * @author surezzzzzz
 */
@Getter
public enum PortalMenuNodeType {

    /**
     * 分组节点
     */
    GROUP,

    /**
     * 页面节点
     */
    PAGE
}
