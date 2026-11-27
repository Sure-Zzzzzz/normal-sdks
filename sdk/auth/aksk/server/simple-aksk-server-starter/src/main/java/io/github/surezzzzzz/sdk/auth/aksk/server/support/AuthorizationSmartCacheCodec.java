package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;
import java.util.Base64;

/**
 * OAuth2Authorization 的 SmartCache 专用编解码器。
 *
 * <p>Spring Security Jackson 读侧不信任 OAuth2Authorization 顶层类型；授权对象又包含
 * 多态 token 和 attribute。这里使用字节载荷保留完整对象，并在读侧强制类名白名单。
 * Token 管理扫描同一 Redis 值时复用该解码器，避免两套格式漂移。</p>
 *
 * @author surezzzzzz
 */
public final class AuthorizationSmartCacheCodec {

    /**
     * 授权对象专用载荷版本；升级字段结构时必须同步递增并让旧值自然失效。
     */
    public static final int SCHEMA_VERSION = 1;

    /**
     * 标识 Base64 封装的授权对象载荷。
     */
    public static final String CODEC = "jdk-oauth2-authorization";

    /**
     * 仅封装外层 JSON 使用，不参与授权对象内部多态序列化。
     */
    private static final ObjectMapper ENVELOPE_OBJECT_MAPPER = new ObjectMapper();

    private AuthorizationSmartCacheCodec() {
    }

    /**
     * 编码授权对象为 SmartCache 字符串载荷。
     *
     * @param authorization 授权对象
     * @return JSON 外壳 + Base64 授权字节
     */
    public static String encode(OAuth2Authorization authorization) throws IOException {
        ObjectNode payload = ENVELOPE_OBJECT_MAPPER.createObjectNode();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("codec", CODEC);
        payload.put("type", OAuth2Authorization.class.getName());
        payload.put("data", Base64.getEncoder().encodeToString(javaSerialize(authorization)));
        return ENVELOPE_OBJECT_MAPPER.writeValueAsString(payload);
    }

    /**
     * 判断 Redis 原始值是否为本编码器外壳。
     *
     * @param rawBytes Redis 原始字节
     * @return true 表示本 SDK 2.2.1 授权载荷
     */
    public static boolean isEnvelope(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return false;
        }
        try {
            JsonNode root = ENVELOPE_OBJECT_MAPPER.readTree(rawBytes);
            return root.isObject()
                    && CODEC.equals(root.path("codec").asText(null))
                    && root.hasNonNull("data");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 解码并还原授权对象；版本、类型和反序列化类名全部校验。
     *
     * @param rawBytes Redis 原始字节
     * @return 授权对象
     */
    public static OAuth2Authorization decodeEnvelope(byte[] rawBytes) throws IOException, ClassNotFoundException {
        JsonNode root = ENVELOPE_OBJECT_MAPPER.readTree(rawBytes);
        int schemaVersion = root.path("schemaVersion").asInt(-1);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new StreamCorruptedException("不支持的 OAuth2Authorization 缓存版本：" + schemaVersion);
        }
        String typeName = root.path("type").asText(null);
        if (!OAuth2Authorization.class.getName().equals(typeName)) {
            throw new StreamCorruptedException("不支持的授权缓存类型：" + typeName);
        }
        byte[] data = Base64.getDecoder().decode(root.path("data").asText());
        try (ObjectInputStream stream = new AuthorizationObjectInputStream(new ByteArrayInputStream(data))) {
            return (OAuth2Authorization) stream.readObject();
        }
    }

    /**
     * 解码历史裸 Java 序列化授权缓存；必须与专用外壳共用同一类名白名单。
     *
     * @param rawBytes Redis 中的 0xACED 开头历史字节
     * @return 授权对象
     */
    public static OAuth2Authorization decodeRawJavaSerialized(byte[] rawBytes)
            throws IOException, ClassNotFoundException {
        if (rawBytes == null || rawBytes.length < 2
                || (rawBytes[0] & 0xFF) != 0xAC || (rawBytes[1] & 0xFF) != 0xED) {
            throw new StreamCorruptedException("不是历史 Java 序列化授权缓存");
        }
        try (ObjectInputStream stream = new AuthorizationObjectInputStream(new ByteArrayInputStream(rawBytes))) {
            return (OAuth2Authorization) stream.readObject();
        }
    }

    private static byte[] javaSerialize(OAuth2Authorization authorization) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream stream = new ObjectOutputStream(buffer)) {
            stream.writeObject(authorization);
            return buffer.toByteArray();
        }
    }

    /**
     * 反序列化类名白名单，覆盖授权对象及其官方 token/attribute 类型。
     */
    private static final class AuthorizationObjectInputStream extends ObjectInputStream {

        private AuthorizationObjectInputStream(ByteArrayInputStream input) throws IOException {
            super(input);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
            String name = descriptor.getName();
            if (!isAllowed(name)) {
                throw new StreamCorruptedException("禁止反序列化授权缓存类：" + name);
            }
            return super.resolveClass(descriptor);
        }

        private boolean isAllowed(String name) {
            if (name == null || name.isEmpty()) {
                return false;
            }
            return name.startsWith("[")
                    || name.startsWith("java.lang.")
                    || name.startsWith("java.util.")
                    || name.startsWith("java.time.")
                    || name.equals("java.net.URL")
                    || name.startsWith("org.springframework.security.oauth2.core.")
                    || name.startsWith("org.springframework.security.oauth2.server.authorization.")
                    || name.startsWith("org.springframework.security.core.authority.");
        }
    }
}
