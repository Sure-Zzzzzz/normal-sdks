package io.github.surezzzzzz.sdk.auth.aksk.server.e2eserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class AkskE2eControlConfiguration {

    private static final String CONTROL_HEADER = "X-Iam-Aksk-E2e-Control";
    private static final String READY_PATH = "/__iam-aksk-e2e/ready";
    private static final String SHUTDOWN_PATH = "/__iam-aksk-e2e/shutdown";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> akskE2eControlFilter(
            ConfigurableApplicationContext context,
            @Value("${iam.aksk.e2e.aksk.control-token}") String controlToken) {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<OncePerRequestFilter>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                if (!READY_PATH.equals(request.getRequestURI()) && !SHUTDOWN_PATH.equals(request.getRequestURI())) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!controlToken.equals(request.getHeader(CONTROL_HEADER))) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                if (READY_PATH.equals(request.getRequestURI()) && "GET".equals(request.getMethod())) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    return;
                }
                if (SHUTDOWN_PATH.equals(request.getRequestURI()) && "POST".equals(request.getMethod())) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    Thread shutdownThread = new Thread(context::close, "iam-aksk-e2e-aksk-shutdown");
                    shutdownThread.setDaemon(false);
                    shutdownThread.start();
                    return;
                }
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        });
        registration.addUrlPatterns("/__iam-aksk-e2e/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
