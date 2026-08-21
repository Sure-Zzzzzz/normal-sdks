package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Capture Starter 真实 Spring Security 端到端测试配置。
 *
 * @author surezzzzzz
 */
@TestConfiguration
class XffCaptureSecurityTestConfiguration {

    @Bean
    XffSecurityRequestObservation xffSecurityRequestObservation() {
        return new XffSecurityRequestObservation();
    }

    @Bean
    SecurityFilterChain xffSecurityTestFilterChain(
            HttpSecurity http,
            XffSecurityRequestObservation observation)
            throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                .antMatchers(
                        "/xff-security-view",
                        "/xff-security-parameters",
                        "/xff-security-body")
                .permitAll()
                .antMatchers("/xff-security-authenticated")
                .authenticated()
                .antMatchers("/xff-security-forbidden")
                .hasRole("allowed")
                .anyRequest().denyAll()
                .and()
                .httpBasic(withDefaults())
                .addFilterBefore(
                        new XffSecurityRequestObservationFilter(observation),
                        FilterSecurityInterceptor.class);
        return http.build();
    }

    @Bean
    InMemoryUserDetailsManager xffSecurityTestUsers() {
        return new InMemoryUserDetailsManager(User.withUsername("xff-test-user")
                .password("{noop}xff-test-password")
                .roles("user")
                .build());
    }

}

class XffSecurityRequestObservation {

    private volatile SecurityRequestView view;

    void record(HttpServletRequest request) {
        this.view = new SecurityRequestView(
                System.identityHashCode(request),
                request.getClass().getName(),
                request.getHeader(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR) != null,
                request.getRemoteAddr());
    }

    SecurityRequestView getView() {
        return view;
    }
}

final class XffSecurityRequestObservationFilter extends OncePerRequestFilter {

    private final XffSecurityRequestObservation observation;

    XffSecurityRequestObservationFilter(XffSecurityRequestObservation observation) {
        this.observation = observation;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        observation.record(request);
        filterChain.doFilter(request, response);
    }
}

final class SecurityRequestView {

    private final int identity;
    private final String type;
    private final boolean xffPresent;
    private final String remoteAddr;

    SecurityRequestView(int identity, String type, boolean xffPresent,
                        String remoteAddr) {
        this.identity = identity;
        this.type = type;
        this.xffPresent = xffPresent;
        this.remoteAddr = remoteAddr;
    }

    int getIdentity() {
        return identity;
    }

    String getType() {
        return type;
    }

    boolean isXffPresent() {
        return xffPresent;
    }

    String getRemoteAddr() {
        return remoteAddr;
    }
}

/**
 * 真实 HTTP 测试 Controller。
 *
 * @author surezzzzzz
 */
@RestController
class XffCaptureSecurityTestController {

    private final XffCaptureService xffCaptureService;

    XffCaptureSecurityTestController(XffCaptureService xffCaptureService) {
        this.xffCaptureService = xffCaptureService;
    }

    @GetMapping("/xff-security-view")
    String securityView(HttpServletRequest request) {
        return requestView(request);
    }

    @GetMapping("/xff-security-authenticated")
    String authenticated(HttpServletRequest request) {
        return requestView(request);
    }

    @GetMapping("/xff-security-forbidden")
    String forbidden(HttpServletRequest request) {
        return requestView(request);
    }

    @GetMapping("/xff-security-parameters")
    String parameters(@RequestParam("single") String single,
                      @RequestParam("multiple") java.util.List<String> multiple) {
        return "single=" + single + "|multiple=" + multiple;
    }

    @PostMapping("/xff-security-body")
    String body(@RequestBody String requestBody) {
        return requestBody;
    }

    private String requestView(HttpServletRequest request) {
        return "headerPresent="
                + (request.getHeader(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR) != null)
                + "|present=" + xffCaptureService.capture(request).isPresent()
                + "|remoteAddr=" + request.getRemoteAddr();
    }
}
