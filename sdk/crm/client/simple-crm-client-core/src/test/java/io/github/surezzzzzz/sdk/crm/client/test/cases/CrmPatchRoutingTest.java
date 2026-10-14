package io.github.surezzzzzz.sdk.crm.client.test.cases;

import io.github.surezzzzzz.sdk.crm.client.CrmPatchRouting;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PATCH 分派查询参数推导测试：载荷判别字段（state/owner/sequence）镜像进查询参数，
 * 其余载荷字段不参与路由。
 *
 * @author surezzzzzz
 */
class CrmPatchRoutingTest {

    @Test
    void statePatchMirrorsStateOnly() {
        Map<String, Object> patch = new LinkedHashMap<String, Object>();
        patch.put("state", "inactive");
        patch.put("mergeTargetId", "customer-2");
        Map<String, String> routing = CrmPatchRouting.of(patch);
        assertEquals(1, routing.size(), "mergeTargetId 是载荷不是路由判别值");
        assertEquals("inactive", routing.get("state"));
    }

    @Test
    void ownerPatchMirrorsOwner() {
        Map<String, Object> patch = new LinkedHashMap<String, Object>();
        patch.put("owner", "iam:operator-1");
        Map<String, String> routing = CrmPatchRouting.of(patch);
        assertEquals("iam:operator-1", routing.get("owner"));
    }

    @Test
    void reportPatchMirrorsSequenceAndState() {
        Map<String, Object> patch = new LinkedHashMap<String, Object>();
        patch.put("sequence", 3);
        patch.put("state", "IN_PROGRESS");
        patch.put("resultData", "{\"progress\":50}");
        Map<String, String> routing = CrmPatchRouting.of(patch);
        assertEquals("3", routing.get("sequence"), "sequence 数值须字符串化");
        assertEquals("IN_PROGRESS", routing.get("state"));
        assertEquals(2, routing.size(), "resultData 是载荷不是路由判别值");
    }

    @Test
    void nullOrEmptyPatchYieldsEmptyRouting() {
        assertTrue(CrmPatchRouting.of(null).isEmpty(), "空载荷不得产生路由参数");
        assertTrue(CrmPatchRouting.of(new LinkedHashMap<String, Object>()).isEmpty());
    }
}
