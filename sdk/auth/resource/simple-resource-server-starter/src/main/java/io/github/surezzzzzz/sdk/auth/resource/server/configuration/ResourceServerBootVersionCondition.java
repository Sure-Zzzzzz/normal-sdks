package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import org.springframework.boot.SpringBootVersion;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Spring Boot版本条件。
 *
 * @author surezzzzzz
 */
public class ResourceServerBootVersionCondition implements Condition {

    private final boolean legacy;

    /**
     * 创建Spring Boot版本条件。
     *
     * @param legacy 是否为旧版配置条件
     */
    protected ResourceServerBootVersionCondition(boolean legacy) {
        this.legacy = legacy;
    }

    /**
     * 判断当前Spring Boot版本是否匹配。
     *
     * @param context  条件上下文
     * @param metadata 注解元数据
     * @return 是否匹配
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String version = SpringBootVersion.getVersion();
        if (version == null || version.trim().isEmpty()) {
            return false;
        }
        boolean legacyVersion = version.startsWith("2.2.") || version.startsWith("2.3.");
        boolean modernVersion = version.startsWith("2.4.") || version.startsWith("2.5.")
                || version.startsWith("2.6.") || version.startsWith("2.7.");
        return legacy ? legacyVersion : modernVersion;
    }

    /**
     * 2.2/2.3版本条件。
     */
    public static final class Legacy extends ResourceServerBootVersionCondition {

        /**
         * 创建旧版Spring Boot条件。
         */
        public Legacy() {
            super(true);
        }
    }

    /**
     * 2.4至2.7版本条件。
     */
    public static final class Modern extends ResourceServerBootVersionCondition {

        /**
         * 创建现代Spring Boot条件。
         */
        public Modern() {
            super(false);
        }
    }
}
