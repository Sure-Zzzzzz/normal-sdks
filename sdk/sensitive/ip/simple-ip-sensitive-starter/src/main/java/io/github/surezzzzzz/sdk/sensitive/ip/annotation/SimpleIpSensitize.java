package io.github.surezzzzzz.sdk.sensitive.ip.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.github.surezzzzzz.sdk.sensitive.ip.serializer.SimpleIpSensitizeSerializer;

import java.lang.annotation.*;

/**
 * IP 字段脱敏注解
 *
 * 用于字段上，在 JSON 序列化时自动脱敏
 *
 * @author surezzzzzz
 *
 * @example
 * <pre>
 * &#64;Data
 * public class AccessLog {
 *     &#64;SimpleIpSensitize(mask = {3, 4})
 *     private String clientIp;
 *
 *     &#64;SimpleIpSensitize  // 使用默认策略
 *     private String serverIp;
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = SimpleIpSensitizeSerializer.class)
public @interface SimpleIpSensitize {

    /**
     * 要脱敏的段/组位置（1-based 索引）
     * <p>
     * 默认空数组表示使用配置的默认策略
     * <p>
     * IPv4 示例：
     * - [3, 4] 表示脱敏第3、4段 → "192.168.*.*"
     * - [1, 2] 表示脱敏第1、2段 → "*.*.1.1"
     * <p>
     * IPv6 示例：
     * - [5, 6, 7, 8] 表示脱敏后4组
     */
    int[] mask() default {};

    /**
     * 掩码字符
     * <p>
     * 默认空字符串表示使用配置的默认掩码字符
     * <p>
     * IPv4 默认："*"
     * IPv6 默认："****"
     */
    String maskChar() default "";
}
