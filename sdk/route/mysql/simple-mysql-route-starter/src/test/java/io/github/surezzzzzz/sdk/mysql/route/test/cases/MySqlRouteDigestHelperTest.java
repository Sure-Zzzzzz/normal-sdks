package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.support.MySqlRouteDigestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL Route 资源摘要帮助类测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class MySqlRouteDigestHelperTest {

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 资源摘要帮助类测试");
    }

    @Test
    public void shouldAcceptOnlyLowercaseHexSha256Digest() {
        String digest = MySqlRouteDigestHelper.sha256("test-resource");

        assertTrue(MySqlRouteDigestHelper.isSha256(digest));
        assertFalse(MySqlRouteDigestHelper.isSha256(null));
        assertFalse(MySqlRouteDigestHelper.isSha256(digest.substring(1)));
        assertFalse(MySqlRouteDigestHelper.isSha256(digest.toUpperCase()));
        assertFalse(MySqlRouteDigestHelper.isSha256(digest.substring(0, digest.length() - 1) + "g"));
    }
}
