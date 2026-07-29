package io.github.surezzzzzz.sdk.kms.client.e2eserver;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

/**
 * 固定 Spring Boot 2.7.9 的发布版 KMS E2E Server 启动应用。
 *
 * <p>仅用于跨版本 Client E2E：加载发布坐标的 Server、可丢弃本地测试库与测试身份解析器，
 * 不参与 Client 测试源码或低版本 Client 的编译与运行。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class KmsClientE2eServerApplication {

    private static final String SERVER_FILE_PROPERTY = "kms.e2e.server.file";
    private static final String SCHEMA_RESOURCE = "smart-kms-server-1.0.0-schema.sql";
    private static final String BASE_URL_PROPERTY = "baseUrl";
    private static final String TENANT_HEADER = "X-Test-Tenant";
    private static final String PRINCIPAL_HEADER = "X-Test-Principal";
    private static final String REQUEST_ID_HEADER = "X-Test-Request-Id";

    public static void main(String[] args) throws IOException {
        ConfigurableApplicationContext context = SpringApplication.run(KmsClientE2eServerApplication.class, args);
        try {
            context.getBean(KmsClientE2eServerApplication.class).initialize(context);
        } catch (RuntimeException | IOException exception) {
            context.close();
            throw exception;
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("缺少 KMS E2E Server 运行参数");
        }
        return value;
    }

    /**
     * 注册仅用于发布版 Server E2E 的测试主体解析器。
     *
     * <p>仅接受专用测试头；该实现绝不能作为生产认证方式。</p>
     *
     * @return 测试主体解析器
     */
    @Bean
    public KmsPrincipalResolver kmsPrincipalResolver() {
        return new KmsPrincipalResolver() {
            @Override
            public KmsRequestContext resolve(HttpServletRequest request) {
                String tenantId = request.getHeader(TENANT_HEADER);
                String principalId = request.getHeader(PRINCIPAL_HEADER);
                String requestId = request.getHeader(REQUEST_ID_HEADER);
                if (tenantId == null || principalId == null || requestId == null) {
                    return null;
                }
                return new KmsRequestContext(new KmsPrincipal(principalId, tenantId,
                        new HashSet<String>(Arrays.asList(SmartKmsServerConstant.SCOPE_MANAGE,
                                SmartKmsServerConstant.SCOPE_SIGN, SmartKmsServerConstant.SCOPE_VERIFY,
                                SmartKmsServerConstant.SCOPE_ENCRYPT, SmartKmsServerConstant.SCOPE_DECRYPT,
                                SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY))), requestId);
            }
        };
    }

    /**
     * 初始化可丢弃测试库并发布仅含连接信息的临时清单。
     *
     * @param context 已启动的 Web 上下文
     * @throws IOException 清单无法写入时抛出
     */
    public void initialize(ConfigurableApplicationContext context) throws IOException {
        new ResourceDatabasePopulator(new ClassPathResource(SCHEMA_RESOURCE)).execute(context.getBean(DataSource.class));
        ServletWebServerApplicationContext webContext = (ServletWebServerApplicationContext) context;
        Properties manifest = new Properties();
        manifest.setProperty(BASE_URL_PROPERTY, "http://127.0.0.1:" + webContext.getWebServer().getPort());
        File serverFile = new File(requiredProperty(SERVER_FILE_PROPERTY));
        File parent = serverFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建 KMS E2E Server 临时目录");
        }
        try (FileOutputStream output = new FileOutputStream(serverFile)) {
            manifest.store(output, null);
        }
    }

}
