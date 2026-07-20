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
     * 类型名称
     */
    private String type;

    /**
     * JSON 数据
     */
    private Object data;
}
