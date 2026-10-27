package io.github.surezzzzzz.sdk.ticket.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketTemplateInvalidException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketServiceException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 表单模板校验（模板自身合法性+答案按模板校验；上限防滥用——DESIGN §6.1）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
public class TicketTemplateValidator {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,64}$");

    /**
     * 校验模板 JSON 自身（管理面保存前）。
     *
     * @param formTemplate 模板 JSON
     */
    public void validateTemplate(String formTemplate) {
        List<Map<String, Object>> fields = parse(formTemplate);
        if (fields.size() > SimpleTicketServerConstant.TEMPLATE_FIELD_MAX_COUNT) {
            throw new TicketTemplateInvalidException();
        }
        for (Map<String, Object> field : fields) {
            String key = str(field.get("key"));
            String type = str(field.get("type"));
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new TicketTemplateInvalidException();
            }
            if (!"text".equals(type) && !"select".equals(type) && !"textarea".equals(type)) {
                throw new TicketTemplateInvalidException();
            }
            if ("select".equals(type)) {
                Object options = field.get("options");
                if (!(options instanceof List) || ((List<?>) options).isEmpty()
                        || ((List<?>) options).size() > SimpleTicketServerConstant.TEMPLATE_OPTION_MAX_COUNT) {
                    throw new TicketTemplateInvalidException();
                }
            }
        }
    }

    /**
     * 按模板校验答案并序列化为快照 JSON（answers=null 视为空答）。
     *
     * @param formTemplate 模板 JSON
     * @param answers      答案 Map（key→值）
     * @return 规范化答案 JSON
     */
    public String validateAnswers(String formTemplate, Map<String, Object> answers) {
        List<Map<String, Object>> fields = parse(formTemplate);
        Map<String, Object> normalized = new LinkedHashMap<>();
        Map<String, Object> source = answers == null ? new LinkedHashMap<>() : answers;
        for (Map<String, Object> field : fields) {
            String key = str(field.get("key"));
            boolean required = Boolean.TRUE.equals(field.get("required"));
            Object value = source.get(key);
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                if (required) {
                    throw new TicketValidationException(key, "为必填项");
                }
                continue;
            }
            String text = String.valueOf(value);
            int maxLength = maxLengthOf(field);
            if (text.length() > maxLength) {
                throw new TicketValidationException(key, "超过长度上限 " + maxLength);
            }
            if ("select".equals(str(field.get("type")))) {
                List<String> options = new ArrayList<>();
                for (Object option : (List<?>) field.get("options")) {
                    options.add(String.valueOf(option));
                }
                if (!options.contains(text)) {
                    throw new TicketValidationException(key, "不在选项范围内");
                }
            }
            normalized.put(key, text);
        }
        try {
            return TicketJson.mapper().writeValueAsString(normalized);
        } catch (Exception e) {
            throw new TicketServiceException(e);
        }
    }

    private int maxLengthOf(Map<String, Object> field) {
        Object declared = field.get("maxLength");
        if (declared instanceof Number) {
            int value = ((Number) declared).intValue();
            return value > 0 && value <= SimpleTicketServerConstant.TEMPLATE_FIELD_MAX_LENGTH
                    ? value : SimpleTicketServerConstant.TEMPLATE_FIELD_MAX_LENGTH;
        }
        return SimpleTicketServerConstant.TEMPLATE_FIELD_MAX_LENGTH;
    }

    private List<Map<String, Object>> parse(String formTemplate) {
        if (formTemplate == null || formTemplate.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> fields = TicketJson.mapper().readValue(formTemplate,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            return fields == null ? new ArrayList<>() : fields;
        } catch (Exception e) {
            throw new TicketTemplateInvalidException();
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
