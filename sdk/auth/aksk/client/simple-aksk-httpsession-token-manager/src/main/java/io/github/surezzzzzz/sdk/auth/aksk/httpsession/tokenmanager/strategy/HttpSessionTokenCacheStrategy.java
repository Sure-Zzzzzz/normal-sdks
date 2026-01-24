package io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.strategy;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy.TokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.annotation.SimpleAkskHttpSessionTokenManagerComponent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;

/**
 * 基于 HttpSession 的 Token 缓存策略（默认策略）
 * <p>
 * 优点：
 * <ul>
 *   <li>无需额外依赖</li>
 *   <li>简单直接</li>
 * </ul>
 * <p>
 * 缺点：
 * <ul>
 *   <li>多副本场景下无法共享缓存</li>
 *   <li>需要配置 Session 粘性或使用分布式 Session（如 Spring Session + Redis）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleAkskHttpSessionTokenManagerComponent
@Slf4j
public class HttpSessionTokenCacheStrategy implements TokenCacheStrategy {

    /**
     * 缓存的 Token 包装类
     */
    private static class CachedToken implements Serializable {
        private static final long serialVersionUID = 1L;

        @Getter
        private final String token;
        private final long expireTime;

        public CachedToken(String token, long expiresInSeconds) {
            this.token = token;
            // 提前 30 秒过期（避免边界情况）
            long ttl = Math.max(expiresInSeconds - 30, 60);
            this.expireTime = System.currentTimeMillis() + ttl * 1000;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expireTime;
        }

    }

    @Override
    public String generateCacheKey(String securityContext) {
        if (!StringUtils.hasText(securityContext)) {
            // 没有 security_context，使用默认 Key（适用于平台级 AKSK）
            return SimpleAkskClientCoreConstant.SESSION_TOKEN_KEY;
        }

        // 有 security_context，生成基于内容的 Hash Key
        int hash = securityContext.hashCode();
        return SimpleAkskClientCoreConstant.SESSION_TOKEN_KEY + ":" + hash;
    }

    @Override
    public String get(String cacheKey) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.debug("No current request, cannot get token from session");
            return null;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            log.debug("No session found, cannot get token");
            return null;
        }

        CachedToken cachedToken = (CachedToken) session.getAttribute(cacheKey);
        if (cachedToken == null) {
            log.debug("Token cache miss in session: key={}", cacheKey);
            return null;
        }

        if (cachedToken.isExpired()) {
            log.debug("Token expired in session: key={}", cacheKey);
            session.removeAttribute(cacheKey);
            return null;
        }

        log.debug("Token cache hit in session: key={}", cacheKey);
        return cachedToken.getToken();
    }

    @Override
    public void put(String cacheKey, String token, long expiresInSeconds) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.warn("No current request, cannot cache token in session");
            return;
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(cacheKey, new CachedToken(token, expiresInSeconds));

        long ttl = Math.max(expiresInSeconds - 30, 60);
        log.debug("Token cached in session: key={}, ttl={}s", cacheKey, ttl);
    }

    @Override
    public void remove(String cacheKey) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(cacheKey);
            log.debug("Token removed from session: key={}", cacheKey);
        }
    }

    /**
     * 获取当前请求
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
