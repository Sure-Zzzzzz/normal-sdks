package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Elasticsearch 字段能力目录输入边界校验器。
 *
 * @author surezzzzzz
 */
public class ElasticsearchFieldCapabilitiesRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<ElasticsearchFieldCapabilitiesRequest> {

    private final int maxIndexLength;

    /**
     * 创建字段能力目录校验器。
     *
     * @param maxIndexLength 索引名称最大长度
     */
    public ElasticsearchFieldCapabilitiesRequestValidator(int maxIndexLength) {
        super(ElasticsearchFieldCapabilitiesRequest.class);
        this.maxIndexLength = maxIndexLength;
    }

    @Override
    public void validate(ElasticsearchFieldCapabilitiesRequest request) {
        requireDatasource(request.getDatasourceKey());
        String index = request.getIndex();
        if (index == null || index.length() > maxIndexLength || !index.matches("[a-z0-9][a-z0-9._+\\-]*")) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "索引不符合字段补全规范");
        }
    }
}
