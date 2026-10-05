package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchAutoConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.AesGcmCursorTokenCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.model.CursorState;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.JacksonSearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 独立核验游标认证、部分结果拒绝和自动配置边界。
 */
@Slf4j
class SearchContractTest {
    @Test
    void disabledConfigurationDoesNotCreateBusinessBeans() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SimpleElasticsearchSearchAutoConfiguration.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(0, context.getBeansOfType(SearchPayloadCodec.class).size());
                });
    }

    @Test
    void enabledWithoutRouteFailsInsteadOfDirectConnection() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SimpleElasticsearchSearchAutoConfiguration.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.elasticsearch.search.enable=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejectsTimeoutAndPartialShards() {
        DefaultSearchResponseParser parser = new DefaultSearchResponseParser(new SensitiveFieldProcessor(), new SimpleElasticsearchSearchProperties());
        assertThrows(RuntimeException.class, () -> parser.complete(Map.of("timed_out", true, "_shards", Map.of("failed", 0))));
        assertThrows(RuntimeException.class, () -> parser.complete(Map.of("timed_out", false, "_shards", Map.of("failed", 1))));
        assertDoesNotThrow(() -> parser.complete(Map.of("timed_out", false, "_shards", Map.of("failed", 0))));
    }

    @Test
    void jsonBoundaryRejectsMalformedObjects() {
        SearchPayloadCodec codec = new JacksonSearchPayloadCodec();
        assertEquals(Map.of("amount", 1), codec.decode(codec.encode(Map.of("amount", 1))));
        assertThrows(RuntimeException.class, () -> codec.decode("not-json"));
        assertThrows(RuntimeException.class, () -> codec.decode("[]"));
    }

    @Test
    void cursorAuthenticationExpiryAndSharedKeyContract() {
        SimpleElasticsearchSearchProperties properties = new SimpleElasticsearchSearchProperties();
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        properties.getCursor().setEncryptionKey(Base64.getEncoder().encodeToString(key));
        AesGcmCursorTokenCodec codec = new AesGcmCursorTokenCodec(properties, new JacksonSearchPayloadCodec());
        CursorState state = CursorState.builder().kind("pit").rawId("private-context").datasource("sample")
                .identifier("record").indices(new String[]{"sample-record"}).requestHash("request-fingerprint")
                .expiresAt(System.currentTimeMillis() + 60000).build();
        String token = codec.encode(state);
        assertNotEquals(token, codec.encode(state), "每次编码使用独立 IV");
        assertFalse(token.contains("private-context"));
        assertEquals("private-context", codec.decode(token, "pit", false).getRawId());
        assertThrows(RuntimeException.class, () -> codec.decode(token, "scroll", false));
        String tampered = (token.charAt(0) == 'A' ? "B" : "A") + token.substring(1);
        assertThrows(RuntimeException.class, () -> codec.decode(tampered, "pit", false));
        state.setExpiresAt(System.currentTimeMillis() - 1000);
        String expired = codec.encode(state);
        assertThrows(RuntimeException.class, () -> codec.decode(expired, "pit", false));
        assertEquals("private-context", codec.decode(expired, "pit", true).getRawId());
        properties.getCursor().setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        assertThrows(RuntimeException.class, () -> codec.decode(token, "pit", false));
    }

    @Test
    void nestedObjectsAndArraysAreProtectedWithoutMutatingInput() {
        SimpleElasticsearchSearchProperties.IndexConfig config = new SimpleElasticsearchSearchProperties.IndexConfig();
        SimpleElasticsearchSearchProperties.SensitiveFieldConfig forbidden = new SimpleElasticsearchSearchProperties.SensitiveFieldConfig();
        forbidden.setField("profile.secret");
        forbidden.setStrategy("forbidden");
        SimpleElasticsearchSearchProperties.SensitiveFieldConfig mask = new SimpleElasticsearchSearchProperties.SensitiveFieldConfig();
        mask.setField("items.contact");
        mask.setStrategy("mask");
        config.setSensitiveFields(List.of(forbidden, mask));
        Map<String, Object> source = Map.of("profile", Map.of("secret", "original-secret", "status", "ready"),
                "items", List.of(Map.of("contact", "x"), Map.of("contact", 123)));
        Map<String, Object> result = new SensitiveFieldProcessor().protect(config, source);
        assertFalse(((Map<?, ?>) result.get("profile")).containsKey("secret"));
        for (Object item : (List<?>) result.get("items")) assertEquals("****", ((Map<?, ?>) item).get("contact"));
        assertEquals("original-secret", ((Map<?, ?>) source.get("profile")).get("secret"));
        assertEquals("x", ((Map<?, ?>) ((List<?>) source.get("items")).get(0)).get("contact"));
    }
}
