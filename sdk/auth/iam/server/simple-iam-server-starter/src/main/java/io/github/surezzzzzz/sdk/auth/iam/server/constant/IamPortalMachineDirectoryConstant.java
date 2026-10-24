package io.github.surezzzzzz.sdk.auth.iam.server.constant;

/**
 * Portal 机器路由目录的 starter 侧契约常量。
 *
 * <p>该能力只属于 Server Starter 的开放 API，不把 Gateway 语义扩散到 server-core。</p>
 *
 * @author surezzzzzz
 */
public final class IamPortalMachineDirectoryConstant {

    /**
     * Portal 可信应用路由投影 API 权限。
     */
    public static final String API_PERMISSION = "iam:portal-application:read:api";
    /**
     * Portal 可信应用路由投影 DATA 资源。
     */
    public static final String DATA_RESOURCE = "iam:portal-application";
    /**
     * Portal 可信应用路由投影路径。
     */
    public static final String PATH = "/iam/api/portal/applications";
    /**
     * 将调用方的 Authorization 请求头原样转发给可信应用自有 Nginx。
     */
    public static final String FORWARD_AUTHORIZATION_REQUEST = "REQUEST";

    private IamPortalMachineDirectoryConstant() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
