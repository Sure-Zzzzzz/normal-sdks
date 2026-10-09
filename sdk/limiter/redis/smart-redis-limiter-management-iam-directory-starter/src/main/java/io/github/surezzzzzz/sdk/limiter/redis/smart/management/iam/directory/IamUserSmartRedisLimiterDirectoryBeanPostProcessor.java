package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory;

import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.annotation.SmartRedisLimiterManagementIamDirectoryComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 目录提供方装饰注册器：容器内每个目录提供方 Bean 初始化后包装为 IAM 用户目录适配件
 *
 * <p>adaptor 形态（引用即装配）：不引入让位与开关——无论容器里的目录实现是管理件配置式
 * 还是宿主自有实现，一律装饰（USER 维度改走 IAM，其余转发原实现）。IamUserClient 缺失时
 * 在首个目录 Bean 初始化阶段响亮失败（getObject 抛出），不静默降级。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterManagementIamDirectoryComponent
public class IamUserSmartRedisLimiterDirectoryBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<IamUserClient> userClientProvider;

    /**
     * 构造装饰注册器
     *
     * @param userClientProvider IAM 用户契约客户端提供方（延迟取，缺失时装饰阶段响亮失败）
     */
    public IamUserSmartRedisLimiterDirectoryBeanPostProcessor(ObjectProvider<IamUserClient> userClientProvider) {
        this.userClientProvider = userClientProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof SmartRedisLimiterDirectoryProvider) || bean instanceof IamUserSmartRedisLimiterDirectoryProvider) {
            return bean;
        }
        IamUserClient userClient = userClientProvider.getObject();
        log.info("目录提供方 Bean 已装饰为 IAM 用户目录适配件：beanName={}", beanName);
        return new IamUserSmartRedisLimiterDirectoryProvider((SmartRedisLimiterDirectoryProvider) bean, userClient);
    }
}
