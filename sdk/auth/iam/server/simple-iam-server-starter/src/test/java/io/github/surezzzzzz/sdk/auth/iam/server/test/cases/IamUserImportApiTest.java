package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.department.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AbstractIamEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.department.IamDepartmentRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IAM 管理端用户 Excel 导入 API 回归测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class, properties =
        "io.github.surezzzzzz.sdk.auth.iam.server.internal-reader.enabled=false")
@AutoConfigureMockMvc
@Import(IamUserImportApiTest.AdminEventCapture.class)
class IamUserImportApiTest {

    private static final String[] HEADERS = SimpleIamServerConstant.USER_IMPORT_HEADERS
            .toArray(new String[SimpleIamServerConstant.USER_IMPORT_HEADERS.size()]);

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String departmentCode = "import-dept-" + suffix;
    private final String adminUsername = "import-admin-" + suffix;
    private final String validUsername = "import-valid-" + suffix;
    private final String trailingBlankUsername = "import-trailing-blank-" + suffix;
    private final String duplicateUsername = "import-duplicate-" + suffix;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamDepartmentRepository departmentRepository;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamUserService userService;

    @Autowired
    private IamRoleService roleService;

    @Autowired
    private AdminEventCapture eventCapture;

    private Cookie adminSession;

    @BeforeEach
    void prepareAdminSession() throws Exception {
        Long adminUserId = createUser(adminUsername, "导入测试管理员");
        roleService.assignRole(adminUserId,
                roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());
        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Import@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = login.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        eventCapture.reset();
    }

    @AfterEach
    void cleanup() {
        deleteUser(validUsername);
        deleteUser(trailingBlankUsername);
        deleteUser(duplicateUsername);
        deleteUser(adminUsername);
        departmentRepository.findByCode(departmentCode).ifPresent(departmentRepository::delete);
    }

    @Test
    @DisplayName("管理员应可下载用户导入模板")
    void downloadTemplate() throws Exception {
        log.info("开始验证管理员下载用户导入模板");
        mockMvc.perform(get("/iam/admin/users/import/template").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", String.format(
                        SimpleIamServerConstant.USER_IMPORT_TEMPLATE_CONTENT_DISPOSITION,
                        SimpleIamServerConstant.USER_IMPORT_TEMPLATE_FILE_NAME)))
                .andExpect(header().string("Content-Type",
                        SimpleIamServerConstant.USER_IMPORT_WORKBOOK_MEDIA_TYPE));
        log.info("管理员下载用户导入模板验证完成");
    }

    @Test
    @DisplayName("导入应创建有效行并隔离重复用户名和无效部门行")
    void importRowsIndependently() throws Exception {
        log.info("开始验证用户导入逐行隔离与审计事件");
        createDepartment();
        createUser(duplicateUsername, "重复用户名");
        eventCapture.reset();

        mockMvc.perform(multipart("/iam/admin/users/import")
                        .file(workbook(
                                new String[]{validUsername, "有效用户", "Import@1234", departmentCode, "13800000001", "valid@example.com"},
                                new String[]{duplicateUsername, "重复用户", "Import@1234", departmentCode, "", ""},
                                new String[]{"import-missing-" + suffix, "无效部门", "Import@1234", "missing-" + suffix, "", ""}))
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.createdRows").value(1))
                .andExpect(jsonPath("$.rows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.rows[0].username").value(validUsername))
                .andExpect(jsonPath("$.rows[0].created").value(true))
                .andExpect(jsonPath("$.rows[1].created").value(false))
                .andExpect(jsonPath("$.rows[2].created").value(false));

        assertTrue(userRepository.findByUsername(validUsername).isPresent(), "有效行必须已创建");
        assertTrue(Boolean.TRUE.equals(userRepository.findByUsername(validUsername).get().getMustChangePassword()),
                "批量导入用户首次登录必须改密");
        AdminActionEvent createdEvent = eventCapture.findCreatedUserEvent(validUsername);
        assertNotNull(createdEvent, "导入成功用户必须发布统一的创建审计事件");
        log.info("用户导入逐行隔离与审计事件验证完成：createdRows=1, failedRows=2");
    }

    @Test
    @DisplayName("尾部空行不应占用500条非空行上限")
    void trailingBlankRowsMustNotCountTowardImportLimit() throws Exception {
        log.info("开始验证用户导入尾部空行不计入上限");
        mockMvc.perform(multipart("/iam/admin/users/import")
                        .file(workbook(501, new String[]{trailingBlankUsername, "尾部空行用户", "Import@1234", "", "", ""}))
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.createdRows").value(1));

        assertTrue(userRepository.findByUsername(trailingBlankUsername).isPresent(), "有效行必须已创建");
        log.info("用户导入尾部空行验证完成：createdRows=1");
    }

    @Test
    @DisplayName("损坏工作簿应返回400而非500")
    void malformedWorkbookMustReturnBadRequest() throws Exception {
        log.info("开始验证损坏用户导入工作簿返回400");
        MockMultipartFile file = new MockMultipartFile(SimpleIamServerConstant.USER_IMPORT_FILE_PART_NAME,
                SimpleIamServerConstant.USER_IMPORT_TEMPLATE_FILE_NAME,
                SimpleIamServerConstant.USER_IMPORT_WORKBOOK_MEDIA_TYPE,
                "not-an-xlsx-workbook".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/iam/admin/users/import")
                        .file(file)
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ServerErrorMessage.USER_IMPORT_WORKBOOK_UNREADABLE));
        log.info("损坏用户导入工作簿验证完成：status=400");
    }

    private void createDepartment() {
        IamDepartmentEntity department = new IamDepartmentEntity();
        department.setCode(departmentCode);
        department.setName("导入测试部门");
        department.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        department.setCreatedAt(Instant.now());
        department.setUpdatedAt(Instant.now());
        departmentRepository.save(department);
    }

    private Long createUser(String username, String displayName) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setDisplayName(displayName);
        request.setPassword("Import@1234");
        return userService.createUser(request).getId();
    }

    private void deleteUser(String username) {
        userRepository.findByUsername(username).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    private MockMultipartFile workbook(String[]... rows) throws Exception {
        return workbook(null, rows);
    }

    private MockMultipartFile workbook(Integer trailingBlankRow, String[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(
                    SimpleIamServerConstant.USER_IMPORT_WORKBOOK_SHEET_NAME);
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(
                    SimpleIamServerConstant.USER_IMPORT_HEADER_ROW_INDEX);
            for (int column = 0; column < HEADERS.length; column++) {
                header.createCell(column).setCellValue(HEADERS[column]);
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(rows[rowIndex][column]);
                }
            }
            if (trailingBlankRow != null) {
                sheet.createRow(trailingBlankRow.intValue());
            }
            workbook.write(output);
            return new MockMultipartFile(SimpleIamServerConstant.USER_IMPORT_FILE_PART_NAME,
                    SimpleIamServerConstant.USER_IMPORT_TEMPLATE_FILE_NAME,
                    SimpleIamServerConstant.USER_IMPORT_WORKBOOK_MEDIA_TYPE, output.toByteArray());
        }
    }

    /**
     * 测试用审计事件捕获器，用于验证导入成功行复用统一用户创建事件。
     */
    @Component
    static class AdminEventCapture {

        private final List<AbstractIamEvent> events = new CopyOnWriteArrayList<AbstractIamEvent>();

        @EventListener
        public void onIamEvent(AbstractIamEvent event) {
            events.add(event);
        }

        private AdminActionEvent findCreatedUserEvent(String username) {
            for (AbstractIamEvent event : events) {
                if (event instanceof AdminActionEvent) {
                    AdminActionEvent adminActionEvent = (AdminActionEvent) event;
                    if (adminActionEvent.getAction() == AdminActionType.CREATED
                            && adminActionEvent.getSubjectType() == AdminSubjectType.USER
                            && username.equals(adminActionEvent.getSubjectName())) {
                        return adminActionEvent;
                    }
                }
            }
            return null;
        }

        private void reset() {
            events.clear();
        }
    }
}
