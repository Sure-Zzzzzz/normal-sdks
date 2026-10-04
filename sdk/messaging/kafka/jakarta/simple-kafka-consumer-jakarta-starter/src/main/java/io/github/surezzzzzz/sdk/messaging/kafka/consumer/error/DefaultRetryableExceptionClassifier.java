package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.FatalConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.RetryableConsumerException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * 默认异常分类器
 *
 * @author surezzzzzz
 */
public class DefaultRetryableExceptionClassifier implements RetryableExceptionClassifier {

    @Override
    public boolean isRetryable(Exception exception) {
        if (exception == null) {
            return false;
        }
        Set<Throwable> visited = new HashSet<>();
        boolean retryable = false;
        Throwable current = exception;
        while (current != null && visited.add(current)) {
            Class<?> type = current.getClass();
            if (type.isAnnotationPresent(FatalConsumerException.class) || isFatal(current)) {
                return false;
            }
            if (type.isAnnotationPresent(RetryableConsumerException.class) || isRetryableType(current)) {
                retryable = true;
            }
            current = current.getCause();
        }
        return retryable;
    }

    private boolean isFatal(Throwable throwable) {
        return throwable instanceof SerializationException
                || throwable instanceof IllegalArgumentException
                || throwable instanceof ClassCastException
                || throwable instanceof NullPointerException
                || hasClassName(throwable, "org.springframework.kafka.support.serializer.DeserializationException");
    }

    private boolean isRetryableType(Throwable throwable) {
        return throwable instanceof RetriableException
                || throwable instanceof TimeoutException
                || throwable instanceof InterruptedException
                || hasClassName(throwable, "org.springframework.kafka.listener.adapter.RecoverableException")
                || hasClassName(throwable, "org.springframework.kafka.support.converter.RecoverableException");
    }

    private boolean hasClassName(Throwable throwable, String className) {
        return throwable.getClass().getName().equals(className);
    }
}
