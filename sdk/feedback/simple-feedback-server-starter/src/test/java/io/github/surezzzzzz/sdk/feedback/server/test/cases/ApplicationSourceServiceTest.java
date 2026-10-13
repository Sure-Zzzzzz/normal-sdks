package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;
import io.github.surezzzzzz.sdk.feedback.server.service.ApplicationSourceService;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注册表数据来源核对服务单测（修正㉔的降级语义三态）。
 *
 * <p>覆盖三个分支：provider 缺席（散形态）=null→UNKNOWN；provider 出站抛异常=null→UNKNOWN
 * （注册表不锁）；目录在场的 IAM/MANUAL 判定。纯服务级，无 Spring 上下文。</p>
 *
 * @author surezzzzzz
 */
class ApplicationSourceServiceTest {

    private static ObjectProvider<AccessibleApplicationProvider> providerOf(
            final AccessibleApplicationProvider provider) {
        return new ObjectProvider<AccessibleApplicationProvider>() {
            @Override
            public AccessibleApplicationProvider getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AccessibleApplicationProvider getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public AccessibleApplicationProvider getIfAvailable() {
                return provider;
            }

            @Override
            public AccessibleApplicationProvider getIfUnique() {
                return provider;
            }
        };
    }

    @Test
    @DisplayName("散形态：provider 缺席=目录不可核对（null→UNKNOWN，注册表全可编辑）")
    void testNoProvider() {
        ApplicationSourceService service = new ApplicationSourceService(providerOf(null));
        assertNull(service.catalogIndex());
        assertEquals(ApplicationSourceService.SOURCE_UNKNOWN, ApplicationSourceService.sourceOf("kms", null));
    }

    @Test
    @DisplayName("出站异常：降级为不可核对（null→UNKNOWN），不抛出不锁表")
    void testOutboundFailureDegrades() {
        ApplicationSourceService service = new ApplicationSourceService(providerOf(subjectId -> {
            throw new IllegalStateException("stub outbound failure");
        }));
        assertNull(service.catalogIndex());
        assertEquals(ApplicationSourceService.SOURCE_UNKNOWN, ApplicationSourceService.sourceOf("kms", null));
    }

    @Test
    @DisplayName("目录在场：命中=IAM 同源，未命中=本表登记")
    void testCatalogHitAndMiss() {
        AccessibleApplicationProvider catalog = new AccessibleApplicationProvider() {
            @Override
            public java.util.List<AccessibleApplication> accessibleApplications(String subjectId) {
                return Collections.emptyList();
            }

            @Override
            public java.util.List<AccessibleApplication> catalogApplications() {
                return Collections.singletonList(AccessibleApplication.builder()
                        .applicationCode("kms").displayName("KMS 密钥管理").build());
            }
        };
        ApplicationSourceService service = new ApplicationSourceService(providerOf(catalog));
        Map<String, AccessibleApplication> index = service.catalogIndex();
        assertEquals(1, index.size());
        assertTrue(index.containsKey("kms"));
        assertEquals(ApplicationSourceService.SOURCE_IAM, ApplicationSourceService.sourceOf("kms", index));
        assertEquals(ApplicationSourceService.SOURCE_MANUAL, ApplicationSourceService.sourceOf("other", index));
        assertFalse(index.containsKey("other"));
    }
}
