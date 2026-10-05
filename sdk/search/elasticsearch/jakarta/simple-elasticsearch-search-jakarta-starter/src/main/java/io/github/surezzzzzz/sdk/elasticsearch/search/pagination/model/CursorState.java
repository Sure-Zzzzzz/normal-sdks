package io.github.surezzzzzz.sdk.elasticsearch.search.pagination.model;

import lombok.*;

import java.util.List;

/**
 * 仅在认证加密 token 内流转的分页所有权，不作为 API 原文响应。
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorState {
    private String kind, datasource, identifier, rawId, requestHash, from, to;
    private String[] indices;
    private long expiresAt, seen;
    private int downgradeLevel;
    private List<Object> sortValues;
}
