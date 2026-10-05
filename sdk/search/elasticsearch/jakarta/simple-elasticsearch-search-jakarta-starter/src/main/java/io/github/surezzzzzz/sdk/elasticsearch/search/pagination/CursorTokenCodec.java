package io.github.surezzzzzz.sdk.elasticsearch.search.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.model.CursorState;

/**
 * 可替换的分页 token 编解码；必须认证、加密并检查过期时间。
 */
public interface CursorTokenCodec {
    /**
     * 校验使用前置条件，失败在发送业务 HTTP 前暴露。
     */
    void validate();

    /**
     * 编码独立快照，失败不携带源数据。
     */
    String encode(CursorState state);

    /**
     * 严格解码协议对象，失败不携带原始正文。
     */
    CursorState decode(String token, String kind, boolean allowExpired);
}
