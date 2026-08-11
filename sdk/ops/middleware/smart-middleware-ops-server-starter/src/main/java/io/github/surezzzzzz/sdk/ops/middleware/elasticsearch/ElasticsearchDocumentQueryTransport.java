package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Elasticsearch GET 查询参数编解码帮助器。
 *
 * @author surezzzzzz
 */
public final class ElasticsearchDocumentQueryTransport {

    private ElasticsearchDocumentQueryTransport() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    /**
     * 解码 URL 安全 Base64 传输的 JSON DSL。
     *
     * @param encodedDsl   编码后的 DSL
     * @param maxDslLength 解码后最大字符数
     * @return 原始 JSON DSL
     */
    public static String decodeDsl(String encodedDsl, int maxDslLength) {
        if (encodedDsl == null || encodedDsl.trim().isEmpty() || encodedDsl.length() > maxDslLength * 2) {
            throw invalid();
        }
        try {
            String dsl = new String(Base64.getUrlDecoder().decode(encodedDsl), StandardCharsets.UTF_8);
            if (dsl.length() > maxDslLength) {
                throw invalid();
            }
            return dsl;
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static MiddlewareOpsException invalid() {
        return new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "JSON DSL 传输参数无效");
    }
}
