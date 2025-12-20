package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

/**
 * 查询异常
 *
 * @author surezzzzzz
 */
public class QueryException extends SimpleElasticsearchSearchException {

    public QueryException(String errorCode, String message) {
        super(errorCode, message);
    }

    public QueryException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
