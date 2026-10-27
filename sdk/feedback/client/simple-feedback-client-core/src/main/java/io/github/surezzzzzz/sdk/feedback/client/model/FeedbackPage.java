package io.github.surezzzzzz.sdk.feedback.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Feedback 分页响应。 */
public final class FeedbackPage {

    private final List<FeedbackSubmission> items;
    private final long total;
    private final int page;
    private final int size;

    public FeedbackPage(List<Map<String, Object>> items, long total, int page, int size) {
        List<FeedbackSubmission> values = new ArrayList<FeedbackSubmission>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                values.add(new FeedbackSubmission(item));
            }
        }
        this.items = Collections.unmodifiableList(values);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<FeedbackSubmission> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getSize() { return size; }
}
