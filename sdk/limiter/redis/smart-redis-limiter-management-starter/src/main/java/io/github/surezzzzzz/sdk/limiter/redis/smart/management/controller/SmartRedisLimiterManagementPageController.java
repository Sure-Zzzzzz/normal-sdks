package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/**
 * SmartRedisLimiter Management 小型管理页面
 *
 * @author surezzzzzz
 */
@Controller
@SmartRedisLimiterManagementComponent
@RequestMapping(SmartRedisLimiterManagementConstant.PLACEHOLDER_UI_BASE_PATH)
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX + ".ui",
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_ENABLE,
        havingValue = "true")
public class SmartRedisLimiterManagementPageController {

    private final SmartRedisLimiterPolicyManagementService managementService;
    private final SmartRedisLimiterManagementProperties properties;

    /**
     * 构造页面 Controller
     */
    public SmartRedisLimiterManagementPageController(
            SmartRedisLimiterPolicyManagementService managementService,
            SmartRedisLimiterManagementProperties properties) {
        this.managementService = managementService;
        this.properties = properties;
    }

    /**
     * 展示登录页
     */
    @GetMapping(SmartRedisLimiterManagementConstant.PATH_LOGIN)
    public String login(Model model) {
        addPageUrls(model);
        return SmartRedisLimiterManagementConstant.VIEW_LOGIN;
    }

    /**
     * 展示策略列表
     */
    @GetMapping(SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE)
    public String policies(
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) String resourceCode,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Model model) {
        SmartRedisLimiterPolicyPageResponse result = managementService.query(
                SmartRedisLimiterPolicyQuery.builder()
                        .serviceCode(serviceCode)
                        .resourceCode(resourceCode)
                        .subject(subject)
                        .enabled(enabled)
                        .page(page)
                        .size(size)
                        .build());
        if (result == null || result.getItems() == null) {
            throw new SmartRedisLimiterManagementException(
                    ErrorCode.PAGE_QUERY_FAILED, ErrorMessage.PAGE_QUERY_FAILED);
        }
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_ITEMS, result.getItems());
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_PAGE, result.getPage());
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_SIZE, result.getSize());
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_TOTAL_ELEMENTS,
                result.getTotalElements());
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_TOTAL_PAGES,
                result.getTotalPages());
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_PAGE_NUMBERS,
                pageNumbers(result.getPage(), result.getTotalPages()));
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_SERVICE_CODE, serviceCode);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_RESOURCE_CODE, resourceCode);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_SUBJECT, subject);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_ENABLED, enabled);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_DEFAULT_PAGE_SIZE,
                properties.getPage().getDefaultSize());
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_MAX_PAGE_SIZE,
                properties.getPage().getMaxSize());
        addPageUrls(model);
        return SmartRedisLimiterManagementConstant.VIEW_POLICY_LIST;
    }

    private List<Integer> pageNumbers(Integer page, Integer totalPages) {
        List<Integer> pageNumbers = new ArrayList<>();
        if (page == null || totalPages == null || totalPages <= 0) {
            return pageNumbers;
        }
        int first = Math.max(1, page - 2);
        int last = Math.min(totalPages, page + 2);
        for (int candidate = first; candidate <= last; candidate++) {
            pageNumbers.add(candidate);
        }
        return pageNumbers;
    }

    private void addPageUrls(Model model) {
        String uiBasePath = properties.getUi().getBasePath();
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_LOGIN_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_LOGIN);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_LOGOUT_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_LOGOUT);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_POLICY_PAGE_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_ADMIN_API_URL,
                properties.getApi().getBasePath()
                        + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_BOOTSTRAP_CSS_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_BOOTSTRAP_CSS);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_MANAGEMENT_UI_CSS_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_MANAGEMENT_UI_CSS);
        model.addAttribute(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_BOOTSTRAP_JS_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_BOOTSTRAP_JS);
    }
}
