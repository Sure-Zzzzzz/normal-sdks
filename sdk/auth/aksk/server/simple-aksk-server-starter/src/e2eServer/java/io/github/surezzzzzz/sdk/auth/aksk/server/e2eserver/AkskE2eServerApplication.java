package io.github.surezzzzzz.sdk.auth.aksk.server.e2eserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Properties;

@SpringBootApplication
@Import({AkskE2eControlConfiguration.class, AkskE2eControlController.class, AkskE2eBootstrapConfiguration.class})
public class AkskE2eServerApplication {

    private static final String MANIFEST_FILE_PROPERTY = "iam.aksk.e2e.aksk.manifest.file";

    public static void main(String[] args) throws IOException {
        SpringApplication application = new SpringApplication(AkskE2eServerApplication.class);
        application.setDefaultProperties(Collections.singletonMap("server.address", "127.0.0.1"));
        ConfigurableApplicationContext context = application.run(args);
        try {
            publishManifest(context);
        } catch (RuntimeException | IOException exception) {
            context.close();
            throw exception;
        }
    }

    private static void publishManifest(ConfigurableApplicationContext context) throws IOException {
        File manifestFile = new File(context.getEnvironment().getRequiredProperty(MANIFEST_FILE_PROPERTY));
        File parent = manifestFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建 AKSK E2E 临时目录");
        }
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        Properties manifest = new Properties();
        manifest.setProperty("baseUrl", "http://127.0.0.1:" + port);
        File temporaryFile = File.createTempFile(manifestFile.getName(), ".tmp", parent);
        try (FileOutputStream output = new FileOutputStream(temporaryFile)) {
            manifest.store(output, null);
        }
        try {
            Files.move(temporaryFile.toPath(), manifestFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Files.move(temporaryFile.toPath(), manifestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
