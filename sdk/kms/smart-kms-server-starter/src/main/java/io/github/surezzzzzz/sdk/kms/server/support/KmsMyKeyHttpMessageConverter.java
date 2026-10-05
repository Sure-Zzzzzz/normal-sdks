package io.github.surezzzzzz.sdk.kms.server.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.server.controller.KmsMyKeyDestructionRequest;
import io.github.surezzzzzz.sdk.kms.server.controller.KmsMyKeyResponse;
import io.github.surezzzzzz.sdk.kms.server.controller.KmsMyKeyStateRequest;
import io.github.surezzzzzz.sdk.kms.server.controller.KmsMyKeyVersionRequest;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 仅处理本人写接口 DTO 的私有 JSON 转换器，宿主宽松配置不能改变该契约。
 *
 * @author surezzzzzz
 */
public class KmsMyKeyHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    private static final int MIN_TIMESTAMP_YEAR = 1;
    private static final int MAX_TIMESTAMP_YEAR = 9999;

    /**
     * 创建 UTF-8 JSON 转换器。
     */
    public KmsMyKeyHttpMessageConverter() {
        super(StandardCharsets.UTF_8, MediaType.APPLICATION_JSON);
    }

    private static String requiredText(ObjectNode value, String field) {
        JsonNode node = value.get(field);
        if (node == null || !node.isTextual()) {
            throw new KmsValidationException();
        }
        return node.textValue();
    }

    private static long rowVersion(ObjectNode value) {
        JsonNode node = value.get("expectedRowVersion");
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            throw new KmsValidationException();
        }
        return node.longValue();
    }

    @Override
    protected boolean supports(Class<?> type) {
        return type == KmsMyKeyStateRequest.class || type == KmsMyKeyVersionRequest.class
                || type == KmsMyKeyDestructionRequest.class || type == KmsMyKeyResponse.class;
    }

    /**
     * 仅接收三种命令 DTO，不允许将响应快照作为请求。
     */
    @Override
    public boolean canRead(Class<?> type, MediaType mediaType) {
        return type != KmsMyKeyResponse.class && super.canRead(type, mediaType);
    }

    /**
     * 只序列化本人安全响应，宿主其他返回类型仍由其原转换器处理。
     */
    @Override
    public boolean canWrite(Class<?> type, MediaType mediaType) {
        return type == KmsMyKeyResponse.class && super.canWrite(type, mediaType);
    }

    @Override
    protected Object readInternal(Class<?> type, HttpInputMessage input) throws IOException {
        String body = StreamUtils.copyToString(input.getBody(), StandardCharsets.UTF_8);
        if (type == KmsMyKeyStateRequest.class) {
            ObjectNode value = KmsHttpJson.parseStrictObject(body, "state", "expectedRowVersion");
            String state = requiredText(value, "state");
            if (!KmsKeyState.ACTIVE.getCode().equals(state) && !KmsKeyState.DISABLED.getCode().equals(state)) {
                throw new KmsValidationException();
            }
            return new KmsMyKeyStateRequest(state, rowVersion(value));
        }
        if (type == KmsMyKeyDestructionRequest.class) {
            ObjectNode value = KmsHttpJson.parseStrictObject(body, "dueAt", "expectedRowVersion");
            String dueAt = requiredText(value, "dueAt");
            try {
                OffsetDateTime parsed = OffsetDateTime.parse(dueAt);
                OffsetDateTime utc = parsed.withOffsetSameInstant(ZoneOffset.UTC);
                // 时区换算也可能越过四位年份，须在 JDBC 写入前拒绝而非误报服务故障。
                if (parsed.getYear() < MIN_TIMESTAMP_YEAR || parsed.getYear() > MAX_TIMESTAMP_YEAR
                        || utc.getYear() < MIN_TIMESTAMP_YEAR || utc.getYear() > MAX_TIMESTAMP_YEAR) {
                    throw new KmsValidationException();
                }
                dueAt = KmsHttpJson.utcMillis(utc.toInstant());
            } catch (RuntimeException exception) {
                throw new KmsValidationException();
            }
            return new KmsMyKeyDestructionRequest(dueAt, rowVersion(value));
        }
        return new KmsMyKeyVersionRequest(rowVersion(KmsHttpJson.parseStrictObject(body, "expectedRowVersion")));
    }

    @Override
    protected void writeInternal(Object response, HttpOutputMessage output) throws IOException {
        KmsMyKeyResponse value = (KmsMyKeyResponse) response;
        String snapshot = value.getResponseSnapshot();
        if (snapshot == null) {
            snapshot = KmsHttpJson.write(value);
        }
        // 验证输出白名单后写原始字节，避免宿主 Mapper 或重放序列化改变首次响应。
        KmsMyKeyResponse.fromSnapshot(snapshot);
        output.getBody().write(snapshot.getBytes(StandardCharsets.UTF_8));
    }
}
