package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.EsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditUserProvider;
import lombok.Getter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 使用正式自动配置发现机制的最小测试宿主，不连接任何认证服务。
 */
@SpringBootApplication
public class AuditTestApplication {
    /**
     * 内存处理器用于独立核验异步输出。
     */
    @Bean
    public Collector collector() {
        return new Collector();
    }

    /**
     * 请求线程主体，不把主体自动传播到上游持久化异步线程。
     */
    @Bean
    public Users users() {
        return new Users();
    }

    /**
     * 在事件发布线程读取追踪标识。
     */
    @Bean
    public EsPersistenceAuditTraceIdProvider traces(Users users) {
        return () -> users.current.get();
    }

    /**
     * 可观察的审计处理器。
     */
    @Getter
    public static class Collector implements EsPersistenceAuditHandler {
        private final BlockingQueue<EsPersistenceAuditRecord> records = new LinkedBlockingQueue<>();
        private volatile String threadName;

        /**
         * 保存监听器交付的记录。
         */
        @Override
        public void handle(EsPersistenceAuditRecord record) {
            threadName = Thread.currentThread().getName();
            records.add(record);
        }
    }

    /**
     * 宿主已有身份信息的适配器。
     */
    public static class Users implements EsPersistenceAuditUserProvider {
        public final ThreadLocal<String> current = new ThreadLocal<>();

        /**
         * 返回客户端标识。
         */
        @Override
        public String getClientId() {
            return current.get();
        }

        /**
         * 返回客户端类型。
         */
        @Override
        public String getClientType() {
            return current.get() == null ? null : "sample";
        }

        /**
         * 返回用户标识。
         */
        @Override
        public String getUserId() {
            return current.get();
        }

        /**
         * 返回用户名。
         */
        @Override
        public String getUsername() {
            return current.get();
        }
    }
}
