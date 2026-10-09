package io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant.SimpleAkskOpenApiClientConstant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 契约形状测试：与 core {@code AkskOpenApiClient} 方法面一一对应断言（防两线漂移的锚）
 * + 注解元数据断言（URL 引底座 server-url、path=/api、端点 Mapping 注解正确）。
 *
 * <p>Spring 装配与真实调用的行为由底座（simple-aksk-feign-redis-client-jakarta-starter）的
 * 18 项测试覆盖；本模块契约不漂移即等价于传输行为正确。</p>
 *
 * @author surezzzzzz
 */
class AkskOpenApiFeignClientContractTest {

    @Test
    void methodSurfaceMatchesCoreInterface() {
        String[] coreMethods = Arrays.stream(
                        io.github.surezzzzzz.sdk.auth.aksk.openapi.client.AkskOpenApiClient.class.getMethods())
                .map(Method::getName)
                .sorted()
                .toArray(String[]::new);
        String[] feignMethods = Arrays.stream(AkskOpenApiFeignClient.class.getMethods())
                .map(Method::getName)
                .sorted()
                .toArray(String[]::new);
        assertThat(feignMethods).containsExactlyInAnyOrder(coreMethods);
        assertThat(feignMethods).hasSize(21); // 20 端点，listClients 分页/批量双形态
    }

    @Test
    void clientAnnotationReferencesBaseServerUrl() {
        // @AkskClientFeignClient 是 @FeignClient 的元注解组合——运行时经 Spring 注解工具取原注解属性
        org.springframework.cloud.openfeign.FeignClient feignMeta =
                org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                        AkskOpenApiFeignClient.class,
                        org.springframework.cloud.openfeign.FeignClient.class);
        assertThat(feignMeta).isNotNull();
        assertThat(feignMeta.url()).isEqualTo(SimpleAkskOpenApiClientConstant.SERVER_URL_PLACEHOLDER);
        assertThat(feignMeta.name()).isEqualTo("aksk-openapi");

        io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient akskMeta =
                AkskOpenApiFeignClient.class.getAnnotation(
                        io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient.class);
        assertThat(akskMeta).isNotNull();
    }

    @Test
    void feignPathAttributeIsApiBase() {
        // path 是 @FeignClient（经 @AkskClientFeignClient 组合）的属性，非类级 @RequestMapping
        org.springframework.cloud.openfeign.FeignClient feignMeta =
                org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                        AkskOpenApiFeignClient.class,
                        org.springframework.cloud.openfeign.FeignClient.class);
        assertThat(feignMeta).isNotNull();
        assertThat(feignMeta.path()).isEqualTo(SimpleAkskOpenApiClientConstant.API_BASE_PATH);
        assertThat(feignMeta.path()).isEqualTo("/api");
    }

    @Test
    void writeEndpointsCarryCorrectMappingAnnotations() throws Exception {
        // 20 端点逐个断言 HTTP 方法与子路径（抓关键端点做锚，防 mapping 改错）
        Method create = AkskOpenApiFeignClient.class.getMethod("createClient",
                io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.CreateClientRequest.class);
        assertThat(create.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class))
                .isNotNull();

        Method revokeAuth = AkskOpenApiFeignClient.class.getMethod("revokeAuthorization", String.class);
        assertThat(revokeAuth.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class))
                .isNotNull();

        Method stats = AkskOpenApiFeignClient.class.getMethod("getTokenStatistics");
        assertThat(stats.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class))
                .isNotNull();

        Method batchRevoke = AkskOpenApiFeignClient.class.getMethod("revokeTokensByClientId", String.class);
        assertThat(batchRevoke.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class))
                .isNotNull();

        Method rotate = AkskOpenApiFeignClient.class.getMethod("rotateSecret", String.class, boolean.class);
        assertThat(rotate.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class))
                .isNotNull();

        Method patch = AkskOpenApiFeignClient.class.getMethod("updateClient", String.class,
                io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.UpdateClientRequest.class);
        assertThat(patch.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class))
                .isNotNull();
    }
}
