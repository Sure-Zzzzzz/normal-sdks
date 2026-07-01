package io.github.surezzzzzz.sdk.elasticsearch.route.test;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.SimpleElasticsearchRouteException;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 测试辅助类：提供跨 Spring Data Elasticsearch 3.x / 4.x 的通用操作。
 *
 * <p>save/get 通过模板代理（路由）执行；indexExists/createIndex/deleteIndex 通过 RestClient HTTP 执行，
 * 避免 SDE 4.x CGLIB 代理不暴露 ElasticsearchOperations 接口导致的 NoSuchMethodException。
 *
 * @author surezzzzzz
 */
@Slf4j
public class EsApiHelper {

    private EsApiHelper() {
    }

    private static SimpleElasticsearchRouteException unwrap(InvocationTargetException e) {
        Throwable cause = e.getTargetException();
        if (cause instanceof SimpleElasticsearchRouteException) {
            return (SimpleElasticsearchRouteException) cause;
        }
        if (cause instanceof RuntimeException) {
            return new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, cause.getMessage(), cause);
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, cause.getMessage(), cause);
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
                    "Failed to check index existence via REST: " + indexName, e);
        } catch (Exception e) {
            throw new SimpleElasticsearchRouteException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    "Failed to check index existence via REST: " + indexName, e);
        }
    }

    private static void createIndexViaRest(RestClient client, String indexName) {
        try {
            client.performRequest(new Request("PUT", "/" + indexName));
        } catch (Exception e) {
            throw new SimpleElasticsearchRouteException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    "Failed to create index via REST: " + indexName, e);
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
                    "Failed to delete index via REST: " + indexName, e);
        } catch (Exception e) {
            throw new SimpleElasticsearchRouteException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    "Failed to delete index via REST: " + indexName, e);
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
    public static Object save(ElasticsearchRestTemplate template, Object entity) {
        try {
            Method save = template.getClass().getMethod("save", Object.class);
            return save.invoke(template, entity);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, "Failed to call save()", e);
        }
    }

    /**
     * 通过代理模板按 ID 查询文档（路由生效）。
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(ElasticsearchRestTemplate template, String id, Class<T> clazz) {
        try {
            Method get = template.getClass().getMethod("get", String.class, Class.class);
            return (T) get.invoke(template, id, clazz);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, "Failed to call get(" + id + ")", e);
        }
    }
}

