package io.github.surezzzzzz.sdk.oss.s3.strategy;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;

/**
 * Jackson 属性命名策略：首字母大写
 * 用于 PolicyDocument 序列化时 key 首字母大写
 */
public class CapitalizeNamingStrategy extends PropertyNamingStrategy {

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * 字段命名：首字母大写
     */
    @Override
    public String nameForField(MapperConfig<?> config, AnnotatedField field, String defaultName) {
        return capitalize(defaultName);
    }

    /**
     * Getter 方法命名：首字母大写
     */
    @Override
    public String nameForGetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
        return capitalize(defaultName);
    }

    /**
     * Setter 方法命名：首字母大写
     */
    @Override
    public String nameForSetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
        return capitalize(defaultName);
    }

    /**
     * 构造器参数命名：首字母大写
     */
    @Override
    public String nameForConstructorParameter(MapperConfig<?> config, AnnotatedParameter ctorParam, String defaultName) {
        return capitalize(defaultName);
    }
}