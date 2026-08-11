package io.github.surezzzzzz.sdk.ops.middleware.catalog;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 启动期数据源目录响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class DatasourceCatalogResponse {

    /**
     * 已初始化数据源条目。
     */
    private final List<Item> items;

    /**
     * 安全数据源目录条目。
     *
     * @author surezzzzzz
     */
    @Getter
    @Builder
    public static class Item {

        /**
         * 中间件类型。
         */
        private final MiddlewareType middlewareType;
        /**
         * 数据源标识。
         */
        private final String datasourceKey;
        /**
         * 启动期配置的自由展示标签。
         */
        private final String clusterTag;
    }
}
