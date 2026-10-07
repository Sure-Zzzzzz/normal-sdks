package io.github.surezzzzzz.sdk.kms.server.walkthrough;

import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalDisplayNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * 走查宿主专用主体显示名解析器：只读查询本机 IAM 用户表与 AKSK 客户端表。
 *
 * <p>仅供 iam.zs.com 走查呈现可读归属，不属于 SDK 能力；连接参数走
 * kms.walkthrough.directory.* 配置键，密码按本模块惯例放在 gitignored 的
 * application-local.yml，查询失败按未解析处理，不影响业务响应。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class WalkthroughPrincipalDisplayNameResolver implements KmsPrincipalDisplayNameResolver {

    /**
     * IAM 人员主体的来源前缀。
     */
    private static final String IAM_PREFIX = "iam:";
    /**
     * AKSK 客户端主体的来源前缀。
     */
    private static final String AKSK_PREFIX = "aksk:";
    /**
     * 按稳定主体标识查 IAM 用户名。
     */
    private static final String IAM_LOOKUP_SQL = "SELECT subject_id, username FROM sure_auth_iam.iam_user WHERE subject_id IN (:ids)";
    /**
     * 按客户端标识查 AKSK 客户端名称。
     */
    private static final String AKSK_LOOKUP_SQL = "SELECT client_id, client_name FROM sure_auth_aksk.oauth2_registered_client WHERE client_id IN (:ids)";

    /**
     * 默认 IAM 目录库连接。
     */
    private static final String DEFAULT_IAM_URL = "jdbc:mysql://localhost:3306/sure_auth_iam?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    /**
     * 默认 AKSK 目录库连接。
     */
    private static final String DEFAULT_AKSK_URL = "jdbc:mysql://localhost:3306/sure_auth_aksk?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    /**
     * 走查环境配置，提供目录库连接参数。
     */
    private final Environment environment;

    private static String repeatPlaceholders(int count) {
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < count; ++i) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }
        return placeholders.toString();
    }

    /**
     * 单个主体解析；走查场景使用批量入口。
     */
    @Override
    public String resolveDisplayName(String principalId) {
        Map<String, String> result = resolveDisplayNames(java.util.Collections.singletonList(principalId));
        return result.get(principalId);
    }

    /**
     * 按来源前缀分组后各执行一次只读 IN 查询。
     */
    @Override
    public Map<String, String> resolveDisplayNames(Collection<String> principalIds) {
        Map<String, String> result = new HashMap<String, String>();
        if (principalIds == null || principalIds.isEmpty()) {
            return result;
        }
        List<String> iamIds = new ArrayList<String>();
        List<String> akskIds = new ArrayList<String>();
        for (String principalId : principalIds) {
            if (principalId == null) {
                continue;
            }
            if (principalId.startsWith(IAM_PREFIX)) {
                iamIds.add(principalId.substring(IAM_PREFIX.length()));
            } else if (principalId.startsWith(AKSK_PREFIX)) {
                akskIds.add(principalId.substring(AKSK_PREFIX.length()));
            }
        }
        lookup(result, "iam", IAM_LOOKUP_SQL, iamIds, IAM_PREFIX);
        lookup(result, "aksk", AKSK_LOOKUP_SQL, akskIds, AKSK_PREFIX);
        return result;
    }

    /**
     * 在走查数据源上执行一次只读解析，失败时仅记诊断并按未解析返回。
     */
    private void lookup(Map<String, String> result, String source, String sql, List<String> ids, String prefix) {
        if (ids.isEmpty()) {
            return;
        }
        String keyPrefix = "kms.walkthrough.directory." + source + ".";
        String url = environment.getProperty(keyPrefix + "url", source.equals("iam") ? DEFAULT_IAM_URL : DEFAULT_AKSK_URL);
        String username = environment.getProperty(keyPrefix + "username", "root");
        String password = environment.getProperty(keyPrefix + "password");
        if (password == null || password.isEmpty()) {
            log.debug("KMS 走查主体显示名未配置目录密码 source={} count={}", source, ids.size());
            return;
        }
        String joined = sql.replace(":ids", repeatPlaceholders(ids.size()));
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(joined)) {
            for (int i = 0; i < ids.size(); ++i) {
                statement.setString(i + 1, ids.get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(prefix + rows.getString(1), rows.getString(2));
                }
            }
        } catch (Exception exception) {
            log.debug("KMS 走查主体显示名解析失败 source={} count={}", source, ids.size());
        }
    }
}
