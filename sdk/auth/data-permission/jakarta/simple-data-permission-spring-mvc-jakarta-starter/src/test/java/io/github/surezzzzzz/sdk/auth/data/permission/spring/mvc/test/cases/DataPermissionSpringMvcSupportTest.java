package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DefaultDataPermissionEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.interceptor.DataPermissionOperationInterceptor;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.resolver.CurrentDataAccessPlanArgumentResolver;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataAccessPlanRestrictionVerifier;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DefaultDataPermissionFacade;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.VerifiedResourceDataGrantDocumentSource;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring MVC数据权限支持组件测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DataPermissionSpringMvcSupportTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 验证IAM人员主体与AKSK服务主体共用已验证授权文档桥接。
     */
    @Test
    void shouldReadDataGrantDocumentFromVerifiedHumanAndServiceContext() {
        DataGrantDocument document = document();
        authenticate("iam", ResourceSubjectType.HUMAN, ApplicationAuthorizationSubjectType.HUMAN, document);
        assertEquals(document, new VerifiedResourceDataGrantDocumentSource().currentDocument().orElse(null),
                "IAM人员主体必须读取已验证数据授权文档");

        authenticate("aksk", ResourceSubjectType.SERVICE, ApplicationAuthorizationSubjectType.SERVICE, document);
        assertEquals(document, new VerifiedResourceDataGrantDocumentSource().currentDocument().orElse(null),
                "AKSK服务主体必须读取已验证数据授权文档");
        log.info("IAM与AKSK已验证上下文共用数据授权文档桥接");
    }

    /**
     * 验证数据计划按完整DNF授权项校验业务目标，不能跨授权项拼接维度。
     */
    @Test
    void shouldRequireTargetToMatchCompleteGrant() {
        DataGrant first = restrictedGrant("tenant-a", "department-a");
        DataGrant second = restrictedGrant("tenant-b", "department-b");
        DataAccessPlan plan = new DefaultDataPermissionEvaluator().evaluate(new DataGrantDocument(
                        SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION, Arrays.asList(first, second)),
                new io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest("order", "read"));

        assertTrue(DataAccessPlanRestrictionVerifier.isTargetAllowed(plan, dimensions("tenant-a", "department-a")),
                "完整命中同一授权项必须允许");
        assertTrue(DataAccessPlanRestrictionVerifier.isTargetAllowed(plan, dimensions("tenant-b", "department-b")),
                "完整命中另一授权项必须允许");
        assertFalse(DataAccessPlanRestrictionVerifier.isTargetAllowed(plan, dimensions("tenant-a", "department-b")),
                "不得跨授权项拼接维度");
        assertThrows(DataPermissionAccessDeniedException.class,
                () -> DataAccessPlanRestrictionVerifier.requireTargetAllowed(plan, dimensions("tenant-c", "department-c")),
                "超出授权范围必须拒绝");
        log.info("数据范围校验保持grant内AND与grant间OR");
    }

    /**
     * 验证缺少已验证数据授权文档时门面失败关闭。
     */
    @Test
    void shouldDenyWhenVerifiedContextHasNoDataGrantDocument() {
        authenticate("iam", ResourceSubjectType.HUMAN, ApplicationAuthorizationSubjectType.HUMAN, null);
        DefaultDataPermissionFacade facade = new DefaultDataPermissionFacade(new DefaultDataPermissionEvaluator(),
                new VerifiedResourceDataGrantDocumentSource());

        assertThrows(DataPermissionAccessDeniedException.class, () -> facade.require("order", "read"),
                "缺少数据授权文档必须拒绝");
    }

    /**
     * 验证自定义门面不能通过返回拒绝计划或抛出运行时异常绕过DATA拒绝。
     *
     * @throws Exception 拦截器调用异常
     */
    @Test
    void shouldFailClosedWhenCustomFacadeReturnsDenyOrFails() throws Exception {
        HandlerMethod handler = new HandlerMethod(new DataOperationController(),
                DataOperationController.class.getMethod("orders"));
        DataPermissionOperationInterceptor deniedInterceptor = new DataPermissionOperationInterceptor(
                (resource, action) -> DataAccessPlan.deny());
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        assertFalse(deniedInterceptor.preHandle(new MockHttpServletRequest(), deniedResponse, handler),
                "自定义门面返回拒绝计划必须阻断请求");
        assertEquals(Integer.valueOf(403), Integer.valueOf(deniedResponse.getStatus()),
                "自定义门面返回拒绝计划必须返回403");

        DataPermissionOperationInterceptor failedInterceptor = new DataPermissionOperationInterceptor(
                (resource, action) -> {
                    throw new IllegalArgumentException("测试异常");
                });
        MockHttpServletResponse failedResponse = new MockHttpServletResponse();
        assertFalse(failedInterceptor.preHandle(new MockHttpServletRequest(), failedResponse, handler),
                "自定义门面评估失败必须阻断请求");
        assertEquals(Integer.valueOf(403), Integer.valueOf(failedResponse.getStatus()),
                "自定义门面评估失败必须返回403");
    }

    /**
     * 验证错误的数据计划参数类型不会退回到常规请求绑定。
     *
     * @throws Exception 反射调用异常
     */
    @Test
    void shouldRejectInvalidCurrentDataAccessPlanParameterType() throws Exception {
        Method method = InvalidPlanController.class.getMethod("orders", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        CurrentDataAccessPlanArgumentResolver resolver = new CurrentDataAccessPlanArgumentResolver();
        assertTrue(resolver.supportsParameter(parameter), "带数据计划注解的参数必须由受控解析器接管");
        assertThrows(DataPermissionAccessDeniedException.class, () -> resolver.resolveArgument(parameter, null,
                new ServletWebRequest(new MockHttpServletRequest()), null), "错误参数类型必须拒绝");
    }

    private DataGrantDocument document() {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(restrictedGrant("tenant-a", "department-a")));
    }

    private DataGrant restrictedGrant(String tenantId, String departmentId) {
        return new DataGrant("order", Collections.singletonList("read"), false, Arrays.asList(
                new DataConstraint("tenantId", DataConstraintOperator.IN, Collections.singletonList(tenantId)),
                new DataConstraint("departmentId", DataConstraintOperator.IN, Collections.singletonList(departmentId))));
    }

    private Map<String, String> dimensions(String tenantId, String departmentId) {
        Map<String, String> dimensions = new HashMap<String, String>();
        dimensions.put("tenantId", tenantId);
        dimensions.put("departmentId", departmentId);
        return dimensions;
    }

    private void authenticate(String sourceId, ResourceSubjectType resourceSubjectType,
                              ApplicationAuthorizationSubjectType authorizationSubjectType, DataGrantDocument document) {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                authorizationSubjectType, "subject-a", "app-a", true, Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.singletonList("order:read"), document, 1L,
                "manifest-a", "digest-a", now.minusSeconds(1L), now.plusSeconds(60L));
        VerifiedResourceContext context = new VerifiedResourceContext(new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId(sourceId), resourceSubjectType, "subject-a"), authorization,
                "request-a");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context, null,
                Collections.emptyList()));
    }

    /**
     * 标记DATA操作的测试Controller。
     */
    static final class DataOperationController {

        /**
         * 查询订单。
         */
        @DataPermissionOperation(resource = "order", action = "read")
        public void orders() {
        }
    }

    /**
     * 使用错误数据计划参数类型的测试Controller。
     */
    static final class InvalidPlanController {

        /**
         * 查询订单。
         *
         * @param plan 错误的数据计划参数
         */
        public void orders(@CurrentDataAccessPlan String plan) {
        }
    }
}
