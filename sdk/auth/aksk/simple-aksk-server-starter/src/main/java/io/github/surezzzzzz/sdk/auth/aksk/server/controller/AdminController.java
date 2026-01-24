package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.UpdateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.*;
import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Admin Management Controller
 * 提供基于Thymeleaf的Web管理页面（仅内网访问）
 *
 * @author surezzzzzz
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.auth.aksk.server.admin",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AdminController {

    private final ClientManagementService clientManagementService;
    private final TokenManagementService tokenManagementService;
    private final RestTemplateBuilder restTemplateBuilder;
    private final Environment environment;

    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    /**
     * 管理首页 - 显示所有AKSK列表（支持过滤、搜索、分页）
     */
    @GetMapping
    public String index(@RequestParam(required = false) String type,
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Model model) {
        // 使用Service层的分页查询（支持类型过滤）
        // type可以是"platform"或"user"
        PageResponse<ClientInfoResponse> pageResponse = clientManagementService.listClients(
                null, type, page, size);

        // 如果有search参数，需要在内存中过滤（因为listClients不支持search参数）
        List<ClientInfoResponse> clients = pageResponse.getData();
        long totalClients = pageResponse.getTotal();
        int totalPages = pageResponse.getTotalPages();

        if (search != null && !search.trim().isEmpty()) {
            // 重新获取所有数据进行搜索过滤
            PageResponse<ClientInfoResponse> allPageResponse = clientManagementService.listClients(
                    null, type, 1, Integer.MAX_VALUE);
            List<ClientInfoResponse> allClients = allPageResponse.getData();

            // 搜索过滤（按clientId、name、userId、username）
            String searchLower = search.trim().toLowerCase();
            List<ClientInfoResponse> filteredClients = allClients.stream()
                    .filter(client ->
                            client.getClientId().toLowerCase().contains(searchLower) ||
                                    client.getClientName().toLowerCase().contains(searchLower) ||
                                    (client.getOwnerUserId() != null && client.getOwnerUserId().toLowerCase().contains(searchLower)) ||
                                    (client.getOwnerUsername() != null && client.getOwnerUsername().toLowerCase().contains(searchLower))
                    )
                    .sorted((a, b) -> b.getClientIdIssuedAt().compareTo(a.getClientIdIssuedAt()))
                    .collect(java.util.stream.Collectors.toList());

            // 重新分页
            totalClients = filteredClients.size();
            totalPages = (int) Math.ceil((double) totalClients / size);
            page = Math.max(1, Math.min(page, totalPages > 0 ? totalPages : 1));

            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, filteredClients.size());
            clients = startIndex < filteredClients.size() ?
                    filteredClients.subList(startIndex, endIndex) :
                    java.util.Collections.emptyList();
        }

        // 添加模型属性
        model.addAttribute("clients", clients);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalClients", totalClients);
        model.addAttribute("currentType", type);
        model.addAttribute("currentSearch", search);

        return "admin/index";
    }

    /**
     * 创建平台级AKSK页面
     */
    @GetMapping("/create-platform")
    public String createPlatformPage() {
        return "admin/create-platform";
    }

    /**
     * 创建平台级AKSK
     */
    @PostMapping("/create-platform")
    public String createPlatform(@RequestParam String name,
                                 RedirectAttributes redirectAttributes) {
        try {
            ClientInfoResponse clientInfo = clientManagementService.createPlatformClient(name);

            redirectAttributes.addFlashAttribute("success", true);
            redirectAttributes.addFlashAttribute("clientId", clientInfo.getClientId());
            redirectAttributes.addFlashAttribute("clientSecret", clientInfo.getClientSecret());
            redirectAttributes.addFlashAttribute("message", ServerErrorMessage.ADMIN_CREATE_SUCCESS);

            return "redirect:/admin/create-success";
        } catch (Exception e) {
            log.error("Failed to create platform client", e);
            redirectAttributes.addFlashAttribute("error", String.format(ErrorMessage.CLIENT_CREATE_FAILED, e.getMessage()));
            return "redirect:/admin/create-platform";
        }
    }

    /**
     * 创建成功页面（显示client_secret）
     */
    @GetMapping("/create-success")
    public String createSuccess() {
        return "admin/create-success";
    }

    /**
     * 创建用户级AKSK页面
     */
    @GetMapping("/create-user")
    public String createUserPage() {
        return "admin/create-user";
    }

    /**
     * 创建用户级AKSK
     */
    @PostMapping("/create-user")
    public String createUser(@RequestParam String ownerUserId,
                             @RequestParam String ownerUsername,
                             @RequestParam String name,
                             RedirectAttributes redirectAttributes) {
        try {
            ClientInfoResponse clientInfo = clientManagementService.createUserClient(ownerUserId, ownerUsername, name);

            redirectAttributes.addFlashAttribute("success", true);
            redirectAttributes.addFlashAttribute("clientId", clientInfo.getClientId());
            redirectAttributes.addFlashAttribute("clientSecret", clientInfo.getClientSecret());
            redirectAttributes.addFlashAttribute("message", ServerErrorMessage.ADMIN_CREATE_SUCCESS);

            return "redirect:/admin/create-success";
        } catch (Exception e) {
            log.error("Failed to create user client", e);
            redirectAttributes.addFlashAttribute("error", String.format(ErrorMessage.CLIENT_CREATE_FAILED, e.getMessage()));
            return "redirect:/admin/create-user";
        }
    }

    /**
     * 删除AKSK (RESTful API)
     */
    @DeleteMapping("/{clientId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> deleteClient(@PathVariable String clientId) {
        try {
            clientManagementService.deleteClient(clientId);
            return ResponseEntity.ok(new ApiResponse(ServerErrorMessage.ADMIN_DELETE_SUCCESS));
        } catch (Exception e) {
            log.error("Failed to delete client: {}", clientId, e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(String.format(ServerErrorMessage.ADMIN_DELETE_FAILED, e.getMessage())));
        }
    }

    /**
     * 修改AKSK状态 (RESTful API - 启用/禁用)
     */
    @PatchMapping("/{clientId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> updateClient(@PathVariable String clientId,
                                                    @RequestBody UpdateClientRequest request) {
        try {
            if (request.getEnabled() == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse("enabled字段不能为空"));
            }

            if (request.getEnabled()) {
                clientManagementService.enableClient(clientId);
                return ResponseEntity.ok(new ApiResponse(ServerErrorMessage.ADMIN_ENABLE_SUCCESS));
            } else {
                clientManagementService.disableClient(clientId);
                return ResponseEntity.ok(new ApiResponse(ServerErrorMessage.ADMIN_DISABLE_SUCCESS));
            }
        } catch (Exception e) {
            log.error("Failed to update client: {}", clientId, e);
            String errorMessage = request.getEnabled() ?
                    String.format(ServerErrorMessage.ADMIN_ENABLE_FAILED, e.getMessage()) :
                    String.format(ServerErrorMessage.ADMIN_DISABLE_FAILED, e.getMessage());
            return ResponseEntity.status(500).body(new ApiResponse(errorMessage));
        }
    }

    /**
     * 查询用户级AKSK页面
     */
    @GetMapping("/query-user")
    public String queryUserPage() {
        return "admin/query-user";
    }

    /**
     * 查询用户级AKSK
     */
    @GetMapping("/query-user/result")
    public String queryUser(@RequestParam String userId, Model model) {
        try {
            // 使用分页查询获取用户的所有Client（传入一个很大的size来获取所有数据）
            PageResponse<ClientInfoResponse> pageResponse = clientManagementService.listClients(
                    userId, "user", 1, Integer.MAX_VALUE);

            // 按创建时间降序排序
            List<ClientInfoResponse> sortedClients = pageResponse.getData().stream()
                    .sorted((a, b) -> b.getClientIdIssuedAt().compareTo(a.getClientIdIssuedAt()))
                    .collect(java.util.stream.Collectors.toList());

            model.addAttribute("userId", userId);
            model.addAttribute("clients", sortedClients);
            return "admin/query-user-result";
        } catch (Exception e) {
            log.error("Failed to query user clients: {}", userId, e);
            model.addAttribute("error", String.format(ServerErrorMessage.ADMIN_QUERY_FAILED, e.getMessage()));
            return "admin/query-user";
        }
    }

    /**
     * 查看AKSK详情 (RESTful API)
     */
    @GetMapping("/{clientId}")
    public String detail(@PathVariable String clientId, Model model) {
        try {
            ClientInfoResponse clientInfo = clientManagementService.getClientById(clientId);
            model.addAttribute("client", clientInfo);
            return "admin/detail";
        } catch (Exception e) {
            log.error("Failed to get client detail: {}", clientId, e);
            model.addAttribute("error", String.format(ServerErrorMessage.ADMIN_QUERY_FAILED, e.getMessage()));
            return "redirect:/admin";
        }
    }

    // ==================== Token Management Pages ====================

    /**
     * Token管理页面 - 显示所有Token列表（支持过滤、搜索、分页）
     * 支持MySQL和Redis两个数据源切换
     */
    @GetMapping("/token")
    public String tokenList(@RequestParam(required = false, defaultValue = "mysql") String source,
                            @RequestParam(required = false) String clientId,
                            @RequestParam(required = false) Integer clientType,
                            @RequestParam(required = false) TokenInfo.TokenStatus status,
                            @RequestParam(required = false) String search,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {

        PageResponse<TokenInfoResponse> pageResponse;

        if ("redis".equals(source)) {
            // 查询Redis Token（只支持状态过滤和分页）
            pageResponse = tokenManagementService.queryRedisTokens(status, page, size);
        } else {
            // 查询MySQL Token（支持所有过滤条件）
            TokenQueryRequest request = new TokenQueryRequest();
            request.setClientId(clientId);
            request.setClientType(clientType);
            request.setStatus(status);
            request.setSearch(search);
            request.setPage(page);
            request.setSize(size);
            pageResponse = tokenManagementService.queryTokens(request);
        }

        // 获取统计信息（仅MySQL统计）
        TokenStatisticsResponse statistics = tokenManagementService.getStatistics();

        // 计算分页信息
        int totalPages = pageResponse.getTotalPages();
        page = Math.max(1, Math.min(page, totalPages > 0 ? totalPages : 1));

        // 添加模型属性
        model.addAttribute("currentSource", source);  // 用于Tab高亮和逻辑判断
        model.addAttribute("tokens", pageResponse.getData());
        model.addAttribute("statistics", statistics);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalTokens", pageResponse.getTotal());
        model.addAttribute("currentClientId", clientId);
        model.addAttribute("currentClientType", clientType);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentSearch", search);

        return "admin/token";
    }

    /**
     * Token详情页面
     */
    @GetMapping("/token/{id}")
    public String tokenDetail(@PathVariable String id, Model model) {
        try {
            TokenInfoResponse tokenInfo = tokenManagementService.getTokenById(id);
            if (tokenInfo == null) {
                model.addAttribute("error", "Token不存在");
                return "redirect:/admin/token";
            }
            model.addAttribute("token", tokenInfo);
            return "admin/token-detail";
        } catch (Exception e) {
            log.error("Failed to get token detail: {}", id, e);
            model.addAttribute("error", String.format("查询Token详情失败: %s", e.getMessage()));
            return "redirect:/admin/token";
        }
    }

    /**
     * 换Token测试页面
     */
    @GetMapping("/token/test")
    public String tokenTestPage() {
        return "admin/token-test";
    }

    /**
     * 执行换Token操作
     */
    @PostMapping("/token/test")
    public String tokenTest(@RequestParam String clientId,
                            @RequestParam String clientSecret,
                            @RequestParam(defaultValue = "read write") String scope,
                            Model model) {
        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .basicAuthentication(clientId, clientSecret)
                    .build();

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("scope", scope);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            String port = environment.getProperty("server.port", "8080");
            String tokenUrl = "http://localhost:" + port + "/oauth2/token";

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                model.addAttribute("success", true);
                model.addAttribute("accessToken", tokenResponse.get("access_token"));
                model.addAttribute("tokenType", tokenResponse.get("token_type"));
                model.addAttribute("expiresIn", tokenResponse.get("expires_in"));
                model.addAttribute("scope", tokenResponse.get("scope"));
            } else {
                model.addAttribute("error", "Token请求失败: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("Token request failed with client error", e);
            model.addAttribute("error", "认证失败: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Token request failed", e);
            model.addAttribute("error", "换Token失败: " + e.getMessage());
        }

        model.addAttribute("clientId", clientId);
        model.addAttribute("requestedScope", scope);
        return "admin/token-test";
    }

    /**
     * Security Context演示页面
     */
    @GetMapping("/security-context-demo")
    public String securityContextDemoPage() {
        return "admin/security-context-demo";
    }

}
