package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.UpdateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.*;
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
                        @RequestParam(required = false) Boolean enabled,
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Model model) {
        // 使用Service层的分页查询（支持类型 + enabled 过滤）
        PageResponse<ClientInfoResponse> pageResponse = clientManagementService.listClients(
                null, type, enabled, page, size);

        // 如果有search参数，需要在内存中过滤（因为listClients不支持search参数）
        List<ClientInfoResponse> clients = pageResponse.getData();
        long totalClients = pageResponse.getTotal();
        int totalPages = pageResponse.getTotalPages();

        if (search != null && !search.trim().isEmpty()) {
            // 重新获取所有数据进行搜索过滤
            PageResponse<ClientInfoResponse> allPageResponse = clientManagementService.listClients(
                    null, type, enabled, 1, Integer.MAX_VALUE);
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
        model.addAttribute("currentEnabled", enabled);
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
                                 @RequestParam(required = false) String scopes,
                                 RedirectAttributes redirectAttributes) {
        try {
            // 后端验证：检查scopes是否包含换行符
            if (scopes != null && scopes.matches(".*[\\r\\n]+.*")) {
                redirectAttributes.addFlashAttribute("error", ServerErrorMessage.ADMIN_SCOPE_NEWLINE_NOT_ALLOWED);
                return "redirect:/admin/create-platform";
            }
            // 后端验证：检查scopes是否包含空格（仅允许逗号分隔）
            if (scopes != null && scopes.matches(".*\\s+.*")) {
                redirectAttributes.addFlashAttribute("error", ServerErrorMessage.ADMIN_SCOPE_SPACE_NOT_ALLOWED);
                return "redirect:/admin/create-platform";
            }

            // 解析 scopes
            List<String> scopeList = null;
            if (scopes != null && !scopes.trim().isEmpty()) {
                scopeList = java.util.Arrays.stream(scopes.split(SimpleAkskServerConstant.SCOPE_DELIMITER))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            }

            ClientInfoResponse clientInfo = clientManagementService.createPlatformClient(name, scopeList);

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
    public String createSuccess(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String clientSecret,
            @RequestParam(required = false) String message,
            Model model) {
        // 支持两种来源：flash attributes（创建时）和 URL 参数（重置 Secret 时）
        if (clientId != null) model.addAttribute("clientId", clientId);
        if (clientSecret != null) model.addAttribute("clientSecret", clientSecret);
        if (message != null) model.addAttribute("message", message);
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
                             @RequestParam(required = false) String scopes,
                             RedirectAttributes redirectAttributes) {
        try {
            // 后端验证：检查scopes是否包含换行符
            if (scopes != null && scopes.matches(".*[\\r\\n]+.*")) {
                redirectAttributes.addFlashAttribute("error", ServerErrorMessage.ADMIN_SCOPE_NEWLINE_NOT_ALLOWED);
                return "redirect:/admin/create-user";
            }
            // 后端验证：检查scopes是否包含空格（仅允许逗号分隔）
            if (scopes != null && scopes.matches(".*\\s+.*")) {
                redirectAttributes.addFlashAttribute("error", ServerErrorMessage.ADMIN_SCOPE_SPACE_NOT_ALLOWED);
                return "redirect:/admin/create-user";
            }

            // 解析 scopes
            List<String> scopeList = null;
            if (scopes != null && !scopes.trim().isEmpty()) {
                scopeList = java.util.Arrays.stream(scopes.split(SimpleAkskServerConstant.SCOPE_DELIMITER))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            }

            ClientInfoResponse clientInfo = clientManagementService.createUserClient(ownerUserId, ownerUsername, name, scopeList);

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
     * 修改AKSK (RESTful API - 启用/禁用/权限范围)
     */
    @PatchMapping("/{clientId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> updateClient(@PathVariable String clientId,
                                                    @RequestBody UpdateClientRequest request) {
        try {
            // 处理 enabled 字段（保持原有逻辑）
            if (request.getEnabled() != null) {
                if (request.getEnabled()) {
                    clientManagementService.enableClient(clientId);
                    return ResponseEntity.ok(new ApiResponse(ServerErrorMessage.ADMIN_ENABLE_SUCCESS));
                } else {
                    clientManagementService.disableClient(clientId);
                    return ResponseEntity.ok(new ApiResponse(ServerErrorMessage.ADMIN_DISABLE_SUCCESS));
                }
            }

            // 处理 scopes 字段
            if (request.getScopes() != null) {
                clientManagementService.updateClientScopes(clientId, request.getScopes());
                return ResponseEntity.ok(new ApiResponse(ServerErrorMessage.ADMIN_SCOPES_UPDATE_SUCCESS));
            }

            // 处理 name 字段
            if (request.getName() != null) {
                clientManagementService.updateClientName(clientId, request.getName());
                return ResponseEntity.ok(new ApiResponse(ServerErrorMessage.ADMIN_NAME_UPDATE_SUCCESS));
            }

            // 处理 ownerUserId 字段（仅用户级AKSK）
            if (request.getOwnerUserId() != null) {
                clientManagementService.updateOwnerInfo(clientId, request.getOwnerUserId(), request.getOwnerUsername());
                return ResponseEntity.ok(new ApiResponse(ServerErrorMessage.ADMIN_OWNER_INFO_UPDATE_SUCCESS));
            }

            // 如果字段都没传
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(ServerErrorMessage.ADMIN_UPDATE_FIELD_REQUIRED));

        } catch (Exception e) {
            log.error("Failed to update client: {}", clientId, e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(String.format(ServerErrorMessage.ADMIN_UPDATE_FAILED, e.getMessage())));
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
                    userId, ClientType.USER.getValue(), 1, Integer.MAX_VALUE);

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
    public String tokenList(@RequestParam(required = false, defaultValue = SimpleAkskServerConstant.TOKEN_SOURCE_MYSQL) String source,
                            @RequestParam(required = false) String clientId,
                            @RequestParam(required = false) Integer clientType,
                            @RequestParam(required = false) TokenInfo.TokenStatus status,
                            @RequestParam(required = false) String search,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {

        PageResponse<TokenInfoResponse> pageResponse;

        if (SimpleAkskServerConstant.TOKEN_SOURCE_REDIS.equals(source)) {
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

        // 批量获取Client信息（用于显示Client名称和类型）
        List<String> clientIds = pageResponse.getData().stream()
                .map(TokenInfoResponse::getClientId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        Map<String, ClientInfoResponse> clientInfoMap = java.util.Collections.emptyMap();
        if (!clientIds.isEmpty()) {
            clientInfoMap = clientManagementService.batchGetClientsByIds(clientIds);
        }

        // 添加模型属性
        model.addAttribute("currentSource", source);  // 用于Tab高亮和逻辑判断
        model.addAttribute("tokens", pageResponse.getData());
        model.addAttribute("clientInfoMap", clientInfoMap);  // 添加Client信息Map
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
                model.addAttribute("error", ServerErrorMessage.ADMIN_TOKEN_NOT_FOUND);
                return "redirect:/admin/token";
            }
            model.addAttribute("token", tokenInfo);
            return "admin/token-detail";
        } catch (Exception e) {
            log.error("Failed to get token detail: {}", id, e);
            model.addAttribute("error", String.format(ServerErrorMessage.ADMIN_TOKEN_QUERY_FAILED, e.getMessage()));
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
                            @RequestParam(required = false) String scope,
                            Model model) {
        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .basicAuthentication(clientId, clientSecret)
                    .build();

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add(SimpleAkskServerConstant.OAUTH2_PARAM_GRANT_TYPE, SimpleAkskServerConstant.AUTHORIZATION_GRANT_TYPE);
            // 只有当scope不为空时才添加到请求中
            if (scope != null && !scope.trim().isEmpty()) {
                body.add(SimpleAkskServerConstant.OAUTH2_PARAM_SCOPE, scope);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            String port = environment.getProperty(SimpleAkskServerConstant.SPRING_PROPERTY_SERVER_PORT, SimpleAkskServerConstant.DEFAULT_SERVER_PORT);
            String tokenUrl = "http://localhost:" + port + "/oauth2/token";

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                model.addAttribute("success", true);
                model.addAttribute("accessToken", tokenResponse.get(SimpleAkskServerConstant.OAUTH2_RESPONSE_ACCESS_TOKEN));
                model.addAttribute("tokenType", tokenResponse.get(SimpleAkskServerConstant.OAUTH2_RESPONSE_TOKEN_TYPE));
                model.addAttribute("expiresIn", tokenResponse.get(SimpleAkskServerConstant.OAUTH2_RESPONSE_EXPIRES_IN));
                model.addAttribute("returnedScope", tokenResponse.get(SimpleAkskServerConstant.OAUTH2_PARAM_SCOPE));
                model.addAttribute("savedClientSecret", clientSecret);
            } else {
                model.addAttribute("error", String.format(ServerErrorMessage.ADMIN_TOKEN_REQUEST_FAILED, response.getStatusCode()));
            }
        } catch (HttpClientErrorException e) {
            log.error("Token request failed with client error", e);
            model.addAttribute("error", String.format(ServerErrorMessage.ADMIN_TOKEN_AUTH_FAILED, e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Token request failed", e);
            model.addAttribute("error", String.format(ServerErrorMessage.ADMIN_TOKEN_EXCHANGE_FAILED, e.getMessage()));
        }

        model.addAttribute("clientId", clientId);
        model.addAttribute("scope", scope);
        return "admin/token-test";
    }

    /**
     * 撤销 Token（Admin 页面用）
     */
    @PostMapping("/token/{id}/revoke")
    @ResponseBody
    public ResponseEntity<Void> adminRevokeToken(@PathVariable String id) {
        log.info("Admin revoking token: {}", id);
        tokenManagementService.revokeToken(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除 Token（Admin 页面用，先撤销再删除）
     */
    @DeleteMapping("/token/{id}")
    @ResponseBody
    public ResponseEntity<Void> adminDeleteToken(@PathVariable String id) {
        log.info("Admin deleting token: {}", id);
        tokenManagementService.deleteToken(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 清理过期Token（admin页面专用）
     */
    @DeleteMapping("/token/expired")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> adminDeleteExpiredTokens() {
        log.info("Admin deleting expired tokens");
        int deletedCount = tokenManagementService.deleteExpiredTokens();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_DELETED_COUNT, deletedCount);
        result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_MESSAGE, deletedCount > 0 ? ServerErrorMessage.ADMIN_TOKEN_CLEANUP_SUCCESS : ServerErrorMessage.ADMIN_TOKEN_CLEANUP_NONE);

        return ResponseEntity.ok(result);
    }

    /**
     * 撤销Token（token-test 页面用，通过 clientId/clientSecret 调标准 revoke 端点）
     */
    @PostMapping("/token/revoke")
    @ResponseBody
    public Map<String, Object> revokeToken(@RequestParam String clientId,
                                           @RequestParam String clientSecret,
                                           @RequestParam String token) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .basicAuthentication(clientId, clientSecret)
                    .build();

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add(SimpleAkskServerConstant.OAUTH2_PARAM_TOKEN, token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String port = environment.getProperty(SimpleAkskServerConstant.SPRING_PROPERTY_SERVER_PORT, SimpleAkskServerConstant.DEFAULT_SERVER_PORT);
            String revokeUrl = "http://localhost:" + port + "/oauth2/revoke";

            ResponseEntity<Void> response = restTemplate.postForEntity(
                    revokeUrl, new HttpEntity<>(body, headers), Void.class);

            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_SUCCESS, true);
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_STATUS, response.getStatusCode().value());
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_MESSAGE, ServerErrorMessage.ADMIN_TOKEN_REVOKE_SUCCESS);
        } catch (HttpClientErrorException e) {
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_SUCCESS, false);
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_STATUS, e.getStatusCode().value());
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_MESSAGE, String.format(ServerErrorMessage.ADMIN_TOKEN_REVOKE_FAILED, e.getResponseBodyAsString()));
        } catch (Exception e) {
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_SUCCESS, false);
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_MESSAGE, String.format(ServerErrorMessage.ADMIN_TOKEN_REVOKE_FAILED, e.getMessage()));
        }
        return result;
    }

    /**
     * Introspect Token
     */
    @PostMapping("/token/introspect")
    @ResponseBody
    public Map<String, Object> introspectToken(@RequestParam String clientId,
                                               @RequestParam String clientSecret,
                                               @RequestParam String token) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .basicAuthentication(clientId, clientSecret)
                    .build();

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add(SimpleAkskServerConstant.OAUTH2_PARAM_TOKEN, token);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String port = environment.getProperty(SimpleAkskServerConstant.SPRING_PROPERTY_SERVER_PORT, SimpleAkskServerConstant.DEFAULT_SERVER_PORT);
            String introspectUrl = "http://localhost:" + port + "/oauth2/introspect";

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    introspectUrl, new HttpEntity<>(body, headers), Map.class);

            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_SUCCESS, true);
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_STATUS, response.getStatusCode().value());
            result.putAll(response.getBody());
        } catch (HttpClientErrorException e) {
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_SUCCESS, false);
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_STATUS, e.getStatusCode().value());
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_MESSAGE, String.format(ServerErrorMessage.ADMIN_TOKEN_INTROSPECT_FAILED, e.getResponseBodyAsString()));
        } catch (Exception e) {
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_SUCCESS, false);
            result.put(SimpleAkskServerConstant.ADMIN_RESPONSE_MESSAGE, String.format(ServerErrorMessage.ADMIN_TOKEN_INTROSPECT_FAILED, e.getMessage()));
        }
        return result;
    }

    /**
     * Security Context演示页面
     */
    @GetMapping("/security-context-demo")
    public String securityContextDemoPage() {
        return "admin/security-context-demo";
    }

    /**
     * 批量撤销指定Client下所有活跃Token（Admin页面用）
     */
    @DeleteMapping("/{clientId}/token")
    @ResponseBody
    public ResponseEntity<BatchRevokeResponse> adminRevokeAllTokens(@PathVariable String clientId) {
        log.info("Admin batch revoking tokens for client: {}", clientId);
        return ResponseEntity.ok(tokenManagementService.revokeAllByClientId(clientId));
    }

    /**
     * 重置Client Secret（Admin页面用）
     */
    @PutMapping("/{clientId}/secret")
    @ResponseBody
    public ResponseEntity<ResetSecretResponse> adminResetSecret(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "true") boolean revokeTokens) {
        log.info("Admin resetting secret for client: {}, revokeTokens={}", clientId, revokeTokens);
        ResetSecretResponse response = clientManagementService.resetSecret(clientId, revokeTokens);
        return ResponseEntity.ok(response);
    }

}
