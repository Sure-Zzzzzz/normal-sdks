package io.github.surezzzzzz.sdk.kms.server.repository;

import java.util.Collections;
import java.util.List;

/**
 * tenant 限定逻辑密钥分页查询结果。
 *
 * @author surezzzzzz
 */
public final class KmsKeyPage {

    private final List<KmsKeyMetadata> items;
    private final long total;

    /**
     * 创建稳定排序的分页查询结果。
     */
    public KmsKeyPage(List<KmsKeyMetadata> items, long total) {
        this.items = Collections.unmodifiableList(new java.util.ArrayList<KmsKeyMetadata>(items));
        this.total = total;
    }

    /**
     * 获取当前页无材料密钥元数据。
     */
    public List<KmsKeyMetadata> getItems() {
        return items;
    }

    /**
     * 获取筛选后的总记录数。
     */
    public long getTotal() {
        return total;
    }
}
