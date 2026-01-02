package io.github.surezzzzzz.sdk.naturallanguage.parser.binder;

import io.github.surezzzzzz.sdk.naturallanguage.parser.model.Intent;

/**
 * 意图转换器（SPI接口，由具体数据源实现）
 * <p>
 * 将通用的 Intent 转换为具体的查询对象
 *
 * @param <T> 目标查询类型
 *           - Elasticsearch: QueryCondition
 *           - MySQL: String (SQL WHERE子句) 或自定义Query对象
 *           - MongoDB: Document
 * @author surezzzzzz
 */
public interface IntentTranslator<T> {

    /**
     * 转换意图为具体查询对象
     *
     * @param intent 通用意图
     * @param context 转换上下文
     * @return 具体的查询对象
     */
    T translate(Intent intent, TranslateContext context);

    /**
     * 获取支持的数据源类型
     *
     * @return 数据源类型（如："elasticsearch", "mysql", "mongodb"）
     */
    String getDataSourceType();
}
