package io.github.surezzzzzz.sdk.auth.iam.server.configuration;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * spring-session Redis HttpSession 装配
 *
 * <p>多实例部署默认开启：HttpSession 落 Redis（redis-route 的唯一 {@code @Primary}
 * 连接工厂即 default 数据源），任意实例互认会话。单实例同样开启，无粘性路由前提；
 * 不提供回退容器内存 session 的开关。</p>
 *
 * <p>装配边界：
 * <ul>
 * <li>不新增任何 RedisConnectionFactory / RedisTemplate bean（redis-route 校验器
 * REDIS_ROUTE_015/016 禁止），连接工厂复用 redis-route 发布的唯一 @Primary；</li>
 * <li>经 {@code @AutoConfigureBefore(SessionAutoConfiguration.class)} 抢在 Boot 的
 * SessionAutoConfiguration 之前，Boot 侧 {@code @ConditionalOnMissingBean(SessionRepository)}
 * 自行退避；</li>
 * <li>测试不关闸：MockMvc 夹具以 JSESSIONID cookie 跨请求传会话（与浏览器一致），
 * 会话属性每请求真走 Redis 序列化往返。</li>
 * </ul></p>
 *
 * <p>namespace 占位符由 spring-session 2.7 的 EmbeddedValueResolverAware 解析
 * （setImportMetadata 阶段），与 redis 侧 key 规范 {@code sure-auth-iam:{businessType}:{me}}
 * 对齐：多实例须共享同一 me 才能互认会话。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisIndexedSessionRepository.class)
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = SimpleIamServerConstant.DEFAULT_SESSION_EXPIRES_IN,
        redisNamespace = SimpleIamServerConstant.SESSION_REDIS_NAMESPACE_DEFAULT)
public class IamRedisHttpSessionConfiguration implements InitializingBean {

    /**
     * 启动期播报生效的 namespace 与匿名会话超时基线，辅助多实例部署排查
     */
    @Override
    public void afterPropertiesSet() {
        log.info("spring-session Redis 会话存储已装配：namespace={}, maxInactiveInterval={}s（HttpSession 全局走 Redis，无容器内存退路）",
                SimpleIamServerConstant.SESSION_REDIS_NAMESPACE_DEFAULT,
                SimpleIamServerConstant.DEFAULT_SESSION_EXPIRES_IN);
    }

    /**
     * 托管 Redis 禁止 CONFIG 命令时的逃生口：跳过 keyspace notifications 探测。
     */
    @Bean
    @ConditionalOnProperty(prefix = SimpleIamServerConstant.CONFIG_PREFIX,
            name = "session.redis-configure-action", havingValue = "noop")
    /**
     * 托管 Redis 禁用 CONFIG 命令时跳过 keyspace notifications 探测（noop 逃生口）
     */
    public ConfigureRedisAction iamSessionConfigureRedisNoOp() {
        return ConfigureRedisAction.NO_OP;
    }

    /**
     * spring-session 默认 cookie 名为 SESSION，此处保持容器时代的 JSESSIONID，
     * 前端 / 网关的会话 cookie 契约不变；宿主自定义 CookieSerializer 时退避。
     */
    @Bean
    @ConditionalOnMissingBean(CookieSerializer.class)
    public CookieSerializer iamSessionCookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        return serializer;
    }
}
