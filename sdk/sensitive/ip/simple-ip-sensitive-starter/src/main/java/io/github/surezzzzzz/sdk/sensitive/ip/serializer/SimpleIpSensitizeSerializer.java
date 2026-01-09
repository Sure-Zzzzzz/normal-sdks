package io.github.surezzzzzz.sdk.sensitive.ip.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import io.github.surezzzzzz.sdk.sensitive.ip.annotation.SimpleIpSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.ip.annotation.SimpleIpSensitize;
import io.github.surezzzzzz.sdk.sensitive.ip.helper.SimpleIpSensitiveHelper;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;

/**
 * IP 脱敏序列化器
 *
 * @author surezzzzzz
 */
@SimpleIpSensitiveComponent
public class SimpleIpSensitizeSerializer extends JsonSerializer<String> implements ContextualSerializer, ApplicationContextAware {

    private static ApplicationContext applicationContext;

    private SimpleIpSensitize annotation;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        try {
            SimpleIpSensitiveHelper helper = applicationContext.getBean(SimpleIpSensitiveHelper.class);

            String masked;
            if (annotation != null) {
                int[] maskPositions = annotation.mask();
                String maskChar = annotation.maskChar();

                if (maskPositions.length > 0 && !maskChar.isEmpty()) {
                    // 指定了位置和掩码字符
                    masked = helper.mask(value, maskPositions, maskChar);
                } else if (maskPositions.length > 0) {
                    // 只指定了位置
                    masked = helper.mask(value, maskPositions);
                } else if (!maskChar.isEmpty()) {
                    // 只指定了掩码字符（使用默认位置）
                    // 这种情况比较少见，暂时使用默认策略
                    masked = helper.mask(value);
                } else {
                    // 使用默认策略
                    masked = helper.mask(value);
                }
            } else {
                // 使用默认策略
                masked = helper.mask(value);
            }

            gen.writeString(masked);
        } catch (Exception e) {
            // 脱敏失败，返回原值（避免影响业务）
            gen.writeString(value);
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property != null) {
            SimpleIpSensitize ann = property.getAnnotation(SimpleIpSensitize.class);
            if (ann != null) {
                SimpleIpSensitizeSerializer serializer = new SimpleIpSensitizeSerializer();
                serializer.annotation = ann;
                return serializer;
            }
        }
        return this;
    }
}
