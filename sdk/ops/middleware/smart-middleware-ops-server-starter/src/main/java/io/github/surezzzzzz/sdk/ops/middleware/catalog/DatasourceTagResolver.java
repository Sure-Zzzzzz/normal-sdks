package io.github.surezzzzzz.sdk.ops.middleware.catalog;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;

/**
 * 启动期数据源展示标签解析器。
 *
 * @author surezzzzzz
 */
public interface DatasourceTagResolver {

    /**
     * 按中间件类型和数据源标识解析启动期标签。
     *
     * @param middlewareType 中间件类型
     * @param datasourceKey  数据源标识
     * @return 未配置或不在启动快照中的数据源返回 null
     */
    String resolve(MiddlewareType middlewareType, String datasourceKey);
}
