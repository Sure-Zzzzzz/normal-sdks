package io.github.surezzzzzz.sdk.elasticsearch.route.test;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.SimpleElasticsearchRouteException;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 测试辅助类：封装 Spring Data Elasticsearch 5 的路由调用与低层索引准备操作。
 *
 * <p>save/get 通过模板代理（路由）执行；indexExists/createIndex/deleteIndex 通过 RestClient HTTP 执行，
 * 索引准备不经 Spring Data 模板，避免将测试准备行为误计为路由行为。
 *
 * @author surezzzzzz
 */
@Slf4j
public class EsApiHelper {

    private static final long ROUTED_READ_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long ROUTED_READ_POLL_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50L);

    private EsApiHelper() {
    }

    private static SimpleElasticsearchRouteException unwrap(InvocationTargetException e) {
        Throwable cause = e.getTargetException();
        if (cause instanceof SimpleElasticsearchRouteException) {
            return (SimpleElasticsearchRouteException) cause;
        }
        if (cause instanceof RuntimeException) {
            return new RouteException(ErrorCode.ROUTE_REFLECTION_INVOKE_FAILED, cause.getMessage(), cause);
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return new RouteException(ErrorCode.ROUTE_REFLECTION_INVOKE_FAILED, cause.getMessage(), cause);
    }

    private static boolean indexExistsViaRest(RestClient client, String indexName) {
        try {
            client.performRequest(new Request("HEAD", "/" + indexName));
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw new SimpleElasticsearchRouteException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    String.format(ErrorMessage.OTHER_CLIENT_EXTRACT_FAILED,
                            "检查索引是否存在: " + indexName), e);
        } catch (Exception e) {
            throw new SimpleElasticsearchRouteException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    String.format(ErrorMessage.OTHER_CLIENT_EXTRACT_FAILED,
                            "检查索引是否存在: " + indexName), e);
        }
    }

    private static void createIndexViaRest(RestClient client, String indexName) {
        try {
            client.performRequest(new Request("PUT", "/" + indexName));
        } catch (Exception e) {
            throw new SimpleElasticsearchRouteException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    String.format(ErrorMessage.OTHER_CLIENT_EXTRACT_FAILED,
                            "创建索引: " + indexName), e);
        }
    }

    private static void deleteIndexViaRest(RestClient client, String indexName) {
        try {
            client.performRequest(new Request("DELETE", "/" + indexName));
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return;
            }
            throw new SimpleElasticsearchRouteException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    String.format(ErrorMessage.OTHER_CLIENT_EXTRACT_FAILED,
                            "删除索引: " + indexName), e);
        } catch (Exception e) {
            throw new SimpleElasticsearchRouteException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    String.format(ErrorMessage.OTHER_CLIENT_EXTRACT_FAILED,
                            "删除索引: " + indexName), e);
        }
    }

    /**
     * 通过 RestClient 检查索引是否存在（版本无关）。
     */
    public static boolean indexExists(RestClient client, String indexName) {
        return indexExistsViaRest(client, indexName);
    }

    /**
     * 通过 RestClient 创建索引（版本无关）。
     */
    public static void createIndex(RestClient client, String indexName) {
        createIndexViaRest(client, indexName);
    }

    /**
     * 通过 RestClient 删除索引（版本无关）。
     */
    public static void deleteIndex(RestClient client, String indexName) {
        deleteIndexViaRest(client, indexName);
    }

    /**
     * 通过代理模板保存文档（路由生效）。
     */
    public static Object save(ElasticsearchOperations template, Object entity) {
        try {
            Method save = template.getClass().getMethod("save", Object.class);
            return save.invoke(template, entity);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_REFLECTION_INVOKE_FAILED,
                    ErrorMessage.ROUTE_REFLECTION_INVOKE_FAILED, e);
        }
    }

    /**
     * 通过代理模板按 ID 查询文档（路由生效）。
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(ElasticsearchOperations template, String id, Class<T> clazz) {
        try {
            Method get = template.getClass().getMethod("get", String.class, Class.class);
            return (T) get.invoke(template, id, clazz);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_REFLECTION_INVOKE_FAILED,
                    ErrorMessage.ROUTE_REFLECTION_INVOKE_FAILED, e);
        }
    }

    /**
     * 轮询代理读直到 ES 搜索刷新完成或达到截止时间。日期分片通过 read-index.pattern 查询时不保证
     * 与按 ID 的低层实时读取同时可见，测试必须等待可验证的目标状态，不能依赖固定睡眠。
     */
    public static <T> T awaitGet(ElasticsearchOperations template, String id, Class<T> clazz) {
        long deadlineNanos = System.nanoTime() + ROUTED_READ_TIMEOUT_NANOS;
        T result;
        do {
            result = get(template, id, clazz);
            if (result != null) {
                return result;
            }
            LockSupport.parkNanos(ROUTED_READ_POLL_INTERVAL_NANOS);
        } while (System.nanoTime() < deadlineNanos);
        return null;
    }
}
