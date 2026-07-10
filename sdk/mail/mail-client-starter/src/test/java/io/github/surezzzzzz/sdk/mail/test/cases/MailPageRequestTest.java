package io.github.surezzzzzz.sdk.mail.test.cases;

import io.github.surezzzzzz.sdk.mail.constant.MailConstant;
import io.github.surezzzzzz.sdk.mail.model.request.MailPageRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mail 分页请求测试
 *
 * @author surezzzzzz
 */
@Slf4j
class MailPageRequestTest {

    @Test
    @DisplayName("测试分页默认值")
    void testDefaultPageRequest() {
        MailPageRequest request = new MailPageRequest();

        log.info("默认页码: {}", request.getPageNo());
        log.info("默认分页大小: {}", request.getPageSize());
        assertEquals(MailConstant.DEFAULT_PAGE_NO, request.getPageNo(), "默认页码应正确");
        assertEquals(MailConstant.DEFAULT_PAGE_SIZE, request.getPageSize(), "默认分页大小应正确");
    }
}
