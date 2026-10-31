package io.github.surezzzzzz.sdk.license.server.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.constant.RoleProjectionConstant;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeSet;

/** 角色协作请求摘要：对象字段排序避免同一JSON仅字段顺序变化误判冲突。 */
public final class ProjectionRequestHelper {
    private ProjectionRequestHelper() { }

    /** 幂等键按已验证主体与准确HTTP目标隔离，不保存原键。 */
    public static String requestDigest(String principalId, String path, String key) {
        if (key == null || key.trim().isEmpty() || key.length() > 256) {
            throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES);
        }
        ObjectNode parts = StrictJson.mapper().createObjectNode();
        parts.put("principal", principalId); parts.put("path", path); parts.put("key", key);
        return digest(parts.toString());
    }

    /** 规范化JSON输入摘要；数组顺序保持不变，不改变商业输入。 */
    public static String inputDigest(JsonNode input) { return digest(canonical(input).toString()); }

    private static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = StrictJson.mapper().createObjectNode();
            TreeSet<String> names = new TreeSet<>(); value.fieldNames().forEachRemaining(names::add);
            for (String name : names) { sorted.set(name, canonical(value.get(name))); }
            return sorted;
        }
        if (value.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode array = StrictJson.mapper().createArrayNode();
            value.forEach(item -> array.add(canonical(item))); return array;
        }
        return value;
    }

    private static String digest(String text) {
        try { return Base64Url.encode(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
