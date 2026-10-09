package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 门户页面权限的取值策略。
 *
 * <p>特权全量（PLATFORM_ADMIN_MANIFEST）的成立条件是三条件合取：平台管理员、
 * 内置应用、已申报清单——缺一即回退授权行投影（AUTHORIZATION_ROW）。清单的
 * 存在性只回答"能不能给全量"，内置判定才回答"该不该给特权"，两判断解耦。
 * 1.3.5 起 openapi 页面准入查询（listPageAdmittedApplicationCodes）与门户读模型
 * 共用本策略，保证两个出口的页面权限语义一致。</p>
 *
 * @author surezzzzzz
 */
enum PagePermissionSource {

    /**
     * 特权直通：仅内置应用适用，读清单申报范围全量。
     */
    PLATFORM_ADMIN_MANIFEST {
        @Override
        Set<String> read(IamApplicationAuthorizationEntity authorization,
                         IamApplicationPermissionManifestEntity manifest) {
            return new HashSet<>(IamApplicationAuthorizationJsonCodec.readStringList(
                    manifest.getPagePermissionsJson(), "pagePermissions"));
        }
    },

    /**
     * 授权行投影：读 iam_application_authorization.page_permissions_json（行为 null 时为空集）。
     */
    AUTHORIZATION_ROW {
        @Override
        Set<String> read(IamApplicationAuthorizationEntity authorization,
                         IamApplicationPermissionManifestEntity manifest) {
            if (authorization == null) {
                return Collections.emptySet();
            }
            return new HashSet<>(IamApplicationAuthorizationJsonCodec.readStringList(
                    authorization.getPagePermissionsJson(), "pagePermissions"));
        }
    };

    static PagePermissionSource resolve(boolean platformAdmin, boolean builtInApplication,
                                        boolean manifestPresent, boolean authorizationPresent) {
        if (platformAdmin && builtInApplication && manifestPresent) {
            return PLATFORM_ADMIN_MANIFEST;
        }
        return AUTHORIZATION_ROW;
    }

    abstract Set<String> read(IamApplicationAuthorizationEntity authorization,
                              IamApplicationPermissionManifestEntity manifest);
}
