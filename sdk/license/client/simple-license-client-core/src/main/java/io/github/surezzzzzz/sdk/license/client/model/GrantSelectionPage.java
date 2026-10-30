package io.github.surezzzzzz.sdk.license.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 当前发现页及完整分页元数据；发现结果不是使用许可，仍须逐次授权。
 *
 * @author surezzzzzz
 */
public final class GrantSelectionPage {

    private final List<GrantCandidate> items;
    private final long total;
    private final int page;
    private final int size;
    private final long totalPages;

    public GrantSelectionPage(List<GrantCandidate> items, long total, int page, int size, long totalPages) {
        this.items = Collections.unmodifiableList(new ArrayList<GrantCandidate>(items));
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
    }

    public List<GrantCandidate> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalPages() {
        return totalPages;
    }
}
