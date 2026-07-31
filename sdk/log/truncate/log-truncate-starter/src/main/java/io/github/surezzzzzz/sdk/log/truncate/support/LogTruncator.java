package io.github.surezzzzzz.sdk.log.truncate.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.log.truncate.configuration.LogTruncateComponent;
import io.github.surezzzzzz.sdk.log.truncate.configuration.LogTruncateProperties;
import io.github.surezzzzzz.sdk.log.truncate.constant.LogTruncateConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 日志截断器
 *
 * @author surezzzzzz
 */
@Slf4j
@LogTruncateComponent
@Qualifier(LogTruncateConstant.LOG_TRUNCATOR_BEAN_NAME)
public class LogTruncator {

    private final LogTruncateProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 创建日志截断器
     *
     * @param properties 日志截断配置
     */
    public LogTruncator(LogTruncateProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_SELF_REFERENCES);
        log.debug("初始化日志截断器，maxTotalBytes={}, maxFieldChars={}",
                properties.getMaxTotalBytes(), properties.getMaxFieldChars());
    }

    /**
     * 将任意对象截断为安全日志字符串
     *
     * @param value 待截断对象
     * @return 截断后的日志字符串
     */
    public String truncate(Object value) {
        try {
            String rendered = render(value);
            return applyTotalBytesLimit(applyFieldTruncate(rendered));
        } catch (Exception exception) {
            log.warn("日志对象序列化失败，使用降级文本输出", exception);
            return applyTotalBytesLimit("<serialize_error: " + safeThrowable(exception) + ">");
        }
    }

    /**
     * 截断原始字符串
     *
     * @param raw 原始字符串
     * @return 截断后的字符串
     */
    public String truncateRaw(String raw) {
        if (raw == null) {
            return null;
        }
        return applyTotalBytesLimit(raw);
    }

    private String render(Object value) throws JsonProcessingException {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Throwable) {
            return renderThrowable((Throwable) value);
        }
        JsonNode node = objectMapper.valueToTree(value);
        return objectMapper.writeValueAsString(boundDepth(node, 0));
    }

    private String renderThrowable(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        return stringWriter.toString();
    }

    private JsonNode boundDepth(JsonNode node, int depth) {
        if (node == null) {
            return null;
        }
        if (depth >= properties.getMaxDepth()) {
            return objectMapper.getNodeFactory().textNode(safeText(properties.getDepthExceededPlaceholder()));
        }
        if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> objectNode.set(entry.getKey(), boundDepth(entry.getValue(), depth + 1)));
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            node.forEach(item -> arrayNode.add(boundDepth(item, depth + 1)));
            return arrayNode;
        }
        return node;
    }

    private String applyFieldTruncate(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            return objectMapper.writeValueAsString(trimTextNodes(root));
        } catch (Exception ignored) {
            return value;
        }
    }

    private JsonNode trimTextNodes(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(truncateByCodePoints(node.asText(), properties.getMaxFieldChars()));
        }
        if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> objectNode.set(entry.getKey(), trimTextNodes(entry.getValue())));
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            node.forEach(item -> arrayNode.add(trimTextNodes(item)));
            return arrayNode;
        }
        return node;
    }

    private String applyTotalBytesLimit(String value) {
        if (value == null) {
            return null;
        }
        int maxBytes = properties.getMaxTotalBytes();
        if (maxBytes <= 0) {
            return "";
        }
        int totalBytes = utf8Length(value);
        if (totalBytes <= maxBytes) {
            return value;
        }

        int rawEnd = 0;
        int usedBytes = 0;
        int bestEnd = hasMarkerSpace(totalBytes, maxBytes) ? 0 : -1;
        int bestDropped = totalBytes;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            int codePointBytes = utf8Length(codePoint);
            if (usedBytes + codePointBytes > maxBytes) {
                break;
            }
            usedBytes += codePointBytes;
            index += Character.charCount(codePoint);
            rawEnd = index;
            int droppedBytes = totalBytes - usedBytes;
            if (usedBytes + utf8Length(buildTruncatedMarker(droppedBytes)) <= maxBytes) {
                bestEnd = index;
                bestDropped = droppedBytes;
            }
        }
        if (bestEnd >= 0) {
            return value.substring(0, bestEnd) + buildTruncatedMarker(bestDropped);
        }
        return value.substring(0, rawEnd);
    }

    private String truncateByCodePoints(String value, int maxCodePoints) {
        if (value == null || maxCodePoints <= 0) {
            return "";
        }
        int totalCodePoints = value.codePointCount(0, value.length());
        if (totalCodePoints <= maxCodePoints) {
            return value;
        }

        int rawEnd = 0;
        int bestEnd = codePointLength(buildTruncatedMarker(totalCodePoints)) <= maxCodePoints ? 0 : -1;
        int bestDropped = totalCodePoints;
        for (int keptCodePoints = 1; keptCodePoints <= maxCodePoints; keptCodePoints++) {
            rawEnd = value.offsetByCodePoints(0, keptCodePoints);
            int droppedCodePoints = totalCodePoints - keptCodePoints;
            if (keptCodePoints + codePointLength(buildTruncatedMarker(droppedCodePoints)) <= maxCodePoints) {
                bestEnd = rawEnd;
                bestDropped = droppedCodePoints;
            }
        }
        if (bestEnd >= 0) {
            return value.substring(0, bestEnd) + buildTruncatedMarker(bestDropped);
        }
        return value.substring(0, rawEnd);
    }

    private boolean hasMarkerSpace(int dropped, int maxBytes) {
        return utf8Length(buildTruncatedMarker(dropped)) <= maxBytes;
    }

    private String buildTruncatedMarker(int dropped) {
        return safeText(properties.getEllipsis())
                + safeText(properties.getTruncatedNoteTemplate())
                .replace(LogTruncateConstant.DROPPED_PLACEHOLDER, String.valueOf(dropped));
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private int utf8Length(String value) {
        int length = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            length += utf8Length(codePoint);
            index += Character.charCount(codePoint);
        }
        return length;
    }

    private int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String safeThrowable(Throwable throwable) {
        try {
            return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        } catch (Exception ignored) {
            return throwable.toString();
        }
    }
}
