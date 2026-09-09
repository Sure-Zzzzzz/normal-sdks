package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * 可信应用重定向URI校验 Helper
 *
 * <p>规则：
 * <ul>
 *   <li>必须是非空绝对 URI（含 scheme 与 host）</li>
 *   <li>禁止通配符（含 {@code *}），避免开放重定向风险</li>
 *   <li>同一可信应用内去重，保留顺序</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
public class RedirectUriHelper {

    /**
     * 通配符字符
     */
    private static final String WILDCARD = "*";

    /**
     * 校验并归一化 redirect_uri 列表
     *
     * @param redirectUris 原始列表
     * @return 去重后的合法列表
     */
    public List<String> normalizeAndValidate(List<String> redirectUris) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : redirectUris) {
            if (raw == null || raw.trim().isEmpty()) {
                throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_REDIRECT_URI_INVALID,
                        String.format(ServerErrorMessage.TRUSTED_APPLICATION_REDIRECT_URI_INVALID, raw));
            }
            String trimmed = raw.trim();
            validateAbsolute(trimmed);
            normalized.add(trimmed);
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 校验单个 redirect_uri 是否为合法绝对 URI 且不含通配符
     */
    private void validateAbsolute(String uri) {
        if (uri.contains(WILDCARD)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_REDIRECT_URI_INVALID,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_REDIRECT_URI_INVALID, uri));
        }
        try {
            URI parsed = new URI(uri);
            if (parsed.getScheme() == null || parsed.getHost() == null) {
                throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_REDIRECT_URI_INVALID,
                        String.format(ServerErrorMessage.TRUSTED_APPLICATION_REDIRECT_URI_INVALID, uri));
            }
        } catch (URISyntaxException exception) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_REDIRECT_URI_INVALID,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_REDIRECT_URI_INVALID, uri));
        }
    }
}
