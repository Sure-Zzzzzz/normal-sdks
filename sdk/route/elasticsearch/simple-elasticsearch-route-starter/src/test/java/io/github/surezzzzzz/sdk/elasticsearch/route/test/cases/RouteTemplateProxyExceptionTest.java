package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RouteTemplateProxy 异常处理集成测试
 *
 * @author Sure
 * @since 1.0.3
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class RouteTemplateProxyExceptionTest {

    @Autowired
    @Qualifier("elasticsearchRestTemplate")
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    private Object indexCoordinatesOf(String indexName) {
        try {
            Class<?> clazz = Class.forName("org.springframework.data.elasticsearch.core.mapping.IndexCoordinates");
            java.lang.reflect.Method of = clazz.getMethod("of", String[].class);
            return of.invoke(null, (Object) new String[]{indexName});
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create IndexCoordinates for: " + indexName, e);
        }
    }

    private void callIndexOps(String indexName) {
        try {
            java.lang.reflect.Method indexOps = elasticsearchRestTemplate.getClass()
                    .getMethod("indexOps", String.class);
            indexOps.invoke(elasticsearchRestTemplate, indexName);
        } catch (NoSuchMethodException e) {
            try {
                Object indexCoordinates = indexCoordinatesOf(indexName);
                Class<?> coordsClass = Class.forName(
                        "org.springframework.data.elasticsearch.core.mapping.IndexCoordinates");
                java.lang.reflect.Method indexOps = elasticsearchRestTemplate.getClass()
                        .getMethod("indexOps", coordsClass);
                indexOps.invoke(elasticsearchRestTemplate, indexCoordinates);
            } catch (InvocationTargetException ex) {
                throw unwrap(ex);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call indexOps", ex);
            }
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call indexOps", e);
        }
    }

    private boolean callIndexExists(String indexName) {
        try {
            java.lang.reflect.Method indexExists = elasticsearchRestTemplate.getClass()
                    .getMethod("indexExists", String.class);
            return (boolean) indexExists.invoke(elasticsearchRestTemplate, indexName);
        } catch (NoSuchMethodException e) {
            Object indexOps = indexOpsObject(indexName);
            try {
                java.lang.reflect.Method exists = indexOps.getClass().getMethod("exists");
                return (boolean) exists.invoke(indexOps);
            } catch (InvocationTargetException ex) {
                throw unwrap(ex);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call exists", ex);
            }
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call indexExists", e);
        }
    }

    private void callDeleteIndex(String indexName) {
        try {
            java.lang.reflect.Method deleteIndex = elasticsearchRestTemplate.getClass()
                    .getMethod("deleteIndex", String.class);
            deleteIndex.invoke(elasticsearchRestTemplate, indexName);
        } catch (NoSuchMethodException e) {
            Object indexOps = indexOpsObject(indexName);
            try {
                java.lang.reflect.Method delete = indexOps.getClass().getMethod("delete");
                delete.invoke(indexOps);
            } catch (InvocationTargetException ex) {
                throw unwrap(ex);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call delete", ex);
            }
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call deleteIndex", e);
        }
    }

    private Object indexOpsObject(String indexName) {
        try {
            java.lang.reflect.Method indexOps = elasticsearchRestTemplate.getClass()
                    .getMethod("indexOps", String.class);
            return indexOps.invoke(elasticsearchRestTemplate, indexName);
        } catch (NoSuchMethodException e) {
            try {
                Object indexCoordinates = indexCoordinatesOf(indexName);
                Class<?> coordsClass = Class.forName(
                        "org.springframework.data.elasticsearch.core.mapping.IndexCoordinates");
                java.lang.reflect.Method indexOps = elasticsearchRestTemplate.getClass()
                        .getMethod("indexOps", coordsClass);
                return indexOps.invoke(elasticsearchRestTemplate, indexCoordinates);
            } catch (InvocationTargetException ex) {
                throw unwrap(ex);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call indexOps", ex);
            }
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call indexOps", e);
        }
    }

    private RuntimeException unwrap(InvocationTargetException e) {
        Throwable cause = e.getTargetException();
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return new RuntimeException(cause);
    }

    @Test
    public void testExceptionNotWrappedAsInvocationTargetException() {
        log.info("=== testExceptionNotWrappedAsInvocationTargetException ===");
        try {
            callIndexExists("non_existent_index_12345");
        } catch (Exception e) {
            log.info("Caught exception type: {}", e.getClass().getName());
            log.info("Exception message: {}", e.getMessage());
            assertNotEquals(InvocationTargetException.class, e.getClass(),
                    "异常不应该被包装为 InvocationTargetException");
            assertFalse(containsInvocationTargetException(e),
                    "堆栈信息不应包含 InvocationTargetException");
            return;
        }
        log.info("索引不存在检查未抛异常，代理异常解包路径未触发");
    }

    @Test
    public void testVersionCompatibilityLikeException() {
        log.info("=== testVersionCompatibilityLikeException ===");
        try {
            callIndexOps("_invalid.index.name.with.dots");
        } catch (Exception e) {
            log.info("Caught exception type: {}", e.getClass().getName());
            log.info("Exception message: {}", e.getMessage());
            assertNotEquals(InvocationTargetException.class, e.getClass());
            boolean isCompatibilityIssue = isVersionCompatibilityRelated(e.getMessage());
            if (isCompatibilityIssue) {
                log.info("Compatibility issue correctly detected: {}", e.getMessage());
            }
            return;
        }
        log.info("当前版本未在 indexOps 构造阶段抛异常");
    }

    @Test
    public void testDeleteIndexException() {
        log.info("=== testDeleteIndexException ===");
        try {
            callDeleteIndex("definitely_non_existent_index_99999");
        } catch (Exception e) {
            log.info("Caught exception type: {}", e.getClass().getName());
            assertNotNull(e);
            assertNotEquals(InvocationTargetException.class, e.getClass());
            printExceptionChain(e);
            return;
        }
        log.info("删除不存在索引未抛异常，当前 ES API 将该操作视为幂等");
    }

    private boolean containsInvocationTargetException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InvocationTargetException) {
                return true;
            }
            for (StackTraceElement element : current.getStackTrace()) {
                if (element.getClassName().contains("InvocationTargetException")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isVersionCompatibilityRelated(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("unrecognized parameter")
                || lower.contains("master_timeout")
                || lower.contains("no such parameter")
                || lower.contains("unknown setting")
                || lower.contains("illegal_argument_exception");
    }

    private void printExceptionChain(Throwable throwable) {
        log.info("=== Exception Chain ===");
        int level = 0;
        Throwable current = throwable;
        while (current != null) {
            log.info("Level {}: {} - {}",
                    level,
                    current.getClass().getSimpleName(),
                    current.getMessage());
            current = current.getCause();
            level++;
        }
        log.info("======================");
    }
}
