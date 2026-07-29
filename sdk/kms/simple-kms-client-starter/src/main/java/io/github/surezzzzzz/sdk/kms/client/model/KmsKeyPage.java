package io.github.surezzzzzz.sdk.kms.client.model;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KMS 逻辑密钥列表的分页 HTTP 响应。
 *
 * <p>分页字段属于列表协议而非 {@link KmsKey} 本身；构造时冻结 {@code items}，避免调用方修改
 * 返回集合而误认为会影响 Client 或服务端状态。</p>
 */
@Value
public class KmsKeyPage {
    List<KmsKey> items;
    Integer page;
    Integer size;
    Long total;

    /**
     * 创建分页结果。
     *
     * @param items 逻辑密钥集合
     * @param page  当前页码
     * @param size  每页数量
     * @param total 总数量
     */
    @Builder
    public KmsKeyPage(List<KmsKey> items, Integer page, Integer size, Long total) {
        this.items = Collections.unmodifiableList(new ArrayList<KmsKey>(items));
        this.page = page;
        this.size = size;
        this.total = total;
    }
}
