package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.support.OpenApiRequestFingerprint;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 机器开单摘要的字段与键序契约。
 */
@Slf4j
class OpenApiRequestFingerprintTest {

    @Test
    void mapOrderDoesNotChangeFingerprintButBodyChangesDo() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", "two");
        first.put("a", "one");
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("a", "one");
        reordered.put("b", "two");
        String hash = OpenApiRequestFingerprint.of("fault", "标题", "描述",
                TicketPriority.NORMAL, "person", first);
        log.debug("核对请求指纹契约：fieldCount={} fingerprintLength={}", first.size(), hash.length());
        assertEquals(64, hash.length());
        assertEquals(hash, OpenApiRequestFingerprint.of("fault", "标题", "描述",
                TicketPriority.NORMAL, "person", reordered));
        reordered.put("b", "changed");
        assertNotEquals(hash, OpenApiRequestFingerprint.of("fault", "标题", "描述",
                TicketPriority.NORMAL, "person", reordered));
    }

    @Test
    void portalFingerprintIncludesRelatedTicketAndAttachments() {
        List<Map<String, Object>> firstAttachments = Collections.singletonList(attachment("ticket/one"));
        List<Map<String, Object>> secondAttachments = Collections.singletonList(attachment("ticket/two"));
        String first = OpenApiRequestFingerprint.portal("incident", "subject", "description",
                TicketPriority.NORMAL, "person-1", Collections.emptyMap(), "TK-1", firstAttachments);
        String relatedChanged = OpenApiRequestFingerprint.portal("incident", "subject", "description",
                TicketPriority.NORMAL, "person-1", Collections.emptyMap(), "TK-2", firstAttachments);
        String attachmentChanged = OpenApiRequestFingerprint.portal("incident", "subject", "description",
                TicketPriority.NORMAL, "person-1", Collections.emptyMap(), "TK-1", secondAttachments);

        assertNotEquals(first, relatedChanged);
        assertNotEquals(first, attachmentChanged);
    }

    private Map<String, Object> attachment(String objectKey) {
        Map<String, Object> value = new HashMap<>();
        value.put("objectKey", objectKey);
        value.put("fileName", "a.txt");
        value.put("contentType", "text/plain");
        value.put("size", 1L);
        return value;
    }
}
