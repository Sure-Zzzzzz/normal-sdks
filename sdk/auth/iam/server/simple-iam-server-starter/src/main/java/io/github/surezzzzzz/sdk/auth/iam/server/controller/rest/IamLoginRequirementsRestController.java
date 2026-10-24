package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamWebAuthConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response.WebLoginRequirementsResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamLoginRequirementsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录要求公开查询接口。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping(IamWebAuthConstant.PATH_REQUIREMENTS)
@RequiredArgsConstructor
public class IamLoginRequirementsRestController {

    private final IamLoginRequirementsService requirementsService;

    /**
     * 获取当前部署要求用户确认的文档版本。
     */
    @GetMapping
    public ResponseEntity<WebLoginRequirementsResponse> requirements() {
        return ResponseEntity.ok(requirementsService.current());
    }
}
