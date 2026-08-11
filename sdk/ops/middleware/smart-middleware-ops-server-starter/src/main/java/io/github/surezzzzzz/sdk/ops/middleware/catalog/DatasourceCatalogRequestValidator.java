package io.github.surezzzzzz.sdk.ops.middleware.catalog;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * 数据源目录请求校验器。
 *
 * @author surezzzzzz
 */
public class DatasourceCatalogRequestValidator extends DefaultMiddlewareOpsRequestValidator<DatasourceCatalogRequest> {

    /**
     * 创建目录请求校验器。
     */
    public DatasourceCatalogRequestValidator() {
        super(DatasourceCatalogRequest.class);
    }

    @Override
    public void validate(DatasourceCatalogRequest request) {
        if (request.getMiddlewareType() == null || request.getCapability() == null) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "数据源目录工作区无效");
        }
    }
}
