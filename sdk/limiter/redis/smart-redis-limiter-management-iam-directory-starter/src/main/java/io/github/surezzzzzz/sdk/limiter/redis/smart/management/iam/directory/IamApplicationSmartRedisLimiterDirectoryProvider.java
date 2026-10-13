package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory;

import io.github.surezzzzzz.sdk.iam.client.model.IamApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterServiceControlMode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterServiceDeclaration;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * IAM 应用目录适配件：服务清单的身份权威化装饰（DESIGN.1.1.0 聚形态）
 *
 * <p>正常运行时服务清单 = IAM 可信应用注册表启用应用全量（运营可见"哪些应用还没接入限流"）；
 * displayName 以注册表 application_name 为权威；yml 声明保持完整形态作为本地完整兜底——
 * 注册表连通类故障时输出与散形态完全等价（副本是兜底，不是第二权威：出现与否、
 * 叫什么名只看注册表）。注册表停用/未登记的声明应用不出现并 WARN；未声明的注册表应用
 * 出现但资源列表为空（页面既有空态文案接得住，不可新建）。对象检索转发被装饰实现
 * （USER 维度由内层适配件回答）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class IamApplicationSmartRedisLimiterDirectoryProvider implements SmartRedisLimiterDirectoryProvider {

    /**
     * 注册表启用状态值（iam_trusted_application.status 1=启用）
     */
    private static final int STATUS_ENABLED = 1;

    private final SmartRedisLimiterDirectoryProvider delegate;

    private final IamApplicationRegistrySnapshot registry;

    /**
     * 构造应用目录适配件
     *
     * @param delegate 被装饰的目录实现（本地完整声明的兜底数据源）
     * @param registry IAM 可信应用注册表快照
     */
    public IamApplicationSmartRedisLimiterDirectoryProvider(SmartRedisLimiterDirectoryProvider delegate,
                                                            IamApplicationRegistrySnapshot registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public List<SmartRedisLimiterServiceDeclaration> listServices() {
        List<IamApplication> applications = registry.current();
        if (applications == null) {
            // 连通类故障：本地完整兜底，输出与散形态完全等价
            return delegate.listServices();
        }
        Map<String, SmartRedisLimiterServiceDeclaration> declaredByCode = new LinkedHashMap<>();
        for (SmartRedisLimiterServiceDeclaration declaration : delegate.listServices()) {
            declaredByCode.put(declaration.getServiceCode(), declaration);
        }
        Set<String> hidden = new HashSet<>(declaredByCode.keySet());
        List<SmartRedisLimiterServiceDeclaration> result = new ArrayList<>(applications.size());
        for (IamApplication application : applications) {
            if (application.getStatus() == null || application.getStatus() != STATUS_ENABLED) {
                continue;
            }
            hidden.remove(application.getApplicationCode());
            SmartRedisLimiterServiceDeclaration declared = declaredByCode.get(application.getApplicationCode());
            result.add(declared == null
                    ? unannotated(application)
                    : withRegistryIdentity(declared, application));
        }
        if (!hidden.isEmpty()) {
            log.warn("本地声明应用未在 IAM 注册表启用名单中，本轮隐藏（请登记或删除声明）：{}", hidden);
        }
        return result;
    }

    @Override
    public SmartRedisLimiterServiceDeclaration findService(String serviceCode) {
        List<IamApplication> applications = registry.current();
        if (applications == null) {
            return delegate.findService(serviceCode);
        }
        IamApplication matched = null;
        boolean disabled = false;
        for (IamApplication application : applications) {
            if (application.getApplicationCode() != null && application.getApplicationCode().equals(serviceCode)) {
                matched = application;
                disabled = application.getStatus() == null || application.getStatus() != STATUS_ENABLED;
                break;
            }
        }
        if (matched == null) {
            return null;
        }
        if (disabled) {
            log.warn("服务在 IAM 注册表已停用，类型化目录隐藏：serviceCode={}", serviceCode);
            return null;
        }
        SmartRedisLimiterServiceDeclaration declared = delegate.findService(serviceCode);
        return declared == null ? unannotated(matched) : withRegistryIdentity(declared, matched);
    }

    @Override
    public List<SmartRedisLimiterDirectoryObject> listObjects(String serviceCode,
                                                              String dimension,
                                                              String customType,
                                                              String keyword,
                                                              int limit) {
        // 对象目录不在本装饰层职责内：USER 维度由内层 IAM 用户适配件回答，其余转发本地
        return delegate.listObjects(serviceCode, dimension, customType, keyword, limit);
    }

    /**
     * 未声明限流语义的注册表应用：身份来自注册表，资源列表空（页面资源下拉既有空态，不可新建）
     */
    private SmartRedisLimiterServiceDeclaration unannotated(IamApplication application) {
        SmartRedisLimiterServiceDeclaration declaration = new SmartRedisLimiterServiceDeclaration();
        declaration.setServiceCode(application.getApplicationCode());
        declaration.setControlMode(SmartRedisLimiterServiceControlMode.TYPED_V2.name());
        declaration.setDisplayName(application.getApplicationName());
        return declaration;
    }

    /**
     * 已声明应用：限流语义（资源/维度/命名空间/自定义类型/代次）原样保留，仅身份换成注册表权威值
     */
    private SmartRedisLimiterServiceDeclaration withRegistryIdentity(SmartRedisLimiterServiceDeclaration declared,
                                                                     IamApplication application) {
        SmartRedisLimiterServiceDeclaration decorated = new SmartRedisLimiterServiceDeclaration();
        decorated.setServiceCode(declared.getServiceCode());
        decorated.setControlMode(declared.getControlMode());
        decorated.setPolicyEpoch(declared.getPolicyEpoch());
        decorated.setDisplayName(application.getApplicationName());
        decorated.setResources(declared.getResources() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(declared.getResources()));
        decorated.setNamespaces(declared.getNamespaces() == null
                ? new HashMap<>() : new HashMap<>(declared.getNamespaces()));
        decorated.setCustomTypes(declared.getCustomTypes() == null
                ? new ArrayList<>() : new ArrayList<>(declared.getCustomTypes()));
        return decorated;
    }
}
