package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.controller;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.annotation.SimpleKafkaOutboxManagementComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.SimpleKafkaOutboxManagementConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.service.KafkaOutboxManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Outbox Management 页面控制器。
 *
 * @author surezzzzzz
 */
@Controller
@RequiredArgsConstructor
@SimpleKafkaOutboxManagementComponent
@RequestMapping("${io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.ui.base-path:/outbox-management}")
@ConditionalOnProperty(prefix = SimpleKafkaOutboxManagementConstant.CONFIG_PREFIX,
        name = SimpleKafkaOutboxManagementConstant.CONFIG_PROPERTY_UI_ENABLE, havingValue = "true", matchIfMissing = true)
public class KafkaOutboxManagementPageController {
    private final KafkaOutboxManagementService service;
    private final SimpleKafkaOutboxManagementProperties properties;

    /**
     * 展示登录页。
     */
    @GetMapping(SimpleKafkaOutboxManagementConstant.PATH_LOGIN)
    public String login(Model model) {
        addUrls(model);
        return SimpleKafkaOutboxManagementConstant.VIEW_LOGIN;
    }

    /**
     * 展示状态总览。
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("summaries", service.summaries());
        addUrls(model);
        return SimpleKafkaOutboxManagementConstant.VIEW_DASHBOARD;
    }

    /**
     * 展示状态列表。
     */
    @GetMapping(SimpleKafkaOutboxManagementConstant.PATH_RECORDS)
    public String records(@RequestParam String status, @RequestParam(required = false) String cursor,
                          @RequestParam(required = false) Integer size, Model model) {
        model.addAttribute("status", status);
        model.addAttribute("page", service.browse(status, cursor, size));
        model.addAttribute("size", size == null ? properties.getPage().getDefaultSize() : size);
        addUrls(model);
        return SimpleKafkaOutboxManagementConstant.VIEW_RECORDS;
    }

    /**
     * 展示记录详情。
     */
    @GetMapping(SimpleKafkaOutboxManagementConstant.PATH_RECORDS + "/{recordId}")
    public String detail(@PathVariable Long recordId, Model model) {
        model.addAttribute("record", service.detail(recordId));
        addUrls(model);
        return SimpleKafkaOutboxManagementConstant.VIEW_DETAIL;
    }

    /**
     * 按 ID 或消息标识定位记录。
     */
    @PostMapping(SimpleKafkaOutboxManagementConstant.PATH_LOCATE)
    public String locate(@RequestParam(required = false) String recordId, @RequestParam(required = false) String messageId,
                         Model model) {
        boolean hasRecordId = recordId != null && !recordId.trim().isEmpty();
        String normalizedMessageId = messageId == null ? null : messageId.trim();
        boolean hasMessageId = normalizedMessageId != null && !normalizedMessageId.isEmpty();
        if (hasRecordId == hasMessageId) return invalidLocate(model);
        Long id;
        if (hasRecordId) {
            try {
                id = Long.valueOf(recordId);
                if (id < 1) return invalidLocate(model);
            } catch (NumberFormatException ex) {
                return invalidLocate(model);
            }
        } else {
            id = service.detailByMessageId(normalizedMessageId).getRecordId();
        }
        return "redirect:" + properties.getUi().getBasePath() + SimpleKafkaOutboxManagementConstant.PATH_RECORDS + "/" + id;
    }

    /**
     * 重置单条 POISON 记录。
     */
    @PostMapping(SimpleKafkaOutboxManagementConstant.PATH_RECORDS + "/{recordId}/reset-poison")
    public String resetPoison(@PathVariable Long recordId, RedirectAttributes attributes) {
        service.resetPoison(recordId);
        attributes.addFlashAttribute("success", "POISON 记录已重置为 PENDING");
        return "redirect:" + properties.getUi().getBasePath() + SimpleKafkaOutboxManagementConstant.PATH_RECORDS + "/" + recordId;
    }

    private void addUrls(Model model) {
        String basePath = properties.getUi().getBasePath();
        model.addAttribute("basePath", basePath);
        model.addAttribute("dashboardUrl", basePath + "/");
        model.addAttribute("locateUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_LOCATE);
        model.addAttribute("loginUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_LOGIN);
        model.addAttribute("logoutUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_LOGOUT);
        model.addAttribute("bootstrapCssUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_BOOTSTRAP_CSS);
        model.addAttribute("managementUiCssUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_MANAGEMENT_UI_CSS);
        model.addAttribute("brandIconUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_ASSETS + "/icon.svg");
    }

    private String invalidLocate(Model model) {
        model.addAttribute("summaries", service.summaries());
        model.addAttribute("error", "请输入一个有效的记录 ID 或消息 ID");
        addUrls(model);
        return SimpleKafkaOutboxManagementConstant.VIEW_DASHBOARD;
    }
}
