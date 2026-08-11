package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

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
    private final ObjectMapper objectMapper;

    /**
     * 创建 Elasticsearch 文档查询校验器。
     */
    public ElasticsearchDocumentQueryRequestValidator(int maxIndexLength, int maxDslLength, int maxSize,
                                                      ObjectMapper objectMapper) {
        super(ElasticsearchDocumentQueryRequest.class);
        this.maxIndexLength = maxIndexLength;
        this.maxDslLength = maxDslLength;
        this.maxSize = maxSize;
        this.objectMapper = objectMapper;
    }

    @Override
    public void validate(ElasticsearchDocumentQueryRequest request) {
        requireDatasource(request.getDatasourceKey());
        if (request.getIndex() == null || request.getIndex().trim().isEmpty()
                || request.getIndex().length() > maxIndexLength || containsControlCharacter(request.getIndex())) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "索引不符合查询规范");
        }
        if (request.getDsl() == null || request.getDsl().trim().isEmpty() || request.getDsl().length() > maxDslLength) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "JSON DSL 不符合查询规范");
        }
        try {
            objectMapper.readTree(request.getDsl());
        } catch (Exception e) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "JSON DSL 格式无效");
        }
        if (request.getSize() <= 0 || request.getSize() > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
        if (request.getPage() <= 0 || request.getPage() > Integer.MAX_VALUE / request.getSize()) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "页码超出允许范围");
        }
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
