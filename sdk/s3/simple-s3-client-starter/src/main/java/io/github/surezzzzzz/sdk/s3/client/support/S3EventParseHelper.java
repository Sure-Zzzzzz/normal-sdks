package io.github.surezzzzzz.sdk.s3.client.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.s3.client.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.s3.client.exception.EventParseFailedException;
import io.github.surezzzzzz.sdk.s3.client.model.S3Event;

/**
 * S3 Event Parse Helper
 *
 * @author surezzzzzz
 */
public final class S3EventParseHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private S3EventParseHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析 S3 事件通知 JSON
     *
     * @param json 事件通知 JSON 字符串
     * @return 解析后的事件对象
     * @throws EventParseFailedException JSON 为空或格式非法
     */
    public static S3Event parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new EventParseFailedException(ErrorCode.EVENT_PARSE_FAILED,
                    String.format(ErrorMessage.EVENT_PARSE_FAILED, "事件 JSON 为空"));
        }
        try {
            return OBJECT_MAPPER.readValue(json, S3Event.class);
        } catch (JsonProcessingException e) {
            throw new EventParseFailedException(ErrorCode.EVENT_PARSE_FAILED,
                    String.format(ErrorMessage.EVENT_PARSE_FAILED, e.getMessage()), e);
        }
    }
}
