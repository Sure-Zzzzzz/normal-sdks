package io.github.surezzzzzz.sdk.auth.aksk.client.core.provider;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 静态的 SecurityContextProvider 实现
 *
 * <p>返回固定的安全上下文，适用于测试或简单场景
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class StaticSecurityContextProvider implements SecurityContextProvider {

    /**
     * 固定的安全上下文
     */
    private String securityContext;

    @Override
    public String getSecurityContext() {
        return securityContext;
    }
}
