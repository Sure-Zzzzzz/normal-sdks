package io.github.surezzzzzz.sdk.auth.iam.server.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 可信应用内置图标编码。
 *
 * @author surezzzzzz
 */
public final class TrustedApplicationIcon {

    private static final Set<String> CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "access-control", "users", "key", "settings", "lock", "dashboard", "project", "message",
            "calendar", "workflow", "data-service", "database", "analytics", "document", "search", "cloud",
            "network", "developer", "application", "default", "folder"
    )));

    private TrustedApplicationIcon() {
    }

    /**
     * 判断图标编码是否在受支持的图标集内（Portal 渲染前校验）
     */
    public static boolean isSupported(String code) {
        return CODES.contains(code);
    }
}
