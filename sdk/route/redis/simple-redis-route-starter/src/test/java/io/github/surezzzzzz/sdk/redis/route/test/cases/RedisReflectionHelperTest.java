package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.support.RedisReflectionHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisReflectionHelper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisReflectionHelperTest {

    @Test
    public void testFindMethodByNameOnly() {
        Method method = RedisReflectionHelper.findMethod(String.class, "length");
        log.info("findMethod(length) 结果: {}", method);
        assertNotNull(method, "String#length 应能找到");
        assertEquals("length", method.getName(), "方法名应为 length");
    }

    @Test
    public void testFindMethodByNameReturnsNullWhenMissing() {
        Method method = RedisReflectionHelper.findMethod(String.class, "notExistMethod");
        assertNull(method, "不存在的方法应返回 null");
    }

    @Test
    public void testFindMethodWithParameterTypes() {
        Method method = RedisReflectionHelper.findMethod(String.class, "substring", int.class, int.class);
        log.info("findMethod(substring,int,int) 结果: {}", method);
        assertNotNull(method, "String#substring(int,int) 应能找到");
        assertEquals(2, method.getParameterCount(), "参数个数应为 2");
    }

    @Test
    public void testFindMethodWithParameterTypesReturnsNullWhenMissing() {
        Method method = RedisReflectionHelper.findMethod(String.class, "substring", String.class);
        assertNull(method, "不存在签名应返回 null");
    }

    @Test
    public void testFindMethodWithNullArgsReturnsNull() {
        assertNull(RedisReflectionHelper.findMethod(null, "x"), "clazz 为 null 应返回 null");
        assertNull(RedisReflectionHelper.findMethod(String.class, null), "methodName 为 null 应返回 null");
    }

    @Test
    public void testInvokeCallsMethodAndReturnsValue() {
        Method method = RedisReflectionHelper.findMethod(String.class, "length");
        Object result = RedisReflectionHelper.invoke("hello", method);
        log.info("invoke length on 'hello' 结果: {}", result);
        assertEquals(5, result, "调用 length 应返回 5");
    }

    @Test
    public void testInvokeReturnsNullOnFailure() {
        Method method = RedisReflectionHelper.findMethod(String.class, "substring", int.class, int.class);
        log.info("invoke substring(10,1) on 'hi' 应失败返回 null");
        Object result = RedisReflectionHelper.invoke("hi", method, 10, 1);
        assertNull(result, "调用失败时应返回 null");
    }

    @Test
    public void testInvokeReturnsNullWhenTargetOrMethodNull() {
        Method method = RedisReflectionHelper.findMethod(String.class, "length");
        assertNull(RedisReflectionHelper.invoke(null, method), "target 为 null 应返回 null");
        assertNull(RedisReflectionHelper.invoke("x", null), "method 为 null 应返回 null");
    }
}
