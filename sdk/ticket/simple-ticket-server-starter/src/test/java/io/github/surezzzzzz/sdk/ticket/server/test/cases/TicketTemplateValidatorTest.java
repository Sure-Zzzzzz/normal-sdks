package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.exception.TicketTemplateInvalidException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketTemplateValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板校验器测试（模板合法性+答案校验+上限——DESIGN §6.1）。
 *
 * @author surezzzzzz
 */
@Slf4j
class TicketTemplateValidatorTest {

    private final TicketTemplateValidator validator = new TicketTemplateValidator();

    private static final String TEMPLATE = "[{\"key\":\"scope\",\"label\":\"影响范围\",\"type\":\"select\","
            + "\"required\":true,\"options\":[\"单系统\",\"多系统\",\"全域\"]},"
            + "{\"key\":\"when\",\"label\":\"发生时间\",\"type\":\"text\",\"required\":false,\"maxLength\":32}]";

    @Test
    void validateTemplateAcceptsLegalShape() {
        validator.validateTemplate(TEMPLATE);
        validator.validateTemplate(null);
        validator.validateTemplate("[]");
    }

    @Test
    void validateTemplateRejectsIllegalShape() {
        assertThrows(TicketTemplateInvalidException.class, () -> validator.validateTemplate("not-json"));
        assertThrows(TicketTemplateInvalidException.class,
                () -> validator.validateTemplate("[{\"key\":\"bad key\",\"type\":\"text\"}]"));
        assertThrows(TicketTemplateInvalidException.class,
                () -> validator.validateTemplate("[{\"key\":\"a\",\"type\":\"datetime\"}]"));
        assertThrows(TicketTemplateInvalidException.class,
                () -> validator.validateTemplate("[{\"key\":\"a\",\"type\":\"select\",\"options\":[]}]"));
    }

    @Test
    void validateAnswersEnforcesRequiredOptionAndLength() {
        Map<String, Object> answers = new HashMap<>();
        answers.put("scope", "全域");
        answers.put("when", "2026-10-10 09:00");
        String normalized = validator.validateAnswers(TEMPLATE, answers);
        log.debug("核对模板答案校验：fieldCount={}", answers.size());
        assertTrue(normalized.contains("\"scope\":\"全域\""));

        assertThrows(TicketValidationException.class,
                () -> validator.validateAnswers(TEMPLATE, new HashMap<>()));
        Map<String, Object> badOption = new HashMap<>();
        badOption.put("scope", "宇宙");
        assertThrows(TicketValidationException.class,
                () -> validator.validateAnswers(TEMPLATE, badOption));
        Map<String, Object> tooLong = new HashMap<>();
        tooLong.put("scope", "单系统");
        tooLong.put("when", "0123456789012345678901234567890123456789");
        TicketValidationException e = assertThrows(TicketValidationException.class,
                () -> validator.validateAnswers(TEMPLATE, tooLong));
        assertEquals("when", e.getField());
    }
}
