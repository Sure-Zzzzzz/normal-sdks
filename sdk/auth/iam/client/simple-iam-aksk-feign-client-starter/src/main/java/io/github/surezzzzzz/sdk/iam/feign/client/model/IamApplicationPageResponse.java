package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IAM 可信应用目录分页 wire 模型（1.3.7）
 *
 * <p>Feign wire DTO：Spring Page wire 形态（content/totalElements，页码 0 起）。</p>
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamApplicationPageResponse {

    /**
     * 当前页条目。
     */
    public java.util.List<IamApplicationResponse> content;

    /**
     * 总元素数。
     */
    public long totalElements;

    /**
     * 总页数。
     */
    public int totalPages;

    /**
     * 页码（0 起）。
     */
    public int number;

    /**
     * 页大小。
     */
    public int size;
}

