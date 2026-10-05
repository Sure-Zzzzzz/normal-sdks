package io.github.surezzzzzz.sdk.elasticsearch.search.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Mapping 刷新终态，不携带原始 mapping、业务索引或异常正文。
 */
@Getter
public class MappingRefreshEvent extends ApplicationEvent {
    private final String datasource;
    private final boolean success;
    private final int fieldCount;

    /**
     * 只携带元数据刷新状态和字段数量，不携带索引或字段正文。
     */
    public MappingRefreshEvent(Object source, String datasource, boolean success, int fieldCount) {
        super(source);
        this.datasource = datasource;
        this.success = success;
        this.fieldCount = fieldCount;
    }
}
