package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.*;

/**
 * 携带 userId 的 UserDetails 实现
 *
 * <p>自持全部字段而非继承 Spring Security 的 {@code User}：分布式部署下
 * SPRING_SECURITY_CONTEXT 随 HttpSession 外置到 Redis（JDK 序列化），
 * {@code User} 未实现 Serializable 会阻断整个登录态序列化，且其字段无法
 * 通过子类补声明找回。字段名与 AuthorizationServerConfiguration 中
 * IamUserDetailsSupportMixin 的 JSON 契约逐一对齐，存量授权 attributes
 * 反序列化不受重构影响。</p>
 *
 * @author surezzzzzz
 */
public class IamUserDetailsSupport implements UserDetails, CredentialsContainer, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long userId;

    private final String username;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean credentialsNonExpired;
    private final boolean accountNonLocked;
    private final Set<GrantedAuthority> authorities;
    private String password;

    private IamUserDetailsSupport(Long userId, String username, String password,
                                  boolean enabled, boolean accountNonExpired,
                                  boolean credentialsNonExpired, boolean accountNonLocked,
                                  Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.accountNonExpired = accountNonExpired;
        this.credentialsNonExpired = credentialsNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.authorities = Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
    }

    /**
     * 以用户实体 + 角色编码构造登录主体
     */
    public static IamUserDetailsSupport of(IamUserEntity user,
                                           Collection<? extends GrantedAuthority> authorities) {
        boolean enabled = user.getStatus() == null || user.getStatus() == SimpleIamServerConstant.STATUS_ACTIVE;
        boolean accountNonLocked = user.getLockedUntil() == null
                || user.getLockedUntil().isBefore(java.time.Instant.now());
        return new IamUserDetailsSupport(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                enabled,
                true,
                true,
                accountNonLocked,
                authorities
        );
    }

    /**
     * IAM 用户 ID（token sub 的来源）
     */
    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 密码哈希（UserDetails 契约）
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * 用户名（UserDetails 契约）
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * UserDetails 契约：账号未过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    /**
     * UserDetails 契约：账号未锁定（锁定事实由登录失败策略前置拦截）
     */
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    /**
     * UserDetails 契约：凭据未过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    /**
     * UserDetails 契约：是否启用（禁用用户直接拒绝）
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 认证完成后擦除密码引用（UserDetails 契约）
     */
    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    /**
     * 相等性比较（集合 / 缓存场景）
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IamUserDetailsSupport)) {
            return false;
        }
        return Objects.equals(userId, ((IamUserDetailsSupport) o).userId);
    }

    /**
     * 哈希值（与 equals 配对）
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}
