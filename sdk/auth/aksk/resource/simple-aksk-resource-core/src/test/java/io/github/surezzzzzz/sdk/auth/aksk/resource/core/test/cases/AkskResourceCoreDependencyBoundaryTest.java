package io.github.surezzzzzz.sdk.auth.aksk.resource.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.AkskResourceIntrospectionClaimConstant;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AKSK资源核心依赖边界测试。
 *
 * @author surezzzzzz
 */
class AkskResourceCoreDependencyBoundaryTest {

    private static final List<String> FORBIDDEN_TYPE_PREFIXES = Arrays.asList(
            "org.springframework.",
            "org.aspectj.",
            "com.fasterxml.jackson.",
            "javax.servlet.",
            "jakarta.servlet.",
            "io.jsonwebtoken.",
            "com.nimbusds.",
            "io.github.surezzzzzz.sdk.auth.aksk.resource.server.",
            "io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.");

    private static final List<String> REMOVED_TYPE_NAMES = Arrays.asList(
            "RequireContext",
            "RequireField",
            "RequireFieldValue",
            "RequireExpression",
            "SimpleAkskSecurityAspect",
            "SimpleAkskSecurityContextProvider",
            "SimpleAkskSecurityContextHelper",
            "SimpleAkskSecurityException",
            "SimpleAkskExpressionException",
            "SimpleAkskResourceConstant",
            "SimpleAkskResourceCoreComponent",
            "HeaderNameConverter",
            "AkskContextHelper",
            "AkskAccessEvent");

    @Test
    void shouldExposeOnlyTheRequiredActiveIntrospectionClaim() {
        assertEquals("active", AkskResourceIntrospectionClaimConstant.ACTIVE,
                "AKSK Provider 必须使用标准 active 字段判断内省令牌状态");
    }

    @Test
    void shouldKeepProductionSourceIndependentFromFrameworkAndRemovedCoreTypes() throws IOException {
        Path sourceDirectory = findProjectRoot().resolve(
                "sdk/auth/aksk/resource/simple-aksk-resource-core/src/main/java");
        assertTrue(Files.isDirectory(sourceDirectory), "必须定位AKSK资源核心生产源码目录");
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            List<Path> sourceFiles = paths.filter(path -> path.toString().endsWith(".java"))
                    .collect(java.util.stream.Collectors.toList());
            assertEquals(1, sourceFiles.size(), "AKSK Resource Core 只能保留最小协议类型");
            sourceFiles.forEach(this::assertCleanProductionSource);
        }
    }

    private void assertCleanProductionSource(Path sourceFile) {
        try {
            String source = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
            for (String forbiddenTypePrefix : FORBIDDEN_TYPE_PREFIXES) {
                assertTrue(!source.contains(forbiddenTypePrefix),
                        "生产源码不得依赖框架或Starter类型：" + sourceFile + " -> " + forbiddenTypePrefix);
            }
            for (String removedTypeName : REMOVED_TYPE_NAMES) {
                assertTrue(!source.contains(removedTypeName),
                        "生产源码不得重新引入已删除的2.x类型：" + sourceFile + " -> " + removedTypeName);
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
            throw new IllegalStateException("无法定位Gradle项目根目录");
        }
        return current;
    }
}
