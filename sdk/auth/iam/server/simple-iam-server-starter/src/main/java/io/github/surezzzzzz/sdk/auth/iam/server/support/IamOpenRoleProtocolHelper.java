package io.github.surezzzzzz.sdk.auth.iam.server.support;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request.CreateOpenRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOpenRoleBindingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.model.IamOpenRoleActor;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.UUID;

/**
 * 公开标识、创建摘要与强条件版本的协议处理。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class IamOpenRoleProtocolHelper {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private IamOpenRoleProtocolHelper() {
    }

    /**
     * 要求当前已验证身份为 AKSK 服务主体，密钥轮换不改变归属。
     */
    public static IamOpenRoleActor requireServiceActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof VerifiedResourceContext)) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_AUTHENTICATION_REQUIRED);
        }
        VerifiedResourceContext context = (VerifiedResourceContext) authentication.getPrincipal();
        VerifiedResourcePrincipal principal = context.getPrincipal();
        if (!SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM.equals(context.getApplicationAuthorization().getApplicationCode())
                || !context.getApplicationAuthorization().isAdmitted()) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_FORBIDDEN);
        }
        if (!SimpleIamServerConstant.OPEN_ROLE_SERVICE_SOURCE_ID.equals(principal.getSourceId().getValue())
                || principal.getSubjectType() != ResourceSubjectType.SERVICE
                || principal.getSourceId().getValue().length() > SimpleIamServerConstant.OPEN_ROLE_SOURCE_MAX_LENGTH
                || principal.getSubjectId().length() > SimpleIamServerConstant.OPEN_ROLE_SUBJECT_MAX_LENGTH) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_AUTHENTICATION_REQUIRED);
        }
        log.debug("开放角色服务主体确认：requestId={}, sourceId={}, subjectType={}",
                context.getRequestId(), principal.getSourceId().getValue(), principal.getSubjectType().getCode());
        return new IamOpenRoleActor(principal.getSourceId().getValue(), principal.getSubjectType().getCode(),
                principal.getSubjectId(), context.getRequestId());
    }

    /**
     * 验证完整主体三元组，不能以名称、前缀或 API 全范围代替。
     */
    public static boolean isOwner(IamOpenRoleBindingEntity binding, IamOpenRoleActor actor) {
        return binding.getOwnerSourceId().equals(actor.getSourceId())
                && binding.getOwnerSubjectType().equals(actor.getSubjectType())
                && binding.getOwnerSubjectId().equals(actor.getSubjectId());
    }

    /**
     * 严格接受标准 UUID 形式，避免 UUID.fromString 接受短段变体。
     */
    public static String uuid(String value) {
        if (value == null || !value.matches(SimpleIamServerConstant.OPEN_ROLE_UUID_PATTERN)) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        }
        return UUID.fromString(value).toString();
    }

    /**
     * 数字业务编号必须为正数。
     */
    public static Long positive(Long value) {
        if (value == null || value <= 0L) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        return value;
    }

    /**
     * 返回规范化新对象，不就地改变宿主或调用方请求。
     */
    public static CreateOpenRoleRequest normalize(CreateOpenRoleRequest input) {
        if (input == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        CreateOpenRoleRequest normalized = new CreateOpenRoleRequest();
        normalized.setExternalId(uuid(input.getExternalId()));
        normalized.setApplicationId(positive(input.getApplicationId()));
        normalized.setRootDepartmentId(positive(input.getRootDepartmentId()));
        normalized.setName(text(input.getName(), SimpleIamServerConstant.OPEN_ROLE_NAME_MAX_LENGTH, true));
        normalized.setDescription(text(input.getDescription(), SimpleIamServerConstant.OPEN_ROLE_DESCRIPTION_MAX_LENGTH, false));
        return normalized;
    }

    /**
     * 校验展示文本；空描述规范化为 null。
     */
    public static String text(String value, int maxLength, boolean required) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            if (required) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
            return null;
        }
        if (normalized.length() > maxLength) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        return normalized;
    }

    /**
     * 生成 SDK 自有序列化的稳定创建或目录内容摘要。
     */
    public static String digest(Object value) {
        try {
            return TokenHashHelper.sha256Hex(MAPPER.writeValueAsString(value));
        } catch (Exception exception) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        }
    }

    /**
     * 派生普通角色编码，仅用于核对绑定，不能作为所有权证明。
     */
    public static String roleCode(String openRoleId) {
        return SimpleIamServerConstant.OPEN_ROLE_CODE_PREFIX + uuid(openRoleId).replace(
                SimpleIamServerConstant.OPEN_ROLE_UUID_SEPARATOR, SimpleIamServerConstant.EMPTY_STRING);
    }

    /**
     * 构造强 ETag。
     */
    public static String etag(String openRoleId, long revision) {
        return String.format(Locale.ROOT, SimpleIamServerConstant.OPEN_ROLE_ETAG_TEMPLATE, uuid(openRoleId), revision);
    }

    /**
     * 必须匹配同一 UUID 与当前版本；不接收通配符、弱标记或标记列表。
     */
    public static void requireIfMatch(String header, IamOpenRoleBindingEntity binding) {
        if (header == null || header.trim().isEmpty()) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_PRECONDITION_REQUIRED);
        }
        if (!header.matches(SimpleIamServerConstant.OPEN_ROLE_ETAG_PATTERN))
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        try {
            long expectedRevision = Long.parseLong(header.substring(header.lastIndexOf(SimpleIamServerConstant.OPEN_ROLE_ETAG_REVISION_SEPARATOR) + 1, header.length() - 1));
            log.debug("开放角色条件版本核对：openRoleId={}, currentRevision={}, expectedRevision={}",
                    binding.getOpenRoleId(), binding.getRevision(), expectedRevision);
        } catch (NumberFormatException invalid) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        }
        if (!etag(binding.getOpenRoleId(), binding.getRevision()).equals(header)) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_PRECONDITION_FAILED);
        }
    }
}
