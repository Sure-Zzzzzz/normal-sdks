package io.github.surezzzzzz.sdk.mysql.route.model;

import lombok.Getter;

/**
 * MySQL Route 运行期连接凭据。
 *
 * @author surezzzzzz
 */
@Getter
public final class MySqlRouteCredential {

    private final String username;
    private final String password;

    public MySqlRouteCredential(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
