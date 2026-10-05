package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;

/**
 * 实体索引与 ID 元数据读取，失败消息不包含业务字段名
 *
 * @author surezzzzzz
 */
public final class DocumentMetadataHelper {

    private DocumentMetadataHelper() {

    }

    /**
     * 优先使用显式索引，否则读取实体索引注解。
     */
    public static String resolveIndex(Object document, String explicitIndex) {
        if (StringUtils.hasText(explicitIndex)) {
            return explicitIndex;
        }
        if (document == null) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    ErrorMessage.REQUEST_VALIDATION_FAILED);
        }
        return resolveIndex(document.getClass());
    }

    /**
     * 优先使用显式索引，否则读取实体索引注解。
     */
    public static String resolveIndex(Class<?> documentClass) {
        if (documentClass == null) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    ErrorMessage.REQUEST_VALIDATION_FAILED);
        }
        Document document = documentClass.getAnnotation(Document.class);
        if (document == null || !StringUtils.hasText(document.indexName())) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    ErrorMessage.REQUEST_VALIDATION_FAILED);
        }
        return document.indexName();
    }

    /**
     * 优先使用显式 ID，否则读取实体 ID 字段。
     */
    public static String resolveId(Object document, String explicitId) {
        if (StringUtils.hasText(explicitId)) {
            return explicitId;
        }
        if (document == null) {
            return null;
        }
        Class<?> current = document.getClass();
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (field.getAnnotation(Id.class) != null) {
                    return getFieldValue(document, field);
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String getFieldValue(Object document, Field field) {
        try {
            field.setAccessible(true);
            Object value = field.get(document);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException | IllegalAccessException e) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    ErrorMessage.REQUEST_VALIDATION_FAILED);
        }
    }
}

