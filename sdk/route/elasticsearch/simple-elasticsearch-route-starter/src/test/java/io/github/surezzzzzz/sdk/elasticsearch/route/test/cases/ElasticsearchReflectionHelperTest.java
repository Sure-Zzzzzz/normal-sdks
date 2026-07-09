package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ElasticsearchReflectionException;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchReflectionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchReflectionHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchReflectionHelperTest {

    @Test
    public void testClassLookup() {
        log.info("=== testClassLookup ===");
        assertTrue(ElasticsearchReflectionHelper.isClassPresent("java.lang.String"));
        assertFalse(ElasticsearchReflectionHelper.isClassPresent("not.exists.MockClass"));
        assertEquals(String.class, ElasticsearchReflectionHelper.loadClass("java.lang.String"));
        assertThrows(ElasticsearchReflectionException.class,
                () -> ElasticsearchReflectionHelper.loadClass("not.exists.MockClass"));
    }

    @Test
    public void testMethodAndInvoke() {
        log.info("=== testMethodAndInvoke ===");
        Method substring = ElasticsearchReflectionHelper.loadMethod(String.class, "substring", int.class);
        assertEquals("bc", ElasticsearchReflectionHelper.invoke(substring, "abc", 1));
        assertNull(ElasticsearchReflectionHelper.findMethod(String.class, "notExists"));
        assertThrows(ElasticsearchReflectionException.class,
                () -> ElasticsearchReflectionHelper.loadMethod(String.class, "notExists"));
    }

    @Test
    public void testFieldAndConstructor() {
        log.info("=== testFieldAndConstructor ===");
        assertNotNull(ElasticsearchReflectionHelper.getStaticField(String.class, "CASE_INSENSITIVE_ORDER"));
        Constructor<?> constructor = ElasticsearchReflectionHelper.loadConstructor(String.class, byte[].class);
        Object value = ElasticsearchReflectionHelper.newInstance(constructor, "abc".getBytes());
        assertEquals("abc", value);
    }
}
