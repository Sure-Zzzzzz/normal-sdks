package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

import lombok.Getter;

/**
 * 索引路由异常
 * 索引路由处理失败时抛出
 *
 * @author surezzzzzz
 */
@Getter
public class IndexRouteException extends SimpleElasticsearchSearchException {

    private final String indexAlias;

    public IndexRouteException(String errorCode, String message, String indexAlias) {
        super(errorCode, message);
        this.indexAlias = indexAlias;
    }

    public IndexRouteException(String errorCode, String message, String indexAlias, Throwable cause) {
        super(errorCode, message, cause);
        this.indexAlias = indexAlias;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " (index: " + indexAlias + ")";
    }
}
