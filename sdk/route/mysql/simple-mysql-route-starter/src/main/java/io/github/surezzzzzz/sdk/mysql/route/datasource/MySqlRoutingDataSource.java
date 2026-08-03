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
    private final String primaryDatasource;
    private final Object transactionResourceKey;
    private final Map<Connection, String> connectionDatasources =
            Collections.synchronizedMap(new WeakHashMap<Connection, String>());

    /**
     * 使用已注册的物理数据源创建严格路由数据源。
     *
     * @param targets datasource 到物理数据源的映射
     */
    public MySqlRoutingDataSource(Map<Object, Object> targets) {
        this(targets, null);
    }

    /**
     * 使用已注册的物理数据源和主数据源名称创建路由数据源。
     *
     * @param targets           datasource 到物理数据源的映射
     * @param primaryDatasource 无 Route 作用域时使用的配置主数据源名称；可为空以保持严格 scope 模式
     */
    public MySqlRoutingDataSource(Map<Object, Object> targets, String primaryDatasource) {
        this.targets = targets;
        validatePrimaryDatasource(primaryDatasource);
        this.primaryDatasource = primaryDatasource;
        this.transactionResourceKey = new Object();
        setTargetDataSources(targets);
        setLenientFallback(false);
        afterPropertiesSet();
    }

    /**
     * 按当前线程 Route 作用域或主数据源获取连接。
     *
     * @return 当前 datasource 对应的连接
     * @throws SQLException 获取物理连接失败
     */
    @Override
    public Connection getConnection() throws SQLException {
        String datasource = currentDatasource();
        Connection connection = super.getConnection();
        connectionDatasources.put(connection, datasource);
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
        String datasource = currentDatasource();
        bindTransactionDatasource(datasource);
        return datasource;
    }

    /**
     * 在进入回调前绑定已激活事务的 datasource。
     *
     * @param datasource 当前 datasource 名称
     */
    public void bindTransactionDatasource(String datasource) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        Object bound = TransactionSynchronizationManager.getResource(transactionResourceKey);
        Connection transactionConnection = transactionConnection();
        String connectionDatasource = transactionConnection == null
                ? null : connectionDatasources.get(transactionConnection);
        if (bound == null && connectionDatasource != null) {
            bound = connectionDatasource;
        }
        if (bound != null && !datasource.equals(bound)) {
            throw new SimpleMysqlRouteException(ErrorCode.TRANSACTION_CROSS_DATASOURCE,
                    String.format(ErrorMessage.TRANSACTION_CROSS_DATASOURCE, datasource));
        }
        if (!TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
            TransactionSynchronizationManager.bindResource(transactionResourceKey, datasource);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
                        TransactionSynchronizationManager.unbindResourceIfPossible(transactionResourceKey);
                    }
                    if (transactionConnection != null) {
                        connectionDatasources.remove(transactionConnection);
                    }
                }
            });
        }
    }

    private void validatePrimaryDatasource(String primaryDatasource) {
        if (primaryDatasource == null) {
            return;
        }
        if (primaryDatasource.trim().isEmpty() || !targets.containsKey(primaryDatasource)) {
            throw new SimpleMysqlRouteException(ErrorCode.PRIMARY_DATASOURCE_INVALID,
                    ErrorMessage.PRIMARY_DATASOURCE_INVALID);
        }
    }

    private String currentDatasource() {
        String datasource = MySqlRouteContextHolder.current();
        if (datasource != null && !datasource.trim().isEmpty()) {
            if (!targets.containsKey(datasource)) {
                throw new SimpleMysqlRouteException(ErrorCode.CONTEXT_INVALID, ErrorMessage.CONTEXT_INVALID);
            }
            return datasource;
        }
        if (primaryDatasource != null) {
            return primaryDatasource;
        }
        throw new SimpleMysqlRouteException(ErrorCode.CONTEXT_INVALID, ErrorMessage.CONTEXT_INVALID);
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
