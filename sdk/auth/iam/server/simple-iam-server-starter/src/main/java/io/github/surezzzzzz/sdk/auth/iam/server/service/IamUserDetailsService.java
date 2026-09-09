package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM UserDetailsService：供 Spring Security Password Grant 用
 *
 * <p>返回的 UserDetails 携带 userId，JWT customizer 从 Principal attributes 取。
 * authorities = ROLE_ 前缀角色码 + 用户跨角色聚合的权限码（iam:xxx:api / iam:xxx:page），
 * 供方法级 @PreAuthorize 与前端菜单过滤消费。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamUserDetailsService implements UserDetailsService {

    private final IamUserRepository userRepository;
    private final RoleService roleService;

    /**
     * Spring Security 桥接：按用户名装载登录主体（含角色 authorities）
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        IamUserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format(ServerErrorMessage.USER_NOT_FOUND, username)));

        List<IamRoleEntity> roles = roleService.getUserRoles(user.getId());
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (IamRoleEntity role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
        }
        for (String permissionCode : roleService.getUserPermissionCodes(user.getId())) {
            authorities.add(new SimpleGrantedAuthority(permissionCode));
        }

        return IamUserDetailsSupport.of(user, authorities);
    }
}
