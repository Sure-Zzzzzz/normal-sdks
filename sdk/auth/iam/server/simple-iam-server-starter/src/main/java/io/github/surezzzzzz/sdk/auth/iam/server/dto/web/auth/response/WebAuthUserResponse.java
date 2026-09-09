package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Web 当前登录用户响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebAuthUserResponse {

    private Long userId;

    private String username;

    private String displayName;

    private boolean admin;

    private List<String> authorities;
}
