package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory;

import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterServiceDeclaration;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * IAM 用户目录适配件：USER 维度的目录对象从身份系统实时检索，其余调用转发被装饰的目录实现
 *
 * <p>设计约定：服务清单与资源声明是限流部署事实，由被装饰实现（管理件配置式或其他自有实现）回答；
 * 仅 USER 维度的对象检索改走 IAM openapi（关键字过滤、页大小取 limit；USER 是全局人员维度，
 * serviceCode 参数不参与过滤）。IAM 调用失败按透传语义上抛，不以空列表伪装"无对象"；
 * 稳定 ID 用公开主体 subjectId（IAM 1.3.5 起 users 族响应回传），缺失该字段的响应条目
 * 跳过并告警，不以登录名冒充。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class IamUserSmartRedisLimiterDirectoryProvider implements SmartRedisLimiterDirectoryProvider {

    private final SmartRedisLimiterDirectoryProvider delegate;

    private final IamUserClient userClient;

    /**
     * 构造 IAM 用户目录适配件
     *
     * @param delegate   被装饰的目录实现（服务清单与非 USER 维度的数据源）
     * @param userClient IAM 用户契约客户端
     */
    public IamUserSmartRedisLimiterDirectoryProvider(SmartRedisLimiterDirectoryProvider delegate,
                                                     IamUserClient userClient) {
        this.delegate = delegate;
        this.userClient = userClient;
    }

    @Override
    public List<SmartRedisLimiterServiceDeclaration> listServices() {
        return delegate.listServices();
    }

    @Override
    public SmartRedisLimiterServiceDeclaration findService(String serviceCode) {
        return delegate.findService(serviceCode);
    }

    @Override
    public List<SmartRedisLimiterDirectoryObject> listObjects(String serviceCode,
                                                              String dimension,
                                                              String customType,
                                                              String keyword,
                                                              int limit) {
        if (!SmartRedisLimiterDataDimension.USER.name().equals(dimension)) {
            return delegate.listObjects(serviceCode, dimension, customType, keyword, limit);
        }
        if (limit <= 0) {
            return Collections.emptyList();
        }
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }
        try {
            IamSpringPage<IamUser> page = userClient.listUsers(null, null, normalizedKeyword, 0, limit);
            List<SmartRedisLimiterDirectoryObject> results = new ArrayList<>();
            for (IamUser user : page.getContent()) {
                // 稳定 ID 用公开主体 subjectId，与运行端令牌主体形态对位；
                // 缺失（旧版 IAM 响应）跳过并告警，不以登录名冒充稳定 ID
                if (user.getSubjectId() == null || user.getSubjectId().isEmpty()) {
                    log.warn("IAM 用户条目缺少 subjectId，跳过：username={}", user.getUsername());
                    continue;
                }
                // 显示名缺失时如实回显登录名，不编造归属
                String name = user.getDisplayName() == null || user.getDisplayName().isEmpty()
                        ? user.getUsername() : user.getDisplayName();
                results.add(new SmartRedisLimiterDirectoryObject(
                        SmartRedisLimiterDataDimension.USER.name(), "", user.getSubjectId(), name));
            }
            log.debug("USER 维度目录经 IAM 检索：keyword={}, limit={}, 命中 {} 条",
                    normalizedKeyword, limit, results.size());
            return results;
        } catch (RuntimeException exception) {
            log.warn("USER 维度目录 IAM 检索失败，按透传语义上抛：keyword={}, limit={}",
                    normalizedKeyword, limit, exception);
            throw exception;
        }
    }
}
