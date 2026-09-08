package io.github.surezzzzzz.sdk.auth.iam.core.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.core.support.IamRouteKeyHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM路由键帮助类测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamRouteKeyHelperTest {

    @Test
    void shouldCreateAndExtractStrictIamRouteKey() {
        String routeKey = IamRouteKeyHelper.createRouteKey("key-a_1.2");

        log.info("IAM路由键：{}", routeKey);
        assertEquals("iam/key-a_1.2", routeKey, "路由键必须使用固定IAM命名空间");
        assertEquals("key-a_1.2", IamRouteKeyHelper.extractKeyId(routeKey), "必须提取原始密钥标识");
        assertTrue(IamRouteKeyHelper.isIamRouteKey(routeKey), "合法IAM路由键必须被识别");
    }

    @Test
    void shouldRejectUnsafeRouteKeyWhenCreating() {
        assertThrows(IamProtocolException.class, () -> IamRouteKeyHelper.createRouteKey(null),
                "空密钥标识必须拒绝");
        assertThrows(IamProtocolException.class, () -> IamRouteKeyHelper.createRouteKey(" key-a"),
                "含首尾空白的密钥标识必须拒绝");
        assertThrows(IamProtocolException.class, () -> IamRouteKeyHelper.createRouteKey("key/a"),
                "含分隔符的密钥标识必须拒绝");
        assertThrows(IamProtocolException.class, () -> IamRouteKeyHelper.createRouteKey("${key}"),
                "动态表达式密钥标识必须拒绝");
    }

    @Test
    void shouldNotTreatMalformedOrForeignRouteKeyAsIam() {
        log.info("非法IAM路由键必须只在路由阶段被拒绝");
        assertNull(IamRouteKeyHelper.extractKeyId("aksk/key-a"), "其他Provider命名空间不得被IAM接管");
        assertNull(IamRouteKeyHelper.extractKeyId("iam/"), "缺失密钥标识必须拒绝");
        assertNull(IamRouteKeyHelper.extractKeyId("iam/key/a"), "多分隔符路由键必须拒绝");
        assertNull(IamRouteKeyHelper.extractKeyId("iam/key a"), "含空白密钥标识必须拒绝");
        assertFalse(IamRouteKeyHelper.isIamRouteKey("iam/key/a"), "非法路由键不得被识别为IAM来源");
    }
}
