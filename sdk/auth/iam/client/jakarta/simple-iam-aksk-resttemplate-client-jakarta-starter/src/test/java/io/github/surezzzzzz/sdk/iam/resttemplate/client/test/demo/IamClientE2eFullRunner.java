package io.github.surezzzzzz.sdk.iam.resttemplate.client.test.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * RT-SB3(jakarta) 端到端全族联调宿主（1.0.2 十接口 48 方法全链）：真实 aksk 底座令牌链
 * （AKP client_credentials）→ 已部署 IAM openapi 1.3.7 全族验证。
 *
 * <p>既有六步（users/departments 读链）保留为回归段；另含 departments 全生命周期、
 * 页面准入详情、可信应用目录、messages 三方法、sessions 两方法、permissions、
 * role-catalog、user-groups 全生命周期（建→读→改→成员加/查/移→删→删后 404→目录收敛）、
 * statistics、users 写方法生命周期、open-roles 全族（读链+规则 If-Match 三态+挂载/摘除）。
 * 断言失败即抛。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public final class IamClientE2eFullRunner {

    /**
     * admin 的公开主体（联调库既有账号，作 e2e 定位输入；env 可覆盖）。
     */
    private static final String adminSubjectId =
            System.getenv().getOrDefault("IAM_E2E_ADMIN_SUBJECT_ID", "4590442652332852");

    private IamClientE2eFullRunner() {
    }

    /**
     * 联调入口：先跑既有六步回归，再跑全族八段。
     *
     * @param args 未使用
     * @throws Exception HTTP 失败时抛出
     */
    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(IamClientE2eFullRunner.RunnerConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Collections.singletonMap("spring.main.banner-mode", "off"));
        ConfigurableApplicationContext context = app.run();
        try {
            IamUserClient users = context.getBean(IamUserClient.class);
            IamDepartmentClient departments = context.getBean(IamDepartmentClient.class);
            IamOpenRoleClient openRoles = context.getBean(IamOpenRoleClient.class);
            IamApplicationClient applications = context.getBean(IamApplicationClient.class);
            IamMessageClient messages = context.getBean(IamMessageClient.class);
            IamSessionClient sessions = context.getBean(IamSessionClient.class);
            IamPermissionClient permissions = context.getBean(IamPermissionClient.class);
            IamRoleCatalogClient roleCatalog = context.getBean(IamRoleCatalogClient.class);
            IamStatisticsClient statistics = context.getBean(IamStatisticsClient.class);
            IamUserGroupClient userGroups = context.getBean(IamUserGroupClient.class);

            // ===== 既有六步回归（users/departments 读链，见 IamClientE2eRunner 文档）=====
            IamSpringPage<IamUser> page = users.listUsers(null, null, null, 0, 20);
            if (page.getNumber() != 0 || page.getContent().isEmpty()) {
                throw new IllegalStateException("[回归1] users 分页解析失败");
            }
            IamUser detail = users.getUser(adminSubjectId);
            if (!"admin".equals(detail.getUsername())) {
                throw new IllegalStateException("[回归2] admin 定位回读失败");
            }
            List<String> admittedCodes = users.listPageAdmittedApplications(adminSubjectId);
            log.info("[C] listPageAdmittedApplications: {} 个", admittedCodes.size());
            if (admittedCodes.isEmpty() || !admittedCodes.contains("iam")) {
                throw new IllegalStateException("[C] admin 页面准入清单必含 iam");
            }
            boolean admittedHasIam = false;
            boolean admittedHasName = false;
            List<PageAdmittedApplication> admittedDetails = users.listPageAdmittedApplicationDetails(adminSubjectId);
            for (PageAdmittedApplication item : admittedDetails) {
                if ("iam".equals(item.getCode())) {
                    admittedHasIam = true;
                    admittedHasName = item.getDisplayName() != null && !item.getDisplayName().isEmpty();
                }
            }
            if (!admittedHasIam || !admittedHasName) {
                throw new IllegalStateException("[C] 详情必须含 iam 且 displayName 非空");
            }

            // ===== [回归3] departments 全生命周期（读链+建/改/删临时部门，删后 404）=====
            List<IamDepartment> departmentList = departments.listDepartments();
            Long rootDepartmentId = null;
            for (IamDepartment department : departmentList) {
                if (department.getParentId() == null) {
                    rootDepartmentId = department.getId();
                }
            }
            log.info("[回归3] listDepartments: {} 个 rootId={}", departmentList.size(), rootDepartmentId);
            if (departmentList.isEmpty() || rootDepartmentId == null) {
                throw new IllegalStateException("[回归3] 联调库必有 root 部门");
            }
            IamDepartment rootByRead = departments.getDepartment(rootDepartmentId);
            if (rootByRead.getId() == null || rootByRead.getCode() == null) {
                throw new IllegalStateException("[回归3] 部门详情必须回读 id/code");
            }
            String tempDeptCode = "e2e-dept-" + System.currentTimeMillis();
            IamDepartment tempDept = departments.createDepartment(tempDeptCode, "E2E 临时部门",
                    rootDepartmentId, 99, 1, null);
            Long tempDeptId = tempDept.getId();
            log.info("[回归3] createDepartment: id={} code={}", tempDeptId, tempDept.getCode());
            if (tempDeptId == null || !tempDeptCode.equals(tempDept.getCode())) {
                throw new IllegalStateException("[回归3] 建部门必须回读 id/code");
            }
            departments.updateDepartment(tempDeptId, "E2E 临时部门v2", rootDepartmentId, 99, 1);
            IamDepartment tempUpdated = departments.getDepartment(tempDeptId);
            if (!"E2E 临时部门v2".equals(tempUpdated.getName())) {
                throw new IllegalStateException("[回归3] 改部门名称必须生效");
            }
            departments.deleteDepartment(tempDeptId);
            try {
                departments.getDepartment(tempDeptId);
                throw new IllegalStateException("[回归3] 删部门后读取必须 404");
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound expected) {
                log.info("[回归3] departments 全生命周期闭环：列→读→建→改→删→404");
            }

            // ===== [D] 可信应用目录 =====
            IamSpringPage<IamApplication> applicationPage = applications.listApplications(0, 50);
            log.info("[D] listApplications: totalElements={}", applicationPage.getTotalElements());
            if (applicationPage.getTotalElements() < 1 || applicationPage.getContent().isEmpty()) {
                throw new IllegalStateException("[D] 联调库必有可信应用");
            }
            boolean directoryHasIam = false;
            for (IamApplication application : applicationPage.getContent()) {
                if ("iam".equals(application.getApplicationCode())) {
                    directoryHasIam = application.getApplicationName() != null
                            && application.getStatus() != null;
                }
            }
            if (!directoryHasIam) {
                throw new IllegalStateException("[D] 目录必须含 iam 三字段全解析");
            }

            // ===== [E1] messages 发信→查信→批次 =====
            IamMessageSendRequest sendRequest = IamMessageSendRequest.builder()
                    .recipientSubjectIds(Collections.singletonList(adminSubjectId))
                    .title("client-e2e-full")
                    .content("openapi full-clan verification")
                    .build();
            IamMessageSendResult sendResult = messages.sendMessage(sendRequest);
            log.info("[E1] sendMessage: batchId={} recipients={}", sendResult.getSendBatchId(),
                    sendResult.getRecipientCount());
            if (sendResult.getSendBatchId() == null || sendResult.getRecipientCount() < 1) {
                throw new IllegalStateException("[E1] 发信必须返回批次与命中数");
            }
            List<IamMessage> messageList = messages.listBySubject(adminSubjectId, 1, 5);
            boolean messageFound = false;
            for (IamMessage message : messageList) {
                if ("client-e2e-full".equals(message.getTitle())) {
                    messageFound = true;
                }
            }
            if (!messageFound) {
                throw new IllegalStateException("[E1] 查信必须命中刚发的批次标题");
            }
            IamMessageBatch batch = messages.getBatch(sendResult.getSendBatchId());
            if (!sendResult.getSendBatchId().equals(batch.getSendBatchId())) {
                throw new IllegalStateException("[E1] 批次详情必须回读同批次");
            }

            // ===== [E2] sessions 查→踢（踢 admin 当前会话，后续请求由客户端令牌不受影响）=====
            List<IamSession> sessionList = sessions.listBySubject(adminSubjectId, 1, 10);
            log.info("[E2] listBySubject: {} 条", sessionList.size());
            if (!sessionList.isEmpty()) {
                String sessionId = sessionList.get(0).getSessionId();
                sessions.revokeSession(sessionId);
                List<IamSession> afterRevoke = sessions.listBySubject(adminSubjectId, 1, 10);
                for (IamSession session : afterRevoke) {
                    if (sessionId.equals(session.getSessionId())) {
                        throw new IllegalStateException("[E2] 被踢会话不得仍在列表");
                    }
                }
                log.info("[E2] revokeSession: {} 已踢除并实证消失", sessionId);
            }

            // ===== [E3] permissions =====
            List<IamPermission> permissionList = permissions.listPermissions(null, 1, 50);
            log.info("[E3] listPermissions: {} 条", permissionList.size());
            if (permissionList.isEmpty()) {
                throw new IllegalStateException("[E3] 权限清单必非空");
            }
            boolean permissionCodeShape = permissionList.get(0).getCode() != null
                    && permissionList.get(0).getType() != null;
            if (!permissionCodeShape) {
                throw new IllegalStateException("[E3] 权限 code/type 必须解析");
            }

            // ===== [E4] role-catalog =====
            List<IamRoleCatalogEntry> roleEntries = roleCatalog.listRoles(null, 1, 50);
            log.info("[E4] listRoles: {} 条", roleEntries.size());
            if (roleEntries.isEmpty()) {
                throw new IllegalStateException("[E4] 角色目录必非空");
            }
            Long catalogRoleId = null;
            for (IamRoleCatalogEntry entry : roleEntries) {
                if ("iam_user".equals(entry.getCode()) && entry.getId() != null) {
                    catalogRoleId = entry.getId();
                }
            }
            if (catalogRoleId == null) {
                throw new IllegalStateException("[E4] 角色目录必须含内置 iam_user");
            }

            // ===== [E5] user-groups 全生命周期 =====
            String groupCode = "client-e2e-full-" + System.currentTimeMillis();
            IamUserGroup created = userGroups.createGroup(groupCode, groupCode, "e2e full lifecycle");
            log.info("[E5] createGroup: id={} code={}", created.getId(), created.getCode());
            if (created.getId() == null || !groupCode.equals(created.getCode())) {
                throw new IllegalStateException("[E5] 建组必须回读 id/code");
            }
            Long groupId = created.getId();
            IamUserGroup fetched = userGroups.getGroup(groupId);
            if (!groupCode.equals(fetched.getCode())) {
                throw new IllegalStateException("[E5] 单组读取失败");
            }
            userGroups.updateGroup(groupId, groupCode + "-v2", null);
            IamUserGroup updated = userGroups.getGroup(groupId);
            if (!(groupCode + "-v2").equals(updated.getName())) {
                throw new IllegalStateException("[E5] 改组名称必须生效");
            }
            userGroups.assignUser(groupId, adminSubjectId);
            List<IamUserGroupMember> members = userGroups.listGroupUsers(groupId, 1, 10);
            boolean memberIn = false;
            for (IamUserGroupMember member : members) {
                if (adminSubjectId.equals(member.getSubjectId())) {
                    memberIn = true;
                }
            }
            if (!memberIn) {
                throw new IllegalStateException("[E5] 加成员后成员列表必含 admin");
            }
            userGroups.revokeUser(groupId, adminSubjectId);
            List<IamUserGroupMember> membersAfterRevoke = userGroups.listGroupUsers(groupId, 1, 10);
            for (IamUserGroupMember member : membersAfterRevoke) {
                if (adminSubjectId.equals(member.getSubjectId())) {
                    throw new IllegalStateException("[E5] 移成员后不得仍在列表");
                }
            }
            userGroups.deleteGroup(groupId);
            try {
                userGroups.getGroup(groupId);
                throw new IllegalStateException("[E5] 删组后读取必须 404");
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound expected) {
                log.info("[E5] 全生命周期闭环：建→读→改→成员加/查/移→删→404");
            }
            List<IamUserGroup> groupDirectory = userGroups.listGroups(null, null, 1, 50);
            boolean groupDirectoryClean = true;
            for (IamUserGroup group : groupDirectory) {
                if (groupCode.equals(group.getCode())) {
                    groupDirectoryClean = false;
                }
            }
            if (!groupDirectoryClean) {
                throw new IllegalStateException("[E5] 删组后目录不得仍含该组");
            }
            log.info("[E5b] listGroups: {} 条（删组后目录已收敛）", groupDirectory.size());

            // ===== [E6] statistics =====
            IamStatistics stats = statistics.getStatistics();
            log.info("[E6] getStatistics: user={} role={} activeSessions={}", stats.getUser(), stats.getRole(),
                    stats.getActiveSessions());
            if (stats.getUser() < 1) {
                throw new IllegalStateException("[E6] 统计用户数必≥1");
            }

            // ===== [E7] users 写方法生命周期（建→读→角色挂/摘→改→停/启→重置密码→删→404）=====
            String e2eUsername = "client-e2e-u" + System.currentTimeMillis();
            IamUser createdUser = users.createUser(e2eUsername, "E2eFull!2026a", "E2E 全族用户",
                    null, null, rootDepartmentId);
            String e2eSubjectId = createdUser.getSubjectId();
            log.info("[E7] createUser: subjectId={} username={}", e2eSubjectId, createdUser.getUsername());
            if (e2eSubjectId == null || !e2eUsername.equals(createdUser.getUsername())) {
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
            List<String> rolesAfterRevoke = users.getUserRoles(e2eSubjectId);
            if (rolesAfterRevoke.size() != rolesBeforeAssign.size()) {
                throw new IllegalStateException("[E7] 摘角色后角色清单必须还原");
            }
            users.updateUser(e2eSubjectId, "E2E 全族用户v2", null, null);
            IamUser updatedUser = users.getUser(e2eSubjectId);
            if (!"E2E 全族用户v2".equals(updatedUser.getDisplayName())) {
                throw new IllegalStateException("[E7] 改 displayName 必须生效");
            }
            users.disableUser(e2eSubjectId);
            users.enableUser(e2eSubjectId);
            users.getUser(e2eSubjectId);
            users.resetPassword(e2eSubjectId, "E2eFull!2026b");
            users.deleteUser(e2eSubjectId);
            try {
                users.getUser(e2eSubjectId);
                throw new IllegalStateException("[E7] 删用户后读取必须 404");
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound expected) {
                log.info("[E7] users 写方法生命周期闭环：建→角色挂/摘→改→停/启→重置密码→删→404");
            }

            // ===== [E8] open-roles 全族（读链 4 + 建角色→规则三态→挂载/摘除）=====
            // 受委托角色族的固定目标应用不可为内置应用（内置=409 语义），取联调库非内置应用 kms
            long targetApplicationId = Long.parseLong(
                    System.getenv().getOrDefault("IAM_E2E_OPEN_TARGET_APPLICATION_ID", "3"));
            IamTargetApplication target = openRoles.getTargetApplication(targetApplicationId);
            log.info("[E8] getTargetApplication: code={} name={}", target.getApplicationCode(), target.getName());
            if (target.getApplicationCode() == null || target.getStatus() == null) {
                throw new IllegalStateException("[E8] 目标应用必须回读 code/status");
            }
            IamPermissionManifest manifest = openRoles.getTargetApplicationManifest(targetApplicationId);
            log.info("[E8] manifest: version={} pages={} apis={}", manifest.getManifestVersion(),
                    manifest.getPagePermissions().size(), manifest.getApiPermissions().size());
            if (manifest.getManifestVersion() == null || manifest.getManifestDigest() == null) {
                throw new IllegalStateException("[E8] 权限清单必须回读 version/digest");
            }
            IamOrganizationDirectory directory = openRoles.listOrganizationDirectoryDepartments(rootDepartmentId);
            log.info("[E8] directory: departments={} digest={}", directory.getDepartments().size(),
                    directory.getDirectoryDigest());
            if (directory.getDepartments().isEmpty() || directory.getDirectoryDigest() == null) {
                throw new IllegalStateException("[E8] 组织目录必须非空且带 digest");
            }
            IamOrganizationMember adminMember = openRoles.getOrganizationDirectoryMember(rootDepartmentId,
                    adminSubjectId);
            if (!adminSubjectId.equals(adminMember.getSubjectId())) {
                throw new IllegalStateException("[E8] 成员定位必须回读同主体");
            }
            String openRoleExternalId = UUID.randomUUID().toString();
            IamOpenRole openRole = openRoles.createOpenRole(openRoleExternalId, targetApplicationId,
                    rootDepartmentId, "e2e-openrole-" + System.currentTimeMillis(), "e2e full clan");
            String openRoleId = openRole.getOpenRoleId();
            log.info("[E8] createOpenRole: id={} revision={}", openRoleId, openRole.getRevision());
            if (openRoleId == null || openRole.getRevision() < 1) {
                throw new IllegalStateException("[E8] 建角色必须回读 id/revision");
            }
            IamOpenRolePage openRolePage = openRoles.listOpenRoles(1, 50);
            boolean listedByExternal = false;
            for (IamOpenRole item : openRolePage.getItems()) {
                if (openRoleExternalId.equals(item.getExternalId())) {
                    listedByExternal = true;
                }
            }
            if (!listedByExternal || openRolePage.getPage() != 1) {
                throw new IllegalStateException("[E8] 角色分页必须命中新角色且页码 1 起");
            }
            IamOpenRole openRoleByRead = openRoles.getOpenRole(openRoleId);
            if (openRoleByRead.getRevision() != openRole.getRevision()) {
                throw new IllegalStateException("[E8] 角色详情必须回读同 revision");
            }
            List<String> pageGrant = manifest.getPagePermissions().isEmpty()
                    ? Collections.<String>emptyList()
                    : manifest.getPagePermissions().subList(0, 1);
            List<String> apiGrant = manifest.getApiPermissions().isEmpty()
                    ? Collections.<String>emptyList()
                    : manifest.getApiPermissions().subList(0, 1);
            // 建角色不初始化规则（读规则 404 态）；写规则永远携带 If-Match（版本源=角色行 revision）
            try {
                openRoles.getOpenRoleRule(openRoleId, targetApplicationId);
                throw new IllegalStateException("[E8] 新角色初始不得已有规则");
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound expected) {
                log.info("[E8] 新角色规则 404 态实证");
            }
            IamOpenRoleRule ruleFirst = openRoles.putOpenRoleRule(openRoleId, targetApplicationId,
                    "\"open-role:" + openRoleId + ":" + openRole.getRevision() + "\"",
                    manifest.getManifestVersion(), manifest.getManifestDigest(), pageGrant, apiGrant,
                    null);
            log.info("[E8] putOpenRoleRule 首写: revision={}", ruleFirst.getRevision());
            IamOpenRoleRule ruleByRead = openRoles.getOpenRoleRule(openRoleId, targetApplicationId);
            if (ruleByRead.getRevision() != ruleFirst.getRevision()
                    || ruleByRead.getPagePermissions().size() != pageGrant.size()) {
                throw new IllegalStateException("[E8] 规则回读必须同版本同页面码数");
            }
            // 二次写换内容（清码）：内容不变时服务端不递增版本（幂等语义）
            IamOpenRoleRule ruleSecond = openRoles.putOpenRoleRule(openRoleId, targetApplicationId,
                    "\"open-role:" + openRoleId + ":" + ruleFirst.getRevision() + "\"",
                    manifest.getManifestVersion(), manifest.getManifestDigest(),
                    Collections.<String>emptyList(), Collections.<String>emptyList(),
                    null);
            if (ruleSecond.getRevision() <= ruleFirst.getRevision()) {
                throw new IllegalStateException("[E8] If-Match 二次写 revision 必须递增");
            }
            openRoles.deleteOpenRoleRule(openRoleId, targetApplicationId,
                    "\"open-role:" + openRoleId + ":" + ruleSecond.getRevision() + "\"");
            try {
                openRoles.getOpenRoleRule(openRoleId, targetApplicationId);
                throw new IllegalStateException("[E8] 删规则后读取必须 404");
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound expected) {
                log.info("[E8] 规则三态闭环：首写→回读→If-Match 二写→删→404");
            }
            String mountDeptCode = "e2e-mount-" + System.currentTimeMillis();
            IamDepartment mountDept = departments.createDepartment(mountDeptCode, "E2E 挂载部门",
                    rootDepartmentId, 99, 1, null);
            IamOpenRoleDepartmentPage mountPageBefore = openRoles.listOpenRoleDepartments(openRoleId);
            openRoles.mountOpenRoleToDepartment(mountDept.getId(), openRoleId,
                    "\"open-role:" + openRoleId + ":" + mountPageBefore.getRevision() + "\"");
            IamOpenRoleDepartmentPage mountPageAfter = openRoles.listOpenRoleDepartments(openRoleId);
            boolean mounted = false;
            for (io.github.surezzzzzz.sdk.iam.client.model.IamOpenRoleDepartment mountItem
                    : mountPageAfter.getItems()) {
                if (mountDept.getId().equals(mountItem.getDepartmentId())) {
                    mounted = true;
                }
            }
            if (!mounted || mountPageAfter.getRevision() <= mountPageBefore.getRevision()) {
                throw new IllegalStateException("[E8] 挂载后列表必含新部门且 revision 递增");
            }
            openRoles.unmountOpenRoleFromDepartment(mountDept.getId(), openRoleId,
                    "\"open-role:" + openRoleId + ":" + mountPageAfter.getRevision() + "\"");
            IamOpenRoleDepartmentPage mountPageFinal = openRoles.listOpenRoleDepartments(openRoleId);
            for (io.github.surezzzzzz.sdk.iam.client.model.IamOpenRoleDepartment mountItem
                    : mountPageFinal.getItems()) {
                if (mountDept.getId().equals(mountItem.getDepartmentId())) {
                    throw new IllegalStateException("[E8] 摘除后不得仍挂载");
                }
            }
            departments.deleteDepartment(mountDept.getId());
            log.info("[E8] open-roles 全族闭环：读链4+建/列/读+规则三态+挂载/摘除（角色本体无删除契约，测后清理）");

            log.info("[IAM-CLIENT-E2E-FULL] ALL CLANS PASSED (RT-SB3(jakarta))");
        } finally {
            context.close();
        }
    }

    /**
     * 宿主配置：零自定义 Bean，令牌链与地址全部来自配置文件与自动配置。
     */
    @Configuration
    @EnableAutoConfiguration
    static class RunnerConfiguration {
    }
}
