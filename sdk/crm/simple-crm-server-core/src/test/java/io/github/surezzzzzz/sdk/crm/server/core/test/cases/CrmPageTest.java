package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分页契约边界测试：真实总数驱动续页判断，游标语义与不可变结果保持兼容。
 *
 * @author surezzzzzz
 */
@Slf4j
class CrmPageTest {

    @Test
    void pageNumberUsesTotalToIdentifyMiddleLastAndOutOfRangePages() {
        CrmPage<String> first = new CrmPage<String>(Arrays.asList("a", "b"),
                new CrmPageRequest(1, 2), 4);
        CrmPage<String> last = new CrmPage<String>(Arrays.asList("c", "d"),
                new CrmPageRequest(2, 2), 4);
        CrmPage<String> beyondLast = new CrmPage<String>(Collections.<String>emptyList(),
                new CrmPageRequest(3, 2), 4);
        log.info("页码边界：首页续页={}，整批末页续页={}，越界空页续页={}",
                first.hasNext(), last.hasNext(), beyondLast.hasNext());
        assertTrue(first.hasNext());
        assertFalse(last.hasNext());
        assertFalse(beyondLast.hasNext());
        assertEquals(Integer.valueOf(2), last.getPage());
        assertEquals(Integer.valueOf(2), last.getPageSize());
        assertEquals(Long.valueOf(4), last.getTotal());
        assertNull(first.getNextCursor());
    }

    @Test
    void pageNumberHandlesPartialLastPageAndEmptyDataset() {
        CrmPage<String> last = new CrmPage<String>(Collections.singletonList("e"),
                new CrmPageRequest(3, 2), 5);
        CrmPage<String> empty = new CrmPage<String>(Collections.<String>emptyList(),
                new CrmPageRequest(1, 20), 0);
        log.info("尾页和空数据：尾页条数={}、总数={}，空数据总数={}",
                last.getItems().size(), last.getTotal(), empty.getTotal());
        assertFalse(last.hasNext());
        assertFalse(empty.hasNext());
    }

    @Test
    void largePageNumberOffsetDoesNotOverflow() {
        CrmPageRequest request = new CrmPageRequest(Integer.MAX_VALUE, 100);
        CrmPage<String> page = new CrmPage<String>(Collections.<String>emptyList(), request,
                Long.MAX_VALUE);
        log.info("大页码偏移量={}", request.offset());
        assertEquals(214748364600L, request.offset());
        assertTrue(page.hasNext());
    }

    @Test
    void cursorModeRetainsOpaqueCursorAndNoNumberedMetadata() {
        CrmPage<String> next = new CrmPage<String>(Collections.singletonList("a"), "opaque-cursor");
        CrmPage<String> last = new CrmPage<String>(Collections.singletonList("b"), null);
        log.info("游标模式：首页续页={}，末页续页={}", next.hasNext(), last.hasNext());
        assertTrue(next.hasNext());
        assertFalse(last.hasNext());
        assertEquals("opaque-cursor", next.getNextCursor());
        assertNull(next.getPage());
        assertNull(next.getPageSize());
        assertNull(next.getTotal());
    }

    @Test
    void pageCopiesItemsAndRejectsInvalidPaginationFacts() {
        List<String> source = new ArrayList<String>(Collections.singletonList("a"));
        CrmPage<String> page = new CrmPage<String>(source, new CrmPageRequest(1, 2), 1);
        source.clear();
        log.info("源列表清空后结果条数={}", page.getItems().size());
        assertEquals(Collections.singletonList("a"), page.getItems());
        assertThrows(UnsupportedOperationException.class, () -> page.getItems().clear());
        assertThrows(CrmException.class, () -> new CrmPageRequest(0, 20));
        assertThrows(CrmException.class, () -> new CrmPageRequest(1, 0));
        assertThrows(CrmException.class, () -> new CrmPageRequest(1, 101));
        assertThrows(IllegalArgumentException.class, () -> new CrmPage<String>(
                Arrays.asList("a", "b"), new CrmPageRequest(1, 1), 2));
        assertThrows(IllegalArgumentException.class, () -> new CrmPage<String>(
                Collections.singletonList("a"), new CrmPageRequest(1, 2), 0));
        assertThrows(IllegalArgumentException.class, () -> new CrmPage<String>(
                        Collections.singletonList("a"), new CrmPageRequest(2, 2), 2),
                "已越过总数的页码不能仍有当页数据");
        assertThrows(IllegalArgumentException.class, () -> new CrmPage<String>(
                Collections.<String>emptyList(), new CrmPageRequest(1, 20), -1));
    }
}
