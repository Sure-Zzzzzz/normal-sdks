package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.support;

/**
 * Header 名称转换工具
 *
 * <p>将 HTTP Header 名称转换为 camelCase 格式。
 *
 * <p>转换规则：
 * <ol>
 *   <li>移除前缀（如 "x-sure-auth-aksk-"）</li>
 *   <li>按 "-" 分割</li>
 *   <li>第一个部分保持小写，后续部分首字母大写</li>
 * </ol>
 *
 * <p>示例：
 * <pre>{@code
 * toCamelCase("x-sure-auth-aksk-user-id", "x-sure-auth-aksk-")     // "userId"
 * toCamelCase("x-sure-auth-aksk-tenant-id", "x-sure-auth-aksk-")  // "tenantId"
 * toCamelCase("x-sure-auth-aksk-username", "x-sure-auth-aksk-")   // "username"
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class HeaderNameConverter {

    /**
     * 将 Header 名称转换为 camelCase
     *
     * @param headerName Header 名称（如 "x-sure-auth-aksk-user-id"）
     * @param prefix     前缀（如 "x-sure-auth-aksk-"）
     * @return camelCase 格式的字段名（如 "userId"）
     */
    public static String toCamelCase(String headerName, String prefix) {
        if (headerName == null || headerName.isEmpty()) {
            return "";
        }

        // 1. 移除前缀
        String withoutPrefix;
        if (prefix != null && !prefix.isEmpty() && headerName.startsWith(prefix)) {
            withoutPrefix = headerName.substring(prefix.length());
        } else {
            withoutPrefix = headerName;
        }

        // 2. 按 "-" 分割
        String[] parts = withoutPrefix.split("-");
        if (parts.length == 0) {
            return "";
        }

        // 3. 第一个部分保持小写，后续部分首字母大写
        StringBuilder camelCase = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            camelCase.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                camelCase.append(parts[i].substring(1).toLowerCase());
            }
        }

        return camelCase.toString();
    }

    private HeaderNameConverter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
