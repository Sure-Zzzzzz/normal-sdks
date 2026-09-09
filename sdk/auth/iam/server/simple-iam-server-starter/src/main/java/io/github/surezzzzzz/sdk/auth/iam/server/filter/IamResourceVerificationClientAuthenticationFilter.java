package io.github.surezzzzzz.sdk.auth.iam.server.filter;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamResourceVerificationClientEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamResourceVerificationClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

/**
 * IAM 资源验证客户端 Basic 认证过滤器。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamResourceVerificationClientAuthenticationFilter extends OncePerRequestFilter {

    private static final String BASIC_PREFIX = "Basic ";


    private final IamResourceVerificationClientService clientService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        java.util.Enumeration<String> authorizations = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (!authorizations.hasMoreElements()) {
            reject(response);
            return;
        }
        String authorization = authorizations.nextElement();
        if (authorizations.hasMoreElements()) {
            reject(response);
            return;
        }
        IamResourceVerificationClientEntity client = authenticate(authorization);
        if (client == null) {
            reject(response);
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                client, null, Collections.emptyList());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"iam-resource-verification\"");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private IamResourceVerificationClientEntity authenticate(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BASIC_PREFIX)) {
            return null;
        }
        try {
            String raw = new String(Base64.getDecoder().decode(
                    authorization.substring(BASIC_PREFIX.length())), StandardCharsets.UTF_8);
            int separatorIndex = raw.indexOf(':');
            if (separatorIndex <= 0) {
                return null;
            }
            return clientService.authenticate(raw.substring(0, separatorIndex),
                    raw.substring(separatorIndex + 1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
