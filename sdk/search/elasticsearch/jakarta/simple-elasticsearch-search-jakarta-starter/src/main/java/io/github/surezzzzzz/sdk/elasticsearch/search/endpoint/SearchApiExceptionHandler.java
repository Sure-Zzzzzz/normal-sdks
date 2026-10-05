package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.SearchErrorResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;

/**
 * 仅覆盖本模块端点；错误由 HTTP 状态表达，不透传业务错误码或原因链。
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {SimpleElasticsearchSearchApiEndpoint.class, SearchExpressionApiEndpoint.class})
@ConditionalOnProperty(prefix = SimpleElasticsearchSearchConstant.CONFIG_PREFIX, name = {VALUE_ENABLE, VALUE_API_ENABLED}, havingValue = VALUE_TRUE)
public class SearchApiExceptionHandler {
    /**
     * 返回固定安全消息及可与 DEBUG 对应的请求标识。
     */
    @ExceptionHandler(SimpleElasticsearchSearchException.class)
    public ResponseEntity<SearchErrorResponse> sdk(SimpleElasticsearchSearchException error) {
        int status = error.getStatus();
        if (status < HTTP_BAD_REQUEST || status > HTTP_STATUS_MAX) status = HTTP_INTERNAL_SERVER_ERROR;
        return failure(status, safeMessage(error.getErrorCode()));
    }

    /**
     * 无法读取的正文只返回参数错误，不包含 Jackson 源片段。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<SearchErrorResponse> malformed(HttpMessageNotReadableException error) {
        return failure(HTTP_BAD_REQUEST, ErrorMessage.REQUEST_INVALID);
    }

    private ResponseEntity<SearchErrorResponse> failure(int status, String message) {
        String requestId = UUID.randomUUID().toString();
        log.debug("查询 HTTP 拒绝 requestId={} status={}", requestId, status);
        return ResponseEntity.status(status).body(new SearchErrorResponse(message, Instant.now().toString(), requestId));
    }

    private String safeMessage(String code) {
        if (ErrorCode.REQUEST_INVALID.equals(code)) return ErrorMessage.REQUEST_INVALID;
        if (ErrorCode.CONFIG_INVALID.equals(code)) return ErrorMessage.CONFIG_INVALID;
        if (ErrorCode.FIELD_INVALID.equals(code)) return ErrorMessage.FIELD_INVALID;
        if (ErrorCode.MAPPING_FAILED.equals(code)) return ErrorMessage.MAPPING_FAILED;
        if (ErrorCode.CURSOR_INVALID.equals(code)) return ErrorMessage.CURSOR_INVALID;
        if (ErrorCode.RESPONSE_INVALID.equals(code)) return ErrorMessage.RESPONSE_INVALID;
        return ErrorMessage.REST_FAILED;
    }
}
