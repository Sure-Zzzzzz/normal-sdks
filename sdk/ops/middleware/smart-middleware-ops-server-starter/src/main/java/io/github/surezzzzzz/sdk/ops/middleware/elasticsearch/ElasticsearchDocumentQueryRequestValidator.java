package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Elasticsearch 文档查询输入边界校验器。
 *
 * @author surezzzzzz
 */
public class ElasticsearchDocumentQueryRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<ElasticsearchDocumentQueryRequest> {

    private final int maxIndexLength;
    private final int maxDslLength;
    private final int maxSize;
    private final int maxOffset;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Elasticsearch 文档查询校验器。
     */
    public ElasticsearchDocumentQueryRequestValidator(int maxIndexLength, int maxDslLength, int maxSize,
                                                      int maxOffset, ObjectMapper objectMapper) {
        super(ElasticsearchDocumentQueryRequest.class);
        this.maxIndexLength = maxIndexLength;
        this.maxDslLength = maxDslLength;
        this.maxSize = maxSize;
        this.maxOffset = maxOffset;
        this.objectMapper = objectMapper;
    }

    @Override
    public void validate(ElasticsearchDocumentQueryRequest request) {
        requireDatasource(request.getDatasourceKey());
        if (request.getIndex() == null || request.getIndex().length() > maxIndexLength
                || !request.getIndex().matches("[a-z0-9*?][a-z0-9._+*?\\-]*(,[a-z0-9*?][a-z0-9._+*?\\-]*)*")) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "索引不符合查询规范");
        }
        if (request.getDsl() == null || request.getDsl().trim().isEmpty() || request.getDsl().length() > maxDslLength) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "JSON DSL 不符合查询规范");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(request.getDsl());
        } catch (Exception e) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "JSON DSL 格式无效");
        }
        if (root == null || !root.isObject() || containsUnsupportedQueryComponent(root)) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "JSON DSL 不符合查询规范");
        }
        validatePaging(root);
    }

    private void validatePaging(JsonNode root) {
        int size = readNonNegativeInteger(root.get("size"), 10, "结果数量超出允许范围");
        int from = readNonNegativeInteger(root.get("from"), 0, "起始位置超出允许范围");
        if (size > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
        if ((long) from + size > maxOffset) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "起始位置超出允许范围");
        }
    }

    private int readNonNegativeInteger(JsonNode value, int defaultValue, String message) {
        if (value == null) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || value.canConvertToInt() && value.intValue() < 0) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, message);
        }
        if (!value.canConvertToInt()) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, message);
        }
        return value.intValue();
    }

    private boolean containsUnsupportedQueryComponent(JsonNode root) {
        return root.has("pit") || root.has("scroll") || root.has("search_after") || root.has("runtime_mappings")
                || root.has("script_fields") || root.has("profile") || containsScript(root);
    }

    private boolean containsScript(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                java.util.Map.Entry<String, JsonNode> field = fields.next();
                if ("script".equals(field.getKey()) || containsScript(field.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode value : node) {
                if (containsScript(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
