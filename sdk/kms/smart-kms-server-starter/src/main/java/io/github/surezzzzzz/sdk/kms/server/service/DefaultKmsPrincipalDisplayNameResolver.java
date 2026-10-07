package io.github.surezzzzzz.sdk.kms.server.service;

/**
 * 默认主体显示名解析器：不接入任何身份目录，始终返回未解析。
 *
 * @author surezzzzzz
 */
public class DefaultKmsPrincipalDisplayNameResolver implements KmsPrincipalDisplayNameResolver {

    /**
     * 创建默认未解析实现。
     */
    public DefaultKmsPrincipalDisplayNameResolver() {
    }

    /**
     * 始终返回未解析，由响应层输出空显示名。
     */
    @Override
    public String resolveDisplayName(String principalId) {
        return null;
    }
}
