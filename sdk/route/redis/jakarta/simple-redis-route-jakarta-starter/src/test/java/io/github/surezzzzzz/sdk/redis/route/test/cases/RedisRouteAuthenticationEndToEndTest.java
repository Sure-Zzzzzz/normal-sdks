package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.factory.DefaultRedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.test.SimpleRedisRouteTestApplication;
import io.lettuce.core.AclSetuserArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.ProtocolKeyword;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 7 ACL 认证端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleRedisRouteTestApplication.class)
public class RedisRouteAuthenticationEndToEndTest {

    private static final ProtocolKeyword CLIENT_SETINFO = new ProtocolKeyword() {
        @Override
        public byte[] getBytes() {
            return name().getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public String name() {
            return "SETINFO";
        }
    };

    @Autowired
    private SimpleRedisRouteRegistry registry;

    @Autowired
    private SimpleRedisRouteProperties properties;

    @Test
    public void testAuthenticatedConnectionAcceptsValidPasswordAndRejectsInvalidPassword() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "route_auth_" + suffix;
        String password = UUID.randomUUID().toString().replace("-", "");
        String key = "route-matrix:auth:" + suffix;
        SimpleRedisRouteProperties.DataSourceConfig source = properties.getSources().get("redis7Standalone");
        RedisClient adminClient = RedisClient.create(RedisURI.Builder.redis(source.getHost(), source.getPort()).build());
        StatefulRedisConnection<String, String> adminConnection = null;
        LettuceConnectionFactory validFactory = null;
        LettuceConnectionFactory invalidFactory = null;
        boolean userCreated = false;
        try {
            adminConnection = adminClient.connect();
            // 临时用户仅能访问本测试键；不修改 fixture 的默认用户认证状态。
            // 新版 Lettuce 建连时会发送 CLIENT SETINFO，测试用户仅放行该子命令。
            AclSetuserArgs acl = new AclSetuserArgs().on().addPassword(password).keyPattern(key)
                    .addCommand(CommandType.GET).addCommand(CommandType.SET).addCommand(CommandType.PING)
                    .addCommand(CommandType.CLIENT, CLIENT_SETINFO);
            assertEquals("OK", adminConnection.sync().aclSetuser(username, acl), "临时 ACL 用户应创建成功");
            userCreated = true;

            SimpleRedisRouteProperties.DataSourceConfig valid = authenticatedSource(source, username, password);
            validFactory = (LettuceConnectionFactory) new DefaultRedisConnectionFactoryFactory()
                    .create("authenticated", valid);
            StringRedisTemplate validTemplate = new StringRedisTemplate(validFactory);
            validTemplate.opsForValue().set(key, "authenticated");
            assertEquals("authenticated", validTemplate.opsForValue().get(key),
                    "正确用户名和密码应完成真实 Redis 读写");

            SimpleRedisRouteProperties.DataSourceConfig invalid = authenticatedSource(source, username,
                    UUID.randomUUID().toString().replace("-", ""));
            invalidFactory = (LettuceConnectionFactory) new DefaultRedisConnectionFactoryFactory()
                    .create("invalid-authentication", invalid);
            StringRedisTemplate invalidTemplate = new StringRedisTemplate(invalidFactory);
            RuntimeException rejected = assertThrows(RuntimeException.class,
                    () -> invalidTemplate.opsForValue().get(key), "错误密码必须由 Redis 服务端拒绝");
            assertTrue(isAuthenticationRejection(rejected), "拒绝原因应为 Redis 认证失败");
            log.info("Redis 7 ACL 正确认证与错误密码拒绝验证完成");
        } finally {
            if (invalidFactory != null) {
                invalidFactory.destroy();
            }
            if (validFactory != null) {
                validFactory.destroy();
            }
            try {
                if (userCreated) {
                    try {
                        registry.getStringRedisTemplate("redis7Standalone").delete(key);
                    } finally {
                        assertEquals(1L, adminConnection.sync().aclDeluser(username), "临时 ACL 用户应清理成功");
                    }
                }
            } finally {
                if (adminConnection != null) {
                    adminConnection.close();
                }
                adminClient.shutdown();
            }
        }
    }

    private SimpleRedisRouteProperties.DataSourceConfig authenticatedSource(
            SimpleRedisRouteProperties.DataSourceConfig source, String username, String password) {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setHost(source.getHost());
        config.setPort(source.getPort());
        config.setDatabase(source.getDatabase());
        config.setUsername(username);
        config.setPassword(password);
        return config;
    }

    private boolean isAuthenticationRejection(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && (message.contains("WRONGPASS") || message.contains("NOAUTH"))) {
                return true;
            }
        }
        return false;
    }
}
