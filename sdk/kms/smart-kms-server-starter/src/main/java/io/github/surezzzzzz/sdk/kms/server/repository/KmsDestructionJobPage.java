package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * owner 限定销毁任务分页查询结果。
 *
 * @author surezzzzzz
 */
public final class KmsDestructionJobPage {

    private final List<KmsDestructionJob> items;
    private final long total;

    /**
     * 创建稳定排序的无材料任务分页投影。
     */
    public KmsDestructionJobPage(List<KmsDestructionJob> items, long total) {
        this.items = Collections.unmodifiableList(new ArrayList<KmsDestructionJob>(items));
        this.total = total;
    }

    /**
     * 获取当前页销毁任务。
     */
    public List<KmsDestructionJob> getItems() {
        return items;
    }

    /**
     * 获取 owner 内销毁任务总数。
     */
    public long getTotal() {
        return total;
    }
}
