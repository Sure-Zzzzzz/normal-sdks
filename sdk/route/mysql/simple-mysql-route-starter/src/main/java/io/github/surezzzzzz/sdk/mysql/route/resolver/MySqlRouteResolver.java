package io.github.surezzzzzz.sdk.mysql.route.resolver;

/**
 * MySQL Route 业务键解析契约。
 *
 * @author surezzzzzz
 */
public interface MySqlRouteResolver {

    /**
     * 将调用方提供的业务路由键解析为已注册的数据源名称。
     *
     * @param routeKey 调用方提供的业务路由键
     * @return 已注册的数据源名称
     */
    String resolve(String routeKey);
}
