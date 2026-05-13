package io.github.surezzzzzz.sdk.elasticsearch.search.binder;

/**
 * 字段绑定器接口
 * <p>
 * 继承自 nl-parser 的 FieldBinder SPI。
 * 由 search-starter 提供基于 field-mapping 配置的默认实现 {@link SearchFieldBinder}。
 *
 * @author surezzzzzz
 */
public interface FieldBinder extends io.github.surezzzzzz.sdk.naturallanguage.parser.binder.FieldBinder {
}
