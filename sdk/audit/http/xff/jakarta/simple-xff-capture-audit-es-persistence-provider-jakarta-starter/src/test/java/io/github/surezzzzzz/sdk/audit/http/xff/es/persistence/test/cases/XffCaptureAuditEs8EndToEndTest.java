package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.XffCaptureAuditEsProviderTestApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

/**
 * 独立 Elasticsearch 8.17.0 链路验收。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = XffCaptureAuditEsProviderTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "io.github.surezzzzzz.sdk.elasticsearch.route.sources.primary.urls=http://localhost:19280",
                "io.github.surezzzzzz.sdk.elasticsearch.route.sources.primary.server-version=8.17.0"
        })
@Import({XffCaptureAuditEsProviderE2eBase.TestController.class,
        XffCaptureAuditEsProviderE2eBase.TestFilterConfiguration.class})
class XffCaptureAuditEs8EndToEndTest extends XffCaptureAuditEsProviderE2eBase {

    private static final String TEST_INDEX = "xff-capture-audit-jakarta-test-" + UUID.randomUUID();

    @DynamicPropertySource
    static void testIndex(DynamicPropertyRegistry registry) {
        registry.add("xff.audit.test.index", () -> TEST_INDEX);
    }

    @Override
    protected String expectedElasticsearchVersion() {
        return "8.17.0";
    }
}
