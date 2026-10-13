package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory;

import io.github.surezzzzzz.sdk.iam.client.IamApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.annotation.SmartRedisLimiterManagementIamDirectoryComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 目录提供方装饰注册器：容器内每个目录提供方 Bean 初始化后包装为 IAM 目录适配件（双层）
 *
 * <p>adaptor 形态（引用即装配）：不引入让位与开关——无论容器里的目录实现是管理件配置式
 * 还是宿主自有实现，一律装饰。内层 USER 目录适配件（USER 维度对象走 IAM openapi，其余
 * 转发原实现）；外层应用目录适配件（服务清单身份以 IAM 可信应用注册表为权威，本地声明
 * 为完整兜底，DESIGN.1.1.0）。两装饰层职责正交（对象检索 vs 服务清单）。任一 IAM 契约
 * 客户端缺失时在首个目录 Bean 初始化阶段响亮失败（getObject 抛出），不静默降级。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterManagementIamDirectoryComponent
public class IamUserSmartRedisLimiterDirectoryBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<IamUserClient> userClientProvider;

    private final ObjectProvider<IamApplicationClient> applicationClientProvider;

    /**
     * 构造装饰注册器
     *
     * @param userClientProvider        IAM 用户契约客户端提供方（延迟取，缺失时装饰阶段响亮失败）
     * @param applicationClientProvider IAM 应用目录契约客户端提供方（延迟取，缺失时装饰阶段响亮失败）
     */
    public IamUserSmartRedisLimiterDirectoryBeanPostProcessor(ObjectProvider<IamUserClient> userClientProvider,
                                                              ObjectProvider<IamApplicationClient> applicationClientProvider) {
        this.userClientProvider = userClientProvider;
        this.applicationClientProvider = applicationClientProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof SmartRedisLimiterDirectoryProvider)
                || bean instanceof IamApplicationSmartRedisLimiterDirectoryProvider) {
            return bean;
        }
        IamUserClient userClient = userClientProvider.getObject();
        IamApplicationClient applicationClient = applicationClientProvider.getObject();
        log.info("目录提供方 Bean 已装饰为 IAM 目录适配件（应用清单权威 + USER 目录）：beanName={}", beanName);
        IamUserSmartRedisLimiterDirectoryProvider userLayer =
                new IamUserSmartRedisLimiterDirectoryProvider((SmartRedisLimiterDirectoryProvider) bean, userClient);
        return new IamApplicationSmartRedisLimiterDirectoryProvider(
                userLayer, new IamApplicationRegistrySnapshot(applicationClient));
    }
}
