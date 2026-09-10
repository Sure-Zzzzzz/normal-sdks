package io.github.surezzzzzz.sdk.auth.collaboration.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IAM 与 AKSK 协作资源 Demo 应用。
 *
 * <p>独立 Resource Consumer：显式装配公共 Resource Server、IAM Resource Provider、
 * AKSK Resource Provider 与 Data Permission MVC 适配，保护同一个真实业务 Controller。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class IamAkskCollaborationDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamAkskCollaborationDemoApplication.class, args);
    }
}
