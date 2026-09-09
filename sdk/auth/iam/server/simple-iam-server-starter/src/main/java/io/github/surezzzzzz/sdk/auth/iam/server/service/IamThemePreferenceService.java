package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamThemePreferenceJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalThemePreferenceRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalThemePreferenceResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserThemePreferenceEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserThemePreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * IAM 当前用户 Portal 主题偏好服务。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamThemePreferenceService {

    private static final int CONTRACT_VERSION = 1;
    private static final String MODE_LIGHT = "light";
    private static final String MODE_DARK = "dark";
    private static final String MODE_CUSTOM = "custom";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final List<String> CUSTOM_TOKEN_KEYS = Collections.unmodifiableList(Arrays.asList(
            "primary", "primaryHover", "primaryActive", "primaryWeak", "primaryText",
            "canvas", "surface", "surfaceRaised", "surfaceSoft", "textPrimary", "textSecondary",
            "textDisabled", "border", "borderStrong", "focusRing", "success", "successBg",
            "warning", "warningBg", "danger", "dangerBg", "info", "infoBg", "overlayScrim"));
    // overlayScrim 晚于首批 23 键发布：存量色板允许缺失，读取与保存时补契约默认值
    private static final List<String> REQUIRED_TOKEN_KEYS = CUSTOM_TOKEN_KEYS.subList(0, CUSTOM_TOKEN_KEYS.size() - 1);
    private static final String DEFAULT_OVERLAY_SCRIM = "#0D192F";

    private final IamUserThemePreferenceRepository themePreferenceRepository;

    /**
     * 读取用户主题偏好
     */
    @Transactional(readOnly = true)
    public PortalThemePreferenceResponse getPreference(Long userId) {
        return themePreferenceRepository.findById(userId)
                .map(this::toResponse)
                .orElseGet(this::defaultPreference);
    }

    /**
     * 保存用户主题偏好
     */
    @Transactional
    public PortalThemePreferenceResponse savePreference(Long userId, PortalThemePreferenceRequest request) {
        ValidatedPreference preference = validate(request);
        IamUserThemePreferenceEntity entity = themePreferenceRepository.findById(userId)
                .orElseGet(() -> {
                    IamUserThemePreferenceEntity created = new IamUserThemePreferenceEntity();
                    created.setUserId(userId);
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        entity.setContractVersion(CONTRACT_VERSION);
        entity.setMode(preference.mode);
        // 浅色/深色只是当前选择，不销毁已保存的自定义色板；仅自定义模式覆盖存储
        if (MODE_CUSTOM.equals(preference.mode)) {
            entity.setCustomTokensJson(IamThemePreferenceJsonCodec.write(preference.customTokens));
        }
        entity.setUpdatedAt(Instant.now());
        return toResponse(themePreferenceRepository.save(entity));
    }

    private ValidatedPreference validate(PortalThemePreferenceRequest request) {
        if (request == null || request.getContractVersion() == null
                || request.getContractVersion().intValue() != CONTRACT_VERSION) {
            throw invalid();
        }
        String mode = request.getMode();
        if (!MODE_LIGHT.equals(mode) && !MODE_DARK.equals(mode) && !MODE_CUSTOM.equals(mode)) {
            throw invalid();
        }
        Map<String, String> customTokens = request.getCustomTokens();
        if (!MODE_CUSTOM.equals(mode)) {
            if (customTokens != null && !customTokens.isEmpty()) {
                throw invalid();
            }
            return new ValidatedPreference(mode, Collections.<String, String>emptyMap());
        }
        return new ValidatedPreference(mode, normalizeCustomTokens(customTokens));
    }

    private Map<String, String> normalizeCustomTokens(Map<String, String> customTokens) {
        if (customTokens == null || !CUSTOM_TOKEN_KEYS.containsAll(customTokens.keySet())
                || !customTokens.keySet().containsAll(REQUIRED_TOKEN_KEYS)) {
            throw invalid();
        }
        Map<String, String> normalized = new LinkedHashMap<String, String>();
        for (String key : CUSTOM_TOKEN_KEYS) {
            String color = customTokens.containsKey(key) ? customTokens.get(key) : DEFAULT_OVERLAY_SCRIM;
            if (color == null || !HEX_COLOR_PATTERN.matcher(color).matches()) {
                throw invalid();
            }
            normalized.put(key, color.toUpperCase());
        }
        validateContrast(normalized, "textPrimary", "canvas");
        validateContrast(normalized, "textSecondary", "surface");
        validateContrast(normalized, "primaryText", "primary");
        validateContrast(normalized, "success", "successBg");
        validateContrast(normalized, "warning", "warningBg");
        validateContrast(normalized, "danger", "dangerBg");
        validateContrast(normalized, "info", "infoBg");
        return Collections.unmodifiableMap(normalized);
    }

    private PortalThemePreferenceResponse toResponse(IamUserThemePreferenceEntity entity) {
        String mode = entity.getMode();
        if (!MODE_LIGHT.equals(mode) && !MODE_DARK.equals(mode) && !MODE_CUSTOM.equals(mode)) {
            return defaultPreference();
        }
        // 色板是持久资产：任何模式都返回供前端编辑回填；坏数据兜底空表，不炸 GET
        return new PortalThemePreferenceResponse(CONTRACT_VERSION, mode,
                readStoredCustomTokens(entity), entity.getUpdatedAt());
    }

    private Map<String, String> readStoredCustomTokens(IamUserThemePreferenceEntity entity) {
        String stored = entity.getCustomTokensJson();
        if (stored == null || stored.isEmpty()) {
            return Collections.<String, String>emptyMap();
        }
        try {
            Map<String, String> tokens = IamThemePreferenceJsonCodec.read(stored);
            if (tokens == null || tokens.isEmpty()) {
                return Collections.<String, String>emptyMap();
            }
            return normalizeCustomTokens(tokens);
        } catch (RuntimeException ex) {
            return Collections.<String, String>emptyMap();
        }
    }

    private PortalThemePreferenceResponse defaultPreference() {
        return new PortalThemePreferenceResponse(CONTRACT_VERSION, MODE_LIGHT,
                Collections.<String, String>emptyMap(), null);
    }

    private void validateContrast(Map<String, String> tokens, String foregroundKey, String backgroundKey) {
        if (contrast(tokens.get(foregroundKey), tokens.get(backgroundKey)) < 4.5D) {
            throw invalid();
        }
    }

    private double contrast(String foreground, String background) {
        double foregroundLuminance = luminance(foreground);
        double backgroundLuminance = luminance(background);
        return (Math.max(foregroundLuminance, backgroundLuminance) + 0.05D)
                / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05D);
    }

    private double luminance(String color) {
        double red = linearChannel(color, 1);
        double green = linearChannel(color, 3);
        double blue = linearChannel(color, 5);
        return red * 0.2126D + green * 0.7152D + blue * 0.0722D;
    }

    private double linearChannel(String color, int startIndex) {
        double channel = Integer.parseInt(color.substring(startIndex, startIndex + 2), 16) / 255D;
        return channel <= 0.04045D ? channel / 12.92D : Math.pow((channel + 0.055D) / 1.055D, 2.4D);
    }

    private SimpleIamServerException invalid() {
        return new SimpleIamServerException("主题偏好无效");
    }

    private static final class ValidatedPreference {

        private final String mode;
        private final Map<String, String> customTokens;

        private ValidatedPreference(String mode, Map<String, String> customTokens) {
            this.mode = mode;
            this.customTokens = customTokens;
        }
    }
}
