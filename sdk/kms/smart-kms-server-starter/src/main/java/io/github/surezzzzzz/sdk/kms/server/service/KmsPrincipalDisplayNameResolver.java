package io.github.surezzzzzz.sdk.kms.server.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 可选的主体显示名解析端口，供宿主将稳定主体标识映射为人员或凭证的可读名称。
 *
 * <p>KMS 不反查身份目录、不自行解析未验证声明；默认实现始终返回未解析，
 * 响应中的显示名字段为空，由前端回退展示原始主体标识。</p>
 *
 * @author surezzzzzz
 */
public interface KmsPrincipalDisplayNameResolver {

    /**
     * 解析单个主体的显示名。
     *
     * @param principalId 稳定主体标识
     * @return 可读显示名；无法解析时返回 null，不抛出异常
     */
    String resolveDisplayName(String principalId);

    /**
     * 批量解析主体显示名；默认逐个调用 {@link #resolveDisplayName(String)}。
     *
     * @param principalIds 稳定主体标识集合
     * @return 仅包含已解析主体的显示名映射；空集合返回空映射
     */
    default Map<String, String> resolveDisplayNames(Collection<String> principalIds) {
        Map<String, String> result = new HashMap<String, String>();
        if (principalIds == null) {
            return result;
        }
        for (String principalId : principalIds) {
            String displayName = resolveDisplayName(principalId);
            if (displayName != null && !displayName.isEmpty()) {
                result.put(principalId, displayName);
            }
        }
        return result;
    }
}
