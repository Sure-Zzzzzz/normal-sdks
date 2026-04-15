package io.github.surezzzzzz.sdk.audit.aksk.test.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试用的Controller
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@RestController
@Slf4j
public class TestController {

    @GetMapping("/test/api")
    public String testApi() {
        log.info("Test API called");
        return "ok";
    }

    @GetMapping("/api/test/{id}")
    public String apiTest(@PathVariable String id) {
        log.info("API test called with id: {}", id);
        return "ok-" + id;
    }

    @GetMapping("/api/jwt/test/{id}")
    public String jwtTest(@PathVariable String id) {
        log.info("JWT test called with id: {}", id);
        return "jwt-ok-" + id;
    }
}
