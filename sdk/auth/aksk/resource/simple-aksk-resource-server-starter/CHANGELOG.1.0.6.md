# Changelog - v1.0.6

## [1.0.6] - 2026-05-06

### Fixed

- **`com.nimbusds:oauth2-oidc-sdk` 依赖范围修正**：由 `compileOnly` 改为 `api`，确保接入方无需手动引入该依赖。`oauth2-oidc-sdk` 是第三方库，不随 Spring Boot BOM 传递，接入方在运行时需要用到其中的类（如 `JWT`、`JWTClaimsSet`），`compileOnly` 会导致运行时 `ClassNotFoundException`
