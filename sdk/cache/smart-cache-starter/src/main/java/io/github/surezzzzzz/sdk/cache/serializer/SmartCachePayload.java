package io.github.surezzzzzz.sdk.cache.serializer;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Smart Cache payload
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
public class SmartCachePayload {

    /**
     * 载荷协议版本；2.2.0 及更早载荷没有该字段，反序列化时按版本 1 处理。
     */
    private Integer schemaVersion;

    /**
     * 类型名称
     */
    private String type;

    /**
     * JSON 数据
     */
    private Object data;
}
