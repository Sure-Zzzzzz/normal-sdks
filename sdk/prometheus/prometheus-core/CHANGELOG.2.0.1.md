# CHANGELOG 2.0.1

类型：Security Patch

## 变更内容

- 升级 `com.google.protobuf:protobuf-java` 从 `3.25.1` 至 `3.25.5`，修复已知 CVE（`GHSA-735f-pc8j-v9w8`）。
- 升级 `com.google.protobuf:protobuf-java-util` 从 `3.25.1` 至 `3.25.5`，与 `protobuf-java` 保持同一版本线。
- 联动升级传递依赖 `com.google.guava:guava` 从 `30.1.1-jre` 至 `32.0.1-jre`，修复已知 CVE（`GHSA-5mg8-w23w-74h3`、`GHSA-7g45-4rm6-3mm3`）。
- 未采用 Protobuf `4.x` 系列：该系列移除了 `makeExtensionsImmutable()` 等 3.x 生成代码依赖的 API，会导致本模块已签入的 `Remote.java`、`Types.java` 编译失败，需重新生成协议代码，风险与改动范围超出本次安全补丁目标。`3.25.5` 同为无 CVE 版本且不改变生成代码。
- 以上版本均已使用 OSV 数据库按精确版本号核验，确认无已知漏洞。

- 本模块 `build.gradle` 不再显式声明 `protobuf-java`/`protobuf-java-util` 版本号，统一由仓库根 `build.gradle` 的 `artifactConstraints` 管理，避免版本口径分裂。

## 向后兼容性

协议 API 未变，编译产物兼容。使用者升级时将 Maven/Gradle 坐标升级至 `2.0.1` 即可。
