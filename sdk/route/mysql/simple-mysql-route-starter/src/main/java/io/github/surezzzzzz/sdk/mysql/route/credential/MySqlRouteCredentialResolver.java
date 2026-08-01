package io.github.surezzzzzz.sdk.mysql.route.credential;

import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteCredential;

/**
 * MySQL Route 凭据解析 SPI。
 *
 * @author surezzzzzz
 */
public interface MySqlRouteCredentialResolver {

    /**
     * 按启动期凭据定位符解析连接凭据。
     *
     * @param credentialRef 凭据定位符
     * @return 连接凭据
     */
    MySqlRouteCredential resolve(String credentialRef);
}
