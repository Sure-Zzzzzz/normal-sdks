package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.CreateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.UpdateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.response.DepartmentRestResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 开放 API 部门族（{@code /iam/api/departments}）。
 *
 * <p>主体为 AKSK 凭证，由公共资源层链鉴权；端点级 @RequireApiPermission
 * 精确码控（iam:department:api）。列表为平铺全量（含 fullPath），外部系统
 * 自行重建树；第一版不挂 DATA（纯码控）。删除走既有级联规则。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/departments")
@RequiredArgsConstructor
public class IamDepartmentRestController {

    /**
     * fullPath 上溯防御上限（脏数据成环时截断，正常层级远达不到）。
     */
    private static final int MAX_FULL_PATH_DEPTH = 32;

    private final DepartmentService departmentService;

    /**
     * 全量部门平铺列表（含 fullPath，外部按 parentId / fullPath 重建树）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API)
    public ResponseEntity<List<DepartmentRestResponse>> listDepartments() {
        List<IamDepartmentEntity> departments = departmentService.getAllDepartments();
        return ResponseEntity.ok(toDepartmentRestResponses(departments));
    }

    /**
     * 部门详情。
     */
    @GetMapping("/{departmentId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API)
    public ResponseEntity<DepartmentRestResponse> getDepartment(@PathVariable Long departmentId) {
        IamDepartmentEntity department = departmentService.getById(departmentId);
        return ResponseEntity.ok(toDepartmentRestResponses(Collections.singletonList(department)).get(0));
    }

    /**
     * 创建部门。
     */
    @PostMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API)
    public ResponseEntity<DepartmentRestResponse> createDepartment(@RequestBody CreateDepartmentRequest request) {
        IamDepartmentEntity department = departmentService.createDepartment(request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(toDepartmentRestResponses(Collections.singletonList(department)).get(0));
    }

    /**
     * 更新部门。
     */
    @PutMapping("/{departmentId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API)
    public ResponseEntity<DepartmentRestResponse> updateDepartment(@PathVariable Long departmentId,
                                                                   @RequestBody UpdateDepartmentRequest request) {
        IamDepartmentEntity department = departmentService.updateDepartment(departmentId, request);
        return ResponseEntity.ok(toDepartmentRestResponses(Collections.singletonList(department)).get(0));
    }

    /**
     * 删除部门（走既有级联规则）。
     */
    @DeleteMapping("/{departmentId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API)
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long departmentId) {
        departmentService.deleteDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 平铺转开放 API 响应；fullPath 一次全量内存拼接（沿 parentId 上溯，环保护截断）。
     */
    private List<DepartmentRestResponse> toDepartmentRestResponses(List<IamDepartmentEntity> departments) {
        Map<Long, IamDepartmentEntity> byId = new HashMap<>();
        for (IamDepartmentEntity department : departments) {
            byId.put(department.getId(), department);
        }
        Map<Long, String> fullPathCache = new HashMap<>();
        List<DepartmentRestResponse> responses = new ArrayList<>(departments.size());
        for (IamDepartmentEntity department : departments) {
            responses.add(DepartmentRestResponse.from(department, buildFullPath(department, byId, fullPathCache)));
        }
        return responses;
    }

    private String buildFullPath(IamDepartmentEntity department,
                                 Map<Long, IamDepartmentEntity> byId,
                                 Map<Long, String> fullPathCache) {
        Long id = department.getId();
        String cached = fullPathCache.get(id);
        if (cached != null) {
            return cached;
        }
        StringBuilder path = new StringBuilder(department.getName());
        Long parentId = department.getParentId();
        int depth = 0;
        while (parentId != null && byId.containsKey(parentId) && depth < MAX_FULL_PATH_DEPTH) {
            IamDepartmentEntity parent = byId.get(parentId);
            path.insert(0, parent.getName() + "/");
            parentId = parent.getParentId();
            depth++;
        }
        String fullPath = "/" + path;
        fullPathCache.put(id, fullPath);
        return fullPath;
    }
}
