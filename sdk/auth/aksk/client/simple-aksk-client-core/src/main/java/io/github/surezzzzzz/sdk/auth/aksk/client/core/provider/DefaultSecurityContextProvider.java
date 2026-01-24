package io.github.surezzzzzz.sdk.auth.aksk.client.core.provider;

/**
 * 默认的 SecurityContextProvider 实现
 * <p>
 * 返回 null，适用于平台级 AKSK（不需要用户上下文）
 *
 * @author surezzzzzz
 */
public class DefaultSecurityContextProvider implements SecurityContextProvider {

    @Override
    public String getSecurityContext() {
        return null;
    }
}
