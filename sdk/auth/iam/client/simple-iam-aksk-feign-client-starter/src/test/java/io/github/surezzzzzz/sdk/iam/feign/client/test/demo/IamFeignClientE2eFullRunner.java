package io.github.surezzzzzz.sdk.iam.feign.client.test.demo;

import io.github.surezzzzzz.sdk.iam.feign.client.*;
import io.github.surezzzzzz.sdk.iam.feign.client.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * Feign-SB2 端到端全族联调宿主（1.0.2 十接口）：Feign 契约接口消费面（区别于 RT 线的
 * core 接口 Bean），真实 aksk 底座令牌链（AKP client_credentials）→ IAM openapi 1.3.7。
 * 每族关键路径一发+wire 字段抽查，断言失败即抛。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class IamFeignClientE2eFullRunner {

    /**
     * admin 的公开主体（联调库既有账号，作 e2e 定位输入；env 可覆盖）。
     */
    private static final String adminSubjectId =
            System.getenv().getOrDefault("IAM_E2E_ADMIN_SUBJECT_ID", "4590442652332852");

    private IamFeignClientE2eFullRunner() {
    }

    /**
     * 联调入口。
     *
     * @param args 未使用
     * @throws Exception HTTP 失败时抛出
     */
    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(IamFeignClientE2eFullRunner.RunnerConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Collections.singletonMap("spring.main.banner-mode", "off"));
        ConfigurableApplicationContext context = app.run();
        try {
            IamUserFeignClient users = context.getBean(IamUserFeignClient.class);
            IamDepartmentFeignClient departments = context.getBean(IamDepartmentFeignClient.class);
            IamOpenRoleFeignClient openRoles = context.getBean(IamOpenRoleFeignClient.class);
            IamApplicationFeignClient applications = context.getBean(IamApplicationFeignClient.class);
            IamMessageFeignClient messages = context.getBean(IamMessageFeignClient.class);
            IamSessionFeignClient sessions = context.getBean(IamSessionFeignClient.class);
            IamPermissionFeignClient permissions = context.getBean(IamPermissionFeignClient.class);
            IamRoleCatalogFeignClient roleCatalog = context.getBean(IamRoleCatalogFeignClient.class);
            IamStatisticsFeignClient statistics = context.getBean(IamStatisticsFeignClient.class);
            IamUserGroupFeignClient userGroups = context.getBean(IamUserGroupFeignClient.class);

            // [回归] users 分页 + 详情
            IamUserPageResponse page = users.listUsers(null, null, null, 0, 20);
            if (page.number != 0 || page.content.isEmpty()) {
                throw new IllegalStateException("[回归] users 分页解析失败");
            }
            IamUserResponse detail = users.getUser(adminSubjectId);
            if (!"admin".equals(detail.username)) {
                throw new IllegalStateException("[回归] admin 定位回读失败");
            }
            List<String> admittedCodes = users.listPageAdmittedApplications(adminSubjectId);
            if (admittedCodes.isEmpty() || !admittedCodes.contains("iam")) {
                throw new IllegalStateException("[C] 准入清单必含 iam");
            }
            List<PageAdmittedApplicationResponse> admitted = users.listPageAdmittedApplicationDetails(adminSubjectId);
            boolean detailOk = false;
            for (PageAdmittedApplicationResponse item : admitted) {
                if ("iam".equals(item.code) && item.displayName != null && !item.displayName.isEmpty()) {
                    detailOk = true;
                }
            }
            if (!detailOk) {
                throw new IllegalStateException("[C] 详情必须含 iam 且 displayName 非空");
            }

            // [回归3] departments 全生命周期（读链+建/改/删临时部门，删后 404）
            List<IamDepartmentResponse> departmentList = departments.listDepartments();
            Long rootDepartmentId = null;
            for (IamDepartmentResponse department : departmentList) {
                if (department.parentId == null) {
                    rootDepartmentId = department.id;
                }
            }
            log.info("[回归3] listDepartments: {} 个 rootId={}", departmentList.size(), rootDepartmentId);
            if (departmentList.isEmpty() || rootDepartmentId == null) {
                throw new IllegalStateException("[回归3] 联调库必有 root 部门");
            }
            String tempDeptCode = "e2e-dept-" + System.currentTimeMillis();
            Map<String, Object> deptBody = new LinkedHashMap<String, Object>();
            deptBody.put("code", tempDeptCode);
            deptBody.put("name", "E2E 临时部门");
            deptBody.put("parentId", rootDepartmentId);
            deptBody.put("sortOrder", 99);
            deptBody.put("status", 1);
            IamDepartmentResponse tempDept = departments.createDepartment(deptBody);
            Long tempDeptId = tempDept.id;
            log.info("[回归3] createDepartment: id={} code={}", tempDeptId, tempDept.code);
            if (tempDeptId == null || !tempDeptCode.equals(tempDept.code)) {
                throw new IllegalStateException("[回归3] 建部门必须回读 id/code");
            }
            Map<String, Object> deptUpdate = new LinkedHashMap<String, Object>();
            deptUpdate.put("name", "E2E 临时部门v2");
            deptUpdate.put("parentId", rootDepartmentId);
            deptUpdate.put("sortOrder", 99);
            deptUpdate.put("status", 1);
            departments.updateDepartment(tempDeptId, deptUpdate);
            if (!"E2E 临时部门v2".equals(departments.getDepartment(tempDeptId).name)) {
                throw new IllegalStateException("[回归3] 改部门名称必须生效");
            }
            departments.deleteDepartment(tempDeptId);
            try {
                departments.getDepartment(tempDeptId);
                throw new IllegalStateException("[回归3] 删部门后读取必须 404");
            } catch (feign.FeignException.NotFound expected) {
                log.info("[回归3] departments 全生命周期闭环：列→读→建→改→删→404");
            }

            // [D] 应用目录（Spring Page wire）
            IamApplicationPageResponse applicationPage = applications.listApplications(0, 50);
            boolean directoryHasIam = false;
            for (IamApplicationResponse application : applicationPage.content) {
                if ("iam".equals(application.applicationCode)) {
                    directoryHasIam = application.applicationName != null && application.status != null;
                }
            }
            if (applicationPage.totalElements < 1 || !directoryHasIam) {
                throw new IllegalStateException("[D] 目录必须含 iam 三字段");
            }

            // [E1] 发信→查信→批次
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("recipientSubjectIds", Collections.singletonList(adminSubjectId));
            body.put("title", "client-e2e-feign");
            body.put("content", "openapi full-clan verification");
            IamMessageSendResultResponse sendResult = messages.sendMessage(body);
            if (sendResult.sendBatchId == null || sendResult.recipientCount < 1) {
                throw new IllegalStateException("[E1] 发信必须返回批次与命中数");
            }
            List<IamMessageResponse> messageList = messages.listMessagesBySubject(adminSubjectId, 1, 5);
            boolean messageFound = false;
            for (IamMessageResponse message : messageList) {
                if ("client-e2e-feign".equals(message.title)) {
                    messageFound = true;
                }
            }
            if (!messageFound) {
                throw new IllegalStateException("[E1] 查信必须命中刚发批次");
            }
            if (!sendResult.sendBatchId.equals(
                    messages.getMessageBatch(sendResult.sendBatchId).sendBatchId)) {
                throw new IllegalStateException("[E1] 批次详情回读失败");
            }

            // [E2] sessions 查（踢略：当前会话可能属浏览器调试，验证查询与 sessionId 解析）
            List<IamSessionResponse> sessionList = sessions.listSessionsBySubject(adminSubjectId, 1, 10);
            log.info("[E2] sessions: {} 条", sessionList.size());

            // [E3] permissions
            List<IamPermissionResponse> permissionList = permissions.listPermissions(null, 1, 50);
            if (permissionList.isEmpty() || permissionList.get(0).code == null) {
                throw new IllegalStateException("[E3] 权限清单解析失败");
            }

            // [E4] role-catalog
            List<IamRoleCatalogEntryResponse> roleEntries = roleCatalog.listRoleCatalog(null, 1, 50);
            Long catalogRoleId = null;
            for (IamRoleCatalogEntryResponse entry : roleEntries) {
                if ("iam_user".equals(entry.code) && entry.id != null) {
                    catalogRoleId = entry.id;
                }
            }
            if (catalogRoleId == null) {
                throw new IllegalStateException("[E4] 角色目录必须含内置 iam_user");
            }

            // [E5] user-groups 生命周期（建→读→改→成员→删→404）
            Map<String, Object> group = new LinkedHashMap<String, Object>();
            String groupCode = "client-e2e-feign-" + System.currentTimeMillis();
            group.put("code", groupCode);
            group.put("name", groupCode);
            IamUserGroupResponse created = userGroups.createUserGroup(group);
            Long groupId = created.id;
            if (groupId == null || !groupCode.equals(created.code)) {
                throw new IllegalStateException("[E5] 建组回读失败");
            }
            Map<String, Object> rename = new LinkedHashMap<String, Object>();
            rename.put("name", groupCode + "-v2");
            userGroups.updateUserGroup(groupId, rename);
            if (!(groupCode + "-v2").equals(userGroups.getUserGroup(groupId).name)) {
                throw new IllegalStateException("[E5] 改组未生效");
            }
            userGroups.assignUserGroupUser(groupId, adminSubjectId);
            boolean memberIn = false;
            for (IamUserGroupMemberResponse member : userGroups.listUserGroupUsers(groupId, 1, 10)) {
                if (adminSubjectId.equals(member.subjectId)) {
                    memberIn = true;
                }
            }
            if (!memberIn) {
                throw new IllegalStateException("[E5] 加成员失败");
            }
            userGroups.revokeUserGroupUser(groupId, adminSubjectId);
            userGroups.deleteUserGroup(groupId);
            try {
                userGroups.getUserGroup(groupId);
                throw new IllegalStateException("[E5] 删组后读取必须 404");
            } catch (feign.FeignException.NotFound expected) {
                log.info("[E5] 全生命周期闭环（404=FeignException.NotFound）");
            }
            boolean groupDirectoryClean = true;
            for (IamUserGroupResponse listedGroup : userGroups.listUserGroups(null, null, 1, 50)) {
                if (groupCode.equals(listedGroup.code)) {
                    groupDirectoryClean = false;
                }
            }
            if (!groupDirectoryClean) {
                throw new IllegalStateException("[E5] 删组后目录不得仍含该组");
            }
            log.info("[E5b] listUserGroups 目录收敛实证");

            // [E6] statistics（嵌套 wire）
            IamStatisticsResponse stats = statistics.getStatistics();
            if (stats.counts == null || stats.counts.user < 1) {
                throw new IllegalStateException("[E6] 统计嵌套段解析失败");
            }
            log.info("[E6] statistics: counts.user={} stats.activeSessions={}", stats.counts.user,
                    stats.stats == null ? -1 : stats.stats.activeSessions);

            // [E7] users 写方法生命周期（建→读→角色挂/摘→改→停/启→重置密码→删→404）
            String e2eUsername = "client-e2e-u" + System.currentTimeMillis();
            Map<String, Object> createUserBody = new LinkedHashMap<String, Object>();
            createUserBody.put("username", e2eUsername);
            createUserBody.put("password", "E2eFull!2026a");
            createUserBody.put("displayName", "E2E 全族用户");
            createUserBody.put("departmentId", rootDepartmentId);
            IamUserResponse createdUser = users.createUser(createUserBody);
            String e2eSubjectId = createdUser.subjectId;
            log.info("[E7] createUser: subjectId={} username={}", e2eSubjectId, createdUser.username);
            if (e2eSubjectId == null || !e2eUsername.equals(createdUser.username)) {
                throw new IllegalStateException("[E7] 建用户必须回读 subjectId/username");
            }
            List<String> rolesBeforeAssign = users.getUserRoles(e2eSubjectId);
            users.assignRole(e2eSubjectId, catalogRoleId);
            List<String> rolesAfterAssign = users.getUserRoles(e2eSubjectId);
            if (!rolesAfterAssign.containsAll(rolesBeforeAssign)
                    || rolesAfterAssign.size() != rolesBeforeAssign.size() + 1) {
                throw new IllegalStateException("[E7] 挂角色后角色清单必须恰增一枚");
            }
            users.revokeRole(e2eSubjectId, catalogRoleId);
            if (users.getUserRoles(e2eSubjectId).size() != rolesBeforeAssign.size()) {
                throw new IllegalStateException("[E7] 摘角色后角色清单必须还原");
            }
            Map<String, Object> updateUserBody = new LinkedHashMap<String, Object>();
            updateUserBody.put("displayName", "E2E 全族用户v2");
            users.updateUser(e2eSubjectId, updateUserBody);
            if (!"E2E 全族用户v2".equals(users.getUser(e2eSubjectId).displayName)) {
                throw new IllegalStateException("[E7] 改 displayName 必须生效");
            }
            users.disableUser(e2eSubjectId);
            users.enableUser(e2eSubjectId);
            users.getUser(e2eSubjectId);
            Map<String, Object> resetBody = new LinkedHashMap<String, Object>();
            resetBody.put("newPassword", "E2eFull!2026b");
            users.resetPassword(e2eSubjectId, resetBody);
            users.deleteUser(e2eSubjectId);
            try {
                users.getUser(e2eSubjectId);
                throw new IllegalStateException("[E7] 删用户后读取必须 404");
            } catch (feign.FeignException.NotFound expected) {
                log.info("[E7] users 写方法生命周期闭环：建→角色挂/摘→改→停/启→重置密码→删→404");
            }

            // [E8] open-roles 全族（读链 4 + 建角色→规则三态→挂载/摘除）
            // 受委托角色族的固定目标应用不可为内置应用（内置=409 语义），取联调库非内置应用 kms
            long targetApplicationId = Long.parseLong(
                    System.getenv().getOrDefault("IAM_E2E_OPEN_TARGET_APPLICATION_ID", "3"));
            IamTargetApplicationResponse target = openRoles.getTargetApplication(targetApplicationId);
            log.info("[E8] getTargetApplication: code={} name={}", target.applicationCode, target.name);
            if (target.applicationCode == null || target.status == null) {
                throw new IllegalStateException("[E8] 目标应用必须回读 code/status");
            }
            IamPermissionManifestResponse manifest = openRoles.getTargetApplicationManifest(targetApplicationId);
            log.info("[E8] manifest: version={} pages={} apis={}", manifest.manifestVersion,
                    manifest.pagePermissions.size(), manifest.apiPermissions.size());
            if (manifest.manifestVersion == null || manifest.manifestDigest == null) {
                throw new IllegalStateException("[E8] 权限清单必须回读 version/digest");
            }
            IamOrganizationDirectoryResponse directory = openRoles.listOrganizationDirectoryDepartments(rootDepartmentId);
            log.info("[E8] directory: departments={} digest={}", directory.departments.size(),
                    directory.directoryDigest);
            if (directory.departments.isEmpty() || directory.directoryDigest == null) {
                throw new IllegalStateException("[E8] 组织目录必须非空且带 digest");
            }
            IamOrganizationMemberResponse adminMember =
                    openRoles.getOrganizationDirectoryMember(rootDepartmentId, adminSubjectId);
            if (!adminSubjectId.equals(adminMember.subjectId)) {
                throw new IllegalStateException("[E8] 成员定位必须回读同主体");
            }
            Map<String, Object> openRoleBody = new LinkedHashMap<String, Object>();
            openRoleBody.put("externalId", UUID.randomUUID().toString());
            openRoleBody.put("applicationId", targetApplicationId);
            openRoleBody.put("rootDepartmentId", rootDepartmentId);
            openRoleBody.put("name", "e2e-openrole-" + System.currentTimeMillis());
            openRoleBody.put("description", "e2e full clan");
            IamOpenRoleResponse openRole = openRoles.createOpenRole(openRoleBody);
            String openRoleId = openRole.openRoleId;
            log.info("[E8] createOpenRole: id={} revision={}", openRoleId, openRole.revision);
            if (openRoleId == null || openRole.revision < 1) {
                throw new IllegalStateException("[E8] 建角色必须回读 id/revision");
            }
            IamOpenRolePageResponse openRolePage = openRoles.listOpenRoles(1, 50);
            boolean listedByExternal = false;
            for (IamOpenRoleResponse item : openRolePage.items) {
                if (openRoleBody.get("externalId").equals(item.externalId)) {
                    listedByExternal = true;
                }
            }
            if (!listedByExternal || openRolePage.page != 1) {
                throw new IllegalStateException("[E8] 角色分页必须命中新角色且页码 1 起");
            }
            if (openRoles.getOpenRole(openRoleId).revision != openRole.revision) {
                throw new IllegalStateException("[E8] 角色详情必须回读同 revision");
            }
            List<String> pageGrant = manifest.pagePermissions.isEmpty()
                    ? Collections.<String>emptyList()
                    : manifest.pagePermissions.subList(0, 1);
            List<String> apiGrant = manifest.apiPermissions.isEmpty()
                    ? Collections.<String>emptyList()
                    : manifest.apiPermissions.subList(0, 1);
            // 建角色不初始化规则（读规则 404 态）；写规则永远携带 If-Match（版本源=角色行 revision）
            try {
                openRoles.getOpenRoleRule(openRoleId, targetApplicationId);
                throw new IllegalStateException("[E8] 新角色初始不得已有规则");
            } catch (feign.FeignException.NotFound expected) {
                log.info("[E8] 新角色规则 404 态实证");
            }
            Map<String, Object> ruleBody = new LinkedHashMap<String, Object>();
            ruleBody.put("manifestVersion", manifest.manifestVersion);
            ruleBody.put("manifestDigest", manifest.manifestDigest);
            ruleBody.put("pagePermissions", pageGrant);
            ruleBody.put("apiPermissions", apiGrant);
            IamOpenRoleRuleResponse ruleFirst = openRoles.putOpenRoleRule(openRoleId, targetApplicationId,
                    "\"open-role:" + openRoleId + ":" + openRole.revision + "\"", ruleBody);
            log.info("[E8] putOpenRoleRule 首写: revision={}", ruleFirst.revision);
            IamOpenRoleRuleResponse ruleByRead = openRoles.getOpenRoleRule(openRoleId, targetApplicationId);
            if (ruleByRead.revision != ruleFirst.revision
                    || ruleByRead.pagePermissions.size() != pageGrant.size()) {
                throw new IllegalStateException("[E8] 规则回读必须同版本同页面码数");
            }
            // 二次写换内容（清码）：内容不变时服务端不递增版本（幂等语义）
            Map<String, Object> ruleBodyClear = new LinkedHashMap<String, Object>();
            ruleBodyClear.put("manifestVersion", manifest.manifestVersion);
            ruleBodyClear.put("manifestDigest", manifest.manifestDigest);
            ruleBodyClear.put("pagePermissions", Collections.emptyList());
            ruleBodyClear.put("apiPermissions", Collections.emptyList());
            IamOpenRoleRuleResponse ruleSecond = openRoles.putOpenRoleRule(openRoleId, targetApplicationId,
                    "\"open-role:" + openRoleId + ":" + ruleFirst.revision + "\"", ruleBodyClear);
            if (ruleSecond.revision <= ruleFirst.revision) {
                throw new IllegalStateException("[E8] If-Match 二次写 revision 必须递增");
            }
            openRoles.deleteOpenRoleRule(openRoleId, targetApplicationId,
                    "\"open-role:" + openRoleId + ":" + ruleSecond.revision + "\"");
            try {
                openRoles.getOpenRoleRule(openRoleId, targetApplicationId);
                throw new IllegalStateException("[E8] 删规则后读取必须 404");
            } catch (feign.FeignException.NotFound expected) {
                log.info("[E8] 规则三态闭环：首写→回读→If-Match 二写→删→404");
            }
            Map<String, Object> mountDeptBody = new LinkedHashMap<String, Object>();
            mountDeptBody.put("code", "e2e-mount-" + System.currentTimeMillis());
            mountDeptBody.put("name", "E2E 挂载部门");
            mountDeptBody.put("parentId", rootDepartmentId);
            mountDeptBody.put("sortOrder", 99);
            mountDeptBody.put("status", 1);
            IamDepartmentResponse mountDept = departments.createDepartment(mountDeptBody);
            IamOpenRoleDepartmentPageResponse mountPageBefore = openRoles.listOpenRoleDepartments(openRoleId);
            openRoles.mountOpenRoleToDepartment(mountDept.id, openRoleId,
                    "\"open-role:" + openRoleId + ":" + mountPageBefore.revision + "\"");
            IamOpenRoleDepartmentPageResponse mountPageAfter = openRoles.listOpenRoleDepartments(openRoleId);
            boolean mounted = false;
            for (IamOpenRoleDepartmentPageResponse.Item mountItem : mountPageAfter.items) {
                if (mountDept.id.equals(mountItem.departmentId)) {
                    mounted = true;
                }
            }
            if (!mounted || mountPageAfter.revision <= mountPageBefore.revision) {
                throw new IllegalStateException("[E8] 挂载后列表必含新部门且 revision 递增");
            }
            openRoles.unmountOpenRoleFromDepartment(mountDept.id, openRoleId,
                    "\"open-role:" + openRoleId + ":" + mountPageAfter.revision + "\"");
            for (IamOpenRoleDepartmentPageResponse.Item mountItem
                    : openRoles.listOpenRoleDepartments(openRoleId).items) {
                if (mountDept.id.equals(mountItem.departmentId)) {
                    throw new IllegalStateException("[E8] 摘除后不得仍挂载");
                }
            }
            departments.deleteDepartment(mountDept.id);
            log.info("[E8] open-roles 全族闭环：读链4+建/列/读+规则三态+挂载/摘除（角色本体无删除契约，测后清理）");

            log.info("[IAM-CLIENT-E2E-FULL] ALL CLANS PASSED (Feign-SB2)");
        } finally {
            context.close();
        }
    }

    /**
     * 宿主配置：零自定义业务 Bean；@EnableFeignClients 激活契约接口扫描。
     */
    @Configuration
    @EnableAutoConfiguration
    @EnableFeignClients(basePackages = "io.github.surezzzzzz.sdk.iam.feign.client")
    static class RunnerConfiguration {
    }
}
