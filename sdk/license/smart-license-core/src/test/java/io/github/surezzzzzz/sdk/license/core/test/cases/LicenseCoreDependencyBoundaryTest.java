package io.github.surezzzzzz.sdk.license.core.test.cases;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * License Core 依赖边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseCoreDependencyBoundaryTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = Arrays.asList(
            "import org.springframework.",
            "import com.fasterxml.jackson.",
            "import javax.servlet.",
            "import javax.persistence.",
            "import org.mybatis.",
            "import io.github.surezzzzzz.sdk.kms.core.",
            "import io.github.surezzzzzz.sdk.kms.server.",
            "import io.github.surezzzzzz.sdk.license.server.",
            "import io.github.surezzzzzz.sdk.license.verifier.");

    @Test
    void shouldKeepProductionSourceIndependentFromFrameworkAndKmsInternal() throws IOException {
        Path sourceDirectory = findProjectRoot().resolve("sdk/license/smart-license-core/src/main/java");
        log.info("扫描 License Core 生产源码依赖边界：{}", sourceDirectory);
        assertTrue(Files.isDirectory(sourceDirectory), "必须定位 License Core 的生产源码目录");
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoForbiddenImport);
        }
    }

    private void assertNoForbiddenImport(Path sourceFile) {
        try {
            List<String> lines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                for (String forbiddenImportPrefix : FORBIDDEN_IMPORT_PREFIXES) {
                    assertTrue(!line.startsWith(forbiddenImportPrefix),
                            "生产源码不得依赖禁用类型：" + sourceFile + " -> " + forbiddenImportPrefix);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取生产源码：" + sourceFile, exception);
        }
    }

    private Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("无法定位 Gradle 项目根目录");
        }
        return current;
    }
}
