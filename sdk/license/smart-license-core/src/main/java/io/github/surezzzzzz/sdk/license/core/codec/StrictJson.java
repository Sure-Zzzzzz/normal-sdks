package io.github.surezzzzzz.sdk.license.core.codec;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import java.io.IOException;

/**
 * SDK 私有严格 JSON 工厂：每次解析新建 Mapper，不复用宿主 Bean。
 *
 * <p>禁用默认多态；拒绝未知字段、重复键、非有限数；深度、大小、整数边界全部受协议预算约束。
 * 不允许任意类反序列化。</p>
 *
 * @author surezzzzzz
 */
public final class StrictJson {

    private StrictJson() {
    }

    /** 创建严格 Mapper（每次调用新建，不缓存不共享）。 */
    public static ObjectMapper mapper() {
        JsonFactory factory = new JsonFactory();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        // 嵌套深度上限按协议预算收紧（默认 1000 → 64，防超深结构炸弹）
        factory.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(SmartLicenseCoreConstant.JSON_MAX_DEPTH)
                .build());
        ObjectMapper mapper = JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
        // 协议标量不做便利转换：数字/布尔值不得成为能力或动作文本，字符串不得成为整数。
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }

    /** 严格解析 JSON 文本；解析失败统一抛协议异常。 */
    public static <T> T parse(String text, Class<T> type) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("JSON 文本不得为空");
        }
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES) {
            throw new IllegalArgumentException("JSON 文本超协议预算");
        }
        try {
            return mapper().readValue(text, type);
        } catch (IOException failure) {
            throw new IllegalArgumentException("JSON 解析失败：" + failure.getClass().getSimpleName());
        }
    }

    /** 严格序列化；结果受协议预算约束。 */
    public static String write(Object value) {
        try {
            String text = mapper().writeValueAsString(value);
            if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES) {
                throw new IllegalArgumentException("JSON 序列化结果超协议预算");
            }
            return text;
        } catch (IOException failure) {
            throw new IllegalArgumentException("JSON 序列化失败：" + failure.getClass().getSimpleName());
        }
    }
}
