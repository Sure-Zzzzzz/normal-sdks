package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;

/**
 * Document Metadata Helper
 *
 * @author surezzzzzz
 */
public final class DocumentMetadataHelper {

    private DocumentMetadataHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String resolveIndex(Object document, String explicitIndex) {
        if (StringUtils.hasText(explicitIndex)) {
            return explicitIndex;
        }
        if (document == null) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "document 不能为空"));
        }
        return resolveIndex(document.getClass());
    }

    public static String resolveIndex(Class<?> documentClass) {
        if (documentClass == null) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "documentClass 不能为空"));
        }
        Document document = documentClass.getAnnotation(Document.class);
        if (document == null || !StringUtils.hasText(document.indexName())) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "未找到 @Document.indexName"));
        }
        return document.indexName();
    }

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
        } catch (IllegalAccessException e) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, field.getName()), e);
        }
    }
}
