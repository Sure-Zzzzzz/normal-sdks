package io.github.surezzzzzz.sdk.log.truncate.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.log.truncate.configuration.LogTruncateComponent;
import io.github.surezzzzzz.sdk.log.truncate.configuration.LogTruncateProperties;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
@LogTruncateComponent  // 只需要这个！
@Qualifier("logTruncator")
public class LogTruncator {

    private final LogTruncateProperties props;
    private final ObjectMapper mapper;

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    public LogTruncator(LogTruncateProperties properties) {
        this.props = properties;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_SELF_REFERENCES);

        log.debug("Initializing LogTruncator with maxTotalBytes={}, maxFieldChars={}",
                properties.getMaxTotalBytes(), properties.getMaxFieldChars());
    }

    /**
     * 截断任意对象为安全的日志字符串
     */
    public String truncate(Object any) {
        try {
            String rendered = render(any);
            String fieldSafe = applyFieldTruncate(rendered);
            return applyTotalBytesLimit(fieldSafe);
        } catch (Exception e) {
            log.warn("Failed to serialize object for truncation", e);
            String fallback = "<serialize_error: " + safeThrowable(e) + ">";
            return applyTotalBytesLimit(fallback);
        }
    }

    /**
     * 截断原始字符串（不做序列化）
     */
    public String truncateRaw(String raw) {
        if (raw == null) return null;
        return applyTotalBytesLimit(raw);
    }

    // 将对象渲染为 JSON 或字符串
    private String render(Object any) throws JsonProcessingException {
        if (any == null) return "null";
        if (any instanceof CharSequence) return any.toString();
        if (any instanceof Throwable) return renderThrowable((Throwable) any);

        // 优先转成树，便于后续深度控制和逐字段截断
        JsonNode node = mapper.valueToTree(any);
        JsonNode bounded = boundDepth(node, 0);
        return mapper.writeValueAsString(bounded);
    }

    private String renderThrowable(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    // 按最大深度替换深层结构为占位符
    private JsonNode boundDepth(JsonNode node, int depth) {
        if (node == null) return null;
        if (depth >= props.getMaxDepth()) {
            return mapper.getNodeFactory().textNode(props.getDepthExceededPlaceholder());
        }
        if (node.isObject()) {
            var obj = mapper.createObjectNode();
            node.fields().forEachRemaining(e ->
                    obj.set(e.getKey(), boundDepth(e.getValue(), depth + 1))
            );
            return obj;
        } else if (node.isArray()) {
            var arr = mapper.createArrayNode();
            node.forEach(n -> arr.add(boundDepth(n, depth + 1)));
            return arr;
        }
        return node;
    }

    // 对 JSON 字符串逐字段裁剪：仅裁剪文本值
    private String applyFieldTruncate(String jsonOrText) {
        try {
            JsonNode root = mapper.readTree(jsonOrText);
            JsonNode trimmed = trimTextNodes(root);
            return mapper.writeValueAsString(trimmed);
        } catch (Exception ignore) {
            // 不是合法 JSON，按普通文本返回
            return jsonOrText;
        }
    }

    private JsonNode trimTextNodes(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) {
            String v = node.asText();
            String cut = cutByChar(v, props.getMaxFieldChars());
            if (cut.length() < v.length()) {
                cut = addTruncatedSuffixByChars(v, cut);
            }
            return mapper.getNodeFactory().textNode(cut);
        } else if (node.isObject()) {
            var obj = mapper.createObjectNode();
            node.fields().forEachRemaining(e ->
                    obj.set(e.getKey(), trimTextNodes(e.getValue()))
            );
            return obj;
        } else if (node.isArray()) {
            var arr = mapper.createArrayNode();
            node.forEach(n -> arr.add(trimTextNodes(n)));
            return arr;
        }
        return node;
    }

    // 整体字节长度限制（UTF-8），保证不截断到半个字符
    private String applyTotalBytesLimit(String s) {
        if (s == null) return null;
        int max = Math.max(0, props.getMaxTotalBytes());
        byte[] bytes = s.getBytes(UTF8);
        if (bytes.length <= max) return s;

        // 按 code point 计算，保证不切到 UTF-8 多字节字符中间
        int allowed = computeAllowedCharEndByUtf8(s, max);
        String kept = s.substring(0, allowed);
        int droppedBytes = bytes.length - kept.getBytes(UTF8).length;

        return kept + props.getEllipsis()
                + props.getTruncatedNoteTemplate().replace("{dropped}", String.valueOf(droppedBytes));
    }

    // 以字符数量做字段级截断（按 code point）
    private String cutByChar(String s, int maxChars) {
        if (s == null) return null;
        if (maxChars <= 0) return "";
        int len = s.codePointCount(0, s.length());
        if (len <= maxChars) return s;

        int endIdx = s.offsetByCodePoints(0, maxChars);
        return s.substring(0, endIdx);
    }

    private String addTruncatedSuffixByChars(String original, String kept) {
        int dropped = original.codePointCount(0, original.length())
                - kept.codePointCount(0, kept.length());
        return kept + props.getEllipsis()
                + props.getTruncatedNoteTemplate().replace("{dropped}", String.valueOf(dropped));
    }

    private int computeAllowedCharEndByUtf8(String s, int maxBytes) {
        int i = 0;
        int used = 0;
        final int len = s.length();
        while (i < len) {
            int cp = s.codePointAt(i);
            int cpLen = utf8Len(cp);
            if (used + cpLen > maxBytes) break;
            used += cpLen;
            i += Character.charCount(cp);
        }
        return i;
    }

    private int utf8Len(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
    }

    private String safeThrowable(Throwable t) {
        try {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        } catch (Exception ignore) {
            return t.toString();
        }
    }
}
