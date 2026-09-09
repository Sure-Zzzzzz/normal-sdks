package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.AuthorizationServerConfiguration;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsSupport;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IamUserDetailsSupport 序列化契约守护
 *
 * <p>分布式部署下 SPRING_SECURITY_CONTEXT 随 HttpSession 经 JDK 序列化落 Redis，
 * OAuth2 授权 attributes 经 AuthorizationServerConfiguration 的 Jackson mixin 落 MySQL。
 * 本测试锁定两条序列化通道的字段集与 round-trip 等值，防止后续演进破坏
 * 存量 session / oauth2_authorization.attributes 的反序列化。</p>
 *
 * @author surezzzzzz
 */
class IamUserDetailsSerializationTest {

    @Test
    void jdkSerializationRoundTripKeepsAllFields() throws Exception {
        IamUserDetailsSupport original = sample();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        assertThat(bytes.size()).isPositive();

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            IamUserDetailsSupport restored = (IamUserDetailsSupport) in.readObject();
            assertThat(restored.getUserId()).isEqualTo(42L);
            assertThat(restored.getUsername()).isEqualTo("ops-user");
            assertThat(restored.getPassword()).isEqualTo("bcrypt-hash");
            assertThat(restored.isEnabled()).isTrue();
            assertThat(restored.isAccountNonExpired()).isTrue();
            assertThat(restored.isCredentialsNonExpired()).isTrue();
            assertThat(restored.isAccountNonLocked()).isTrue();
            assertThat(restored.getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly("ROLE_USER", "ROLE_IAM_ADMIN");
        }
    }

    @Test
    void eraseCredentialsClearsPasswordAfterSerialization() throws Exception {
        IamUserDetailsSupport details = sample();
        details.eraseCredentials();
        assertThat(details.getPassword()).isNull();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(details);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(((IamUserDetailsSupport) in.readObject()).getPassword()).isNull();
        }
    }

    @Test
    void jacksonMixinRoundTripKeepsAuthorizationAttributesShape() throws Exception {
        ObjectMapper mapper = authorizationObjectMapper();
        IamUserDetailsSupport original = sample();

        String json = mapper.writeValueAsString(original);
        assertThat(json)
                .contains("\"userId\"").contains("\"username\"").contains("\"password\"")
                .contains("\"enabled\"").contains("\"accountNonExpired\"")
                .contains("\"credentialsNonExpired\"").contains("\"accountNonLocked\"")
                .contains("\"authorities\"");

        IamUserDetailsSupport back = mapper.readValue(mapper.writeValueAsBytes(original),
                IamUserDetailsSupport.class);
        assertThat(back.getUserId()).isEqualTo(original.getUserId());
        assertThat(back.getUsername()).isEqualTo(original.getUsername());
        assertThat(back.getPassword()).isEqualTo(original.getPassword());
        assertThat(back.isEnabled()).isEqualTo(original.isEnabled());
        assertThat(back.isAccountNonLocked()).isEqualTo(original.isAccountNonLocked());
        assertThat(back.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_IAM_ADMIN");
    }

    /**
     * 复刻生产读写两侧 mapper：写侧 = SAS ParametersMapper 内部装配（typing 无 allowlist，
     * Object 位置的 Long 写成 ["java.lang.Long",n] 包装）；读侧 = 生产 authorizationObjectMapper
     * （TrustedSourceTypeResolverBuilder 对称 typing）。锁定 sid / auth_time 等 Long claim 的
     * refresh 读回不再触发 allowlist 异常。
     */
    @Test
    void principalAndLongClaimsSurviveJdbcWriteAndReadMappers() throws Exception {
        ObjectMapper writer = new ObjectMapper();
        SecurityJackson2Modules.enableDefaultTyping(writer);
        writer.registerModule(new OAuth2AuthorizationServerJackson2Module());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                sample(), null, java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put(java.security.Principal.class.getName(), principal);
        attributes.put("sid", 42L);
        attributes.put("auth_time", 1700000000L);

        java.util.Map<String, Object> back = authorizationObjectMapper().readValue(
                writer.writeValueAsBytes(attributes),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                });

        Object sid = back.get("sid");
        assertThat(sid).isEqualTo(42L);
        assertThat(sid.getClass()).isEqualTo(Long.class);
        assertThat(back.get("auth_time")).isEqualTo(1700000000L);
        UsernamePasswordAuthenticationToken restoredPrincipal = (UsernamePasswordAuthenticationToken)
                back.get(java.security.Principal.class.getName());
        assertThat(((IamUserDetailsSupport) restoredPrincipal.getPrincipal()).getUserId()).isEqualTo(42L);
    }

    /**
     * 与 AuthorizationServerConfiguration.authorizationObjectMapper 同款装配；
     * mixin 与 trusted typing builder 类均经反射取真实类，锁定生产装配的 JSON 契约。
     */
    private ObjectMapper authorizationObjectMapper() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(SecurityJackson2Modules.getModules(
                io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsSupport.class
                        .getClassLoader()));
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        Class<?> mixin = nestedClass("IamUserDetailsSupportMixin");
        objectMapper.addMixIn(IamUserDetailsSupport.class, mixin);
        java.lang.reflect.Constructor<?> builderCtor =
                nestedClass("TrustedSourceTypeResolverBuilder").getDeclaredConstructor();
        builderCtor.setAccessible(true);
        objectMapper.setDefaultTyping(
                (com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder<?>) builderCtor.newInstance());
        return objectMapper;
    }

    private Class<?> nestedClass(String simpleName) {
        return Arrays.stream(AuthorizationServerConfiguration.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(simpleName + " 不存在"));
    }

    private IamUserDetailsSupport sample() {
        IamUserEntity user = new IamUserEntity();
        user.setId(42L);
        user.setUsername("ops-user");
        user.setPasswordHash("bcrypt-hash");
        user.setStatus(1);
        return IamUserDetailsSupport.of(user, Arrays.asList(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_IAM_ADMIN")));
    }
}
