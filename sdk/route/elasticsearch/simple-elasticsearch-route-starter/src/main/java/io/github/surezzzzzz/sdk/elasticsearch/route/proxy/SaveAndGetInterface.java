package io.github.surezzzzzz.sdk.elasticsearch.route.proxy;

/**
 * JDK 代理扩展接口，确保 save(Object) / get(String, Class) 也能被拦截。
 *
 * <p>Spring Data Elasticsearch 3.x 的 ElasticsearchOperations 接口没有 save(Object)，
 * 只通过父类 ElasticsearchTemplate 提供此方法。JDK 代理只代理接口方法，
 * 所以要显式加入此接口来保证 save/get 也能走路由拦截逻辑。
 *
 * <p>Spring Data Elasticsearch 4.x 已在接口中声明这些方法，代理可直接拦截。
 *
 * @author surezzzzzz
 */
public interface SaveAndGetInterface {

    /**
     * 保存文档；Spring Data Elasticsearch 3.x 由 RouteRoutingInterceptor 走 HTTP 兼容写入。
     */
    Object save(Object entity);

    /**
     * 按 ID 查询；Spring Data Elasticsearch 3.x 由 RouteRoutingInterceptor 走 HTTP 兼容查询。
     */
    <T> T get(String id, Class<T> clazz);
}
