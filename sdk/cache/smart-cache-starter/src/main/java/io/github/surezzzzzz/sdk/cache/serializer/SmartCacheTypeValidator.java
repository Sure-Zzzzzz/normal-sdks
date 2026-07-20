package io.github.surezzzzzz.sdk.cache.serializer;

/**
 * Smart Cache 类型校验器
 *
 * @author surezzzzzz
 */
public interface SmartCacheTypeValidator {

    /**
     * 校验类型是否可信
     *
     * @param typeName 类型名称
     * @return true 表示可信
     */
    boolean isTrusted(String typeName);
}
