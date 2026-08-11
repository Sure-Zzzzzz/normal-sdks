package io.github.surezzzzzz.sdk.ops.middleware.ui;

import io.github.surezzzzzz.sdk.ops.middleware.annotation.SmartMiddlewareOpsServerComponent;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

import java.security.Principal;

/**
 * Middleware Ops Thymeleaf 页面入口。
 *
 * @author surezzzzzz
 */
@SmartMiddlewareOpsServerComponent
@Controller
public class MiddlewareOpsPageController {

    private final SmartMiddlewareOpsServerProperties properties;

    /**
     * 创建页面入口控制器。
     *
     * @param properties Server 配置
     */
    public MiddlewareOpsPageController(SmartMiddlewareOpsServerProperties properties) {
        this.properties = properties;
    }

    private static String avatarCharacter(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "?";
        }
        int offset = 0;
        while (offset < username.length()) {
            int codePoint = username.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) {
                return new String(Character.toChars(Character.toUpperCase(codePoint)));
            }
            offset += Character.charCount(codePoint);
        }
        return "?";
    }

    /**
     * 将应用根路径转至页面登录入口。
     *
     * @return 登录页重定向
     */
    @GetMapping("/")
    public RedirectView root() {
        return new RedirectView(properties.getUiBasePath() + "/login");
    }

    /**
     * 显示 Windows AD 登录页。
     *
     * @return 登录视图
     */
    @GetMapping("${io.github.surezzzzzz.sdk.ops.middleware.ui-base-path:/middleware-ops}/login")
    public String login(Model model) {
        model.addAttribute("uiBasePath", properties.getUiBasePath());
        return "middleware-ops/login";
    }

    /**
     * 显示运维工作台。
     *
     * @param model 页面模型
     * @return 工作台视图
     */
    @GetMapping("${io.github.surezzzzzz.sdk.ops.middleware.ui-base-path:/middleware-ops}")
    public String console(Model model, Principal principal) {
        model.addAttribute("apiBasePath", properties.getApiBasePath());
        model.addAttribute("uiBasePath", properties.getUiBasePath());
        model.addAttribute("auditMaxRangeDays", properties.getAudit().getMaxRangeDays());
        model.addAttribute("currentUser", principal.getName());
        model.addAttribute("currentUserAvatar", avatarCharacter(principal.getName()));
        return "middleware-ops/console";
    }
}
