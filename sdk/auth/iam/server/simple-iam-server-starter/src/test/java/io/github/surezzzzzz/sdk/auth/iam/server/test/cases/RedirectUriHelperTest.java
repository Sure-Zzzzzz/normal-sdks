package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedirectUriHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class RedirectUriHelperTest {

    private final RedirectUriHelper helper = new RedirectUriHelper();

    @Test
    @DisplayName("redirect_uri 应去重、裁剪空格并保留顺序")
    void testNormalizeAndDeduplicate() {
        List<String> result = helper.normalizeAndValidate(Arrays.asList(
                " https://a.example.com/callback ",
                "https://b.example.com/callback",
                "https://a.example.com/callback"
        ));

        assertEquals(Arrays.asList("https://a.example.com/callback", "https://b.example.com/callback"), result);
    }

    @Test
    @DisplayName("空 redirect_uri 列表应返回空列表，由授权模式校验决定是否允许")
    void testEmptyRedirectUris() {
        assertEquals(Collections.emptyList(), helper.normalizeAndValidate(null));
        assertEquals(Collections.emptyList(), helper.normalizeAndValidate(Collections.emptyList()));
    }

    @Test
    @DisplayName("相对地址、通配符、空字符串和非法 URI 应拒绝")
    void testRejectInvalidRedirectUris() {
        assertInvalid("/callback");
        assertInvalid("https://*.example.com/callback");
        assertInvalid("   ");
        assertInvalid("https://exa mple.com/callback");
    }

    private void assertInvalid(String uri) {
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> helper.normalizeAndValidate(Collections.singletonList(uri)));
        assertEquals(ErrorCode.TRUSTED_APPLICATION_REDIRECT_URI_INVALID, exception.getErrorCode());
    }
}
