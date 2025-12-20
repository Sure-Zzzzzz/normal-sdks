package io.github.surezzzzzz.sdk.elasticsearch.route.registry;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.VersionException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ES 服务端版本
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public class ServerVersion {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?.*$");

    private final String raw;
    private final int major;
    private final int minor;
    private final int patch;

    private ServerVersion(String raw, int major, int minor, int patch) {
        this.raw = raw;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static ServerVersion parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new VersionException(ErrorCode.VERSION_EMPTY, ErrorMessage.VERSION_EMPTY);
        }

        String value = raw.trim();
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new VersionException(ErrorCode.VERSION_PARSE_FAILED,
                    String.format(ErrorMessage.VERSION_PARSE_FAILED, raw));
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? -1 : Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? -1 : Integer.parseInt(matcher.group(3));

        return new ServerVersion(value, major, minor, patch);
    }

    public static ServerVersion tryParse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}

