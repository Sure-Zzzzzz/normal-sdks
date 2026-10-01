package io.github.surezzzzzz.sdk.auth.iam.server.service.user;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.UserImportResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.UserImportRowResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.department.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ValidationException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.department.IamDepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户 Excel 导入服务。上传流仅在请求期解析，不保存原始文件。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamUserExcelImportService {

    private final IamUserService userService;
    private final IamDepartmentRepository departmentRepository;

    /**
     * 解析并逐行创建本地用户。单行创建走既有用户服务，错误不会回滚其他有效行。
     *
     * @param file 用户导入工作簿
     * @return 各行创建结果
     */
    public UserImportResponse importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validationFailure(ServerErrorMessage.USER_IMPORT_FILE_REQUIRED);
        }
        log.debug("开始解析用户导入工作簿");
        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() != 1) {
                throw validationFailure(ServerErrorMessage.USER_IMPORT_WORKBOOK_INVALID);
            }
            Sheet sheet = workbook.getSheetAt(0);
            validateHeaders(sheet.getRow(SimpleIamServerConstant.USER_IMPORT_HEADER_ROW_INDEX));
            UserImportResponse response = importRows(sheet);
            log.debug("用户导入工作簿解析完成：totalRows={}", response.getTotalRows());
            return response;
        } catch (IOException | POIXMLException | NotOfficeXmlFileException ex) {
            log.debug("用户导入工作簿解析失败：exceptionType={}", ex.getClass().getSimpleName(), ex);
            throw new ValidationException(ServerErrorMessage.USER_IMPORT_WORKBOOK_UNREADABLE, ex);
        }
    }

    private UserImportResponse importRows(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        List<UserImportRowResponse> results = new ArrayList<UserImportRowResponse>();
        int createdRows = 0;
        int nonBlankRows = 0;
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (isBlankRow(row, formatter)) {
                continue;
            }
            if (++nonBlankRows > SimpleIamServerConstant.USER_IMPORT_MAX_ROWS) {
                throw validationFailure(String.format(ServerErrorMessage.USER_IMPORT_ROWS_EXCEEDED,
                        SimpleIamServerConstant.USER_IMPORT_MAX_ROWS));
            }
            UserImportRowResponse result = importRow(row, index + 1, formatter);
            results.add(result);
            if (result.isCreated()) {
                createdRows++;
            }
        }
        UserImportResponse response = new UserImportResponse();
        response.setTotalRows(results.size());
        response.setCreatedRows(createdRows);
        response.setRows(results);
        log.info("用户导入完成：totalRows={}, createdRows={}, failedRows={}",
                response.getTotalRows(), response.getCreatedRows(), response.getTotalRows() - response.getCreatedRows());
        return response;
    }

    private UserImportRowResponse importRow(Row row, int rowNumber, DataFormatter formatter) {
        UserImportRowResponse result = new UserImportRowResponse();
        result.setRowNumber(rowNumber);
        String username = cellValue(row, SimpleIamServerConstant.USER_IMPORT_COLUMN_USERNAME, formatter);
        result.setUsername(username);
        try {
            CreateUserRequest request = new CreateUserRequest();
            request.setUsername(requireValue(username, SimpleIamServerConstant.USER_IMPORT_HEADER_USERNAME));
            request.setDisplayName(requireValue(cellValue(row,
                            SimpleIamServerConstant.USER_IMPORT_COLUMN_DISPLAY_NAME, formatter),
                    SimpleIamServerConstant.USER_IMPORT_HEADER_DISPLAY_NAME));
            request.setPassword(requireValue(cellValue(row,
                            SimpleIamServerConstant.USER_IMPORT_COLUMN_INITIAL_PASSWORD, formatter),
                    SimpleIamServerConstant.USER_IMPORT_HEADER_INITIAL_PASSWORD));
            request.setDepartmentId(resolveDepartmentId(cellValue(row,
                    SimpleIamServerConstant.USER_IMPORT_COLUMN_DEPARTMENT_CODE, formatter)));
            request.setPhone(emptyToNull(cellValue(row, SimpleIamServerConstant.USER_IMPORT_COLUMN_PHONE, formatter)));
            request.setEmail(emptyToNull(cellValue(row, SimpleIamServerConstant.USER_IMPORT_COLUMN_EMAIL, formatter)));
            IamUserEntity user = userService.createUser(request);
            result.setCreated(true);
            result.setMessage(ServerErrorMessage.USER_IMPORT_ROW_CREATED);
        } catch (RuntimeException ex) {
            result.setCreated(false);
            result.setMessage(ex.getMessage());
            log.debug("用户导入行处理失败：rowNumber={}, exceptionType={}",
                    rowNumber, ex.getClass().getSimpleName());
        }
        return result;
    }

    private Long resolveDepartmentId(String departmentCode) {
        String normalizedCode = emptyToNull(departmentCode);
        if (normalizedCode == null) {
            return null;
        }
        IamDepartmentEntity department = departmentRepository.findByCode(normalizedCode)
                .orElseThrow(() -> validationFailure(String.format(
                        ServerErrorMessage.USER_IMPORT_DEPARTMENT_NOT_FOUND, normalizedCode)));
        if (department.getStatus() == null
                || department.getStatus().intValue() != SimpleIamServerConstant.STATUS_ACTIVE) {
            throw validationFailure(String.format(ServerErrorMessage.USER_IMPORT_DEPARTMENT_INACTIVE, normalizedCode));
        }
        return department.getId();
    }

    private void validateHeaders(Row headerRow) {
        if (headerRow == null) {
            throw validationFailure(ServerErrorMessage.USER_IMPORT_HEADER_MISSING);
        }
        DataFormatter formatter = new DataFormatter();
        for (int index = 0; index < SimpleIamServerConstant.USER_IMPORT_HEADERS.size(); index++) {
            if (!SimpleIamServerConstant.USER_IMPORT_HEADERS.get(index)
                    .equals(cellValue(headerRow, index, formatter))) {
                throw validationFailure(ServerErrorMessage.USER_IMPORT_HEADER_MISMATCH);
            }
        }
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int index = 0; index < SimpleIamServerConstant.USER_IMPORT_HEADERS.size(); index++) {
            if (StringUtils.hasText(cellValue(row, index, formatter))) {
                return false;
            }
        }
        return true;
    }

    private String cellValue(Row row, int index, DataFormatter formatter) {
        Cell cell = row == null ? null : row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String requireValue(String value, String columnName) {
        String normalizedValue = emptyToNull(value);
        if (normalizedValue == null) {
            throw validationFailure(String.format(ServerErrorMessage.USER_IMPORT_FIELD_REQUIRED, columnName));
        }
        return normalizedValue;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ValidationException validationFailure(String message) {
        return new ValidationException(message);
    }
}
