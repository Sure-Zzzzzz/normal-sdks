package io.github.surezzzzzz.sdk.mysql.route.datasource;

import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.context.MySqlRouteContextHolder;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * MySQL Route Spring 路由 DataSource。
 *
 * @author surezzzzzz
 */
public class MySqlRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> targets;
    private final Object transactionResourceKey;
    private final Map<Connection, String> connectionDatasourceKeys =
            Collections.synchronizedMap(new WeakHashMap<Connection, String>());

    /**
     * 使用已注册的物理数据源创建严格路由数据源。
     *
     * @param targets datasourceKey 到物理数据源的映射
     */
    public MySqlRoutingDataSource(Map<Object, Object> targets) {
        this.targets = targets;
        this.transactionResourceKey = new Object();
        setTargetDataSources(targets);
        setLenientFallback(false);
        afterPropertiesSet();
    }

    /**
     * 按当前线程的 Route 作用域获取连接。
     *
     * @return 当前 datasourceKey 对应的连接
     * @throws SQLException 获取物理连接失败
     */
    @Override
    public Connection getConnection() throws SQLException {
        String datasourceKey = currentDatasourceKey();
        Connection connection = super.getConnection();
        connectionDatasourceKeys.put(connection, datasourceKey);
        return connection;
    }

    /**
     * 禁止调用方绕过受控 target 配置直接指定数据库连接凭据。
     *
     * @param username 调用方提供的用户名
     * @param password 调用方提供的密码
     * @return 永不返回
     * @throws SimpleMysqlRouteException 调用方尝试指定连接凭据
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SimpleMysqlRouteException(ErrorCode.USER_CREDENTIAL_CONNECTION_UNSUPPORTED,
                ErrorMessage.USER_CREDENTIAL_CONNECTION_UNSUPPORTED);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String datasourceKey = currentDatasourceKey();
        bindTransactionDatasource(datasourceKey);
        return datasourceKey;
    }

    /**
     * 在进入回调前绑定已激活事务的目标。
     *
     * @param datasourceKey 当前目标键
     */
    public void bindTransactionDatasource(String datasourceKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        Object bound = TransactionSynchronizationManager.getResource(transactionResourceKey);
        Connection transactionConnection = transactionConnection();
        String connectionDatasourceKey = transactionConnection == null
                ? null : connectionDatasourceKeys.get(transactionConnection);
        if (bound == null && connectionDatasourceKey != null) {
            bound = connectionDatasourceKey;
        }
        if (bound != null && !datasourceKey.equals(bound)) {
            throw new SimpleMysqlRouteException(ErrorCode.TRANSACTION_CROSS_DATASOURCE,
                    String.format(ErrorMessage.TRANSACTION_CROSS_DATASOURCE, datasourceKey));
        }
        if (!TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
            TransactionSynchronizationManager.bindResource(transactionResourceKey, datasourceKey);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
                        TransactionSynchronizationManager.unbindResourceIfPossible(transactionResourceKey);
                    }
                    if (transactionConnection != null) {
                        connectionDatasourceKeys.remove(transactionConnection);
                    }
                }
            });
        }
    }

    private String currentDatasourceKey() {
        String datasourceKey = MySqlRouteContextHolder.current();
        if (datasourceKey == null || datasourceKey.trim().isEmpty() || !targets.containsKey(datasourceKey)) {
            throw new SimpleMysqlRouteException(ErrorCode.CONTEXT_INVALID, ErrorMessage.CONTEXT_INVALID);
        }
        return datasourceKey;
    }

    private Connection transactionConnection() {
        Object resource = TransactionSynchronizationManager.getResource(this);
        if (!(resource instanceof ConnectionHolder)) {
            return null;
        }
        ConnectionHolder holder = (ConnectionHolder) resource;
        return holder.getConnection();
    }

}
