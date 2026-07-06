# Changelog

## [1.2.1] - 2026-07-03

### Feature

- **Markdown 模板支持嵌套循环**
  - `MdRenderer.findEndfor` 由朴素前向扫描改为基于深度计数的栈式匹配，正确跳过内层 `[suredt.for:key]` / `[suredt.endfor:key]` 配对，找到外层匹配的 `endfor`
  - `MdRenderer.expandLoops` 对每个外层 item 的循环体递归调用 `expandLoops` 展开内层循环，再统一 `replaceTags`，任意层嵌套均可展开
  - 内层循环项通过 `FallbackMap` 变量作用域访问外层循环变量（内层优先，外层兜底）

### Bug Fix

- **修复 Markdown 嵌套循环被无理由禁止**
  - 1.2.0 中 `findEndfor` 遇到嵌套 `[suredt.for:...]` 直接抛 `MD_001`（`markdownUnsupportedFeature("嵌套循环")`），属于实现者图省事用朴素扫描规避正确算法，而非真正的技术限制
  - 1.2.1 改为正确的栈式匹配 + 递归展开，移除该禁止

### 代码质量

- **循环错误文案收敛到 ErrorMessage**
  - `MdRenderer` 中 `expandLoops` / `findEndfor` 的 4 条硬编码错误文案（孤立 endfor / 循环项必须是 Map / key 不匹配 / 缺少 endfor）提取为 `ErrorMessage` 常量
  - 新增 `MD_LOOP_ORPHAN_ENDFOR` / `MD_LOOP_ITEM_NOT_MAP` / `MD_LOOP_KEY_MISMATCH` / `MD_LOOP_MISSING_ENDFOR`，使用 `String.format` 占位符

- **文档同步**
  - README 依赖版本更新为 `1.2.1`
  - Markdown 模板语法与制作注意事项中”不支持嵌套循环”改为”支持嵌套循环，内层可访问外层变量”
  - 异常表 `MD_001` 触发场景移除”嵌套循环”
  - 新增嵌套循环模板与 data 示例
  - 语法汇总表 `for/endfor` 行”当前仅 Word 表格行”改为”Word 表格行 / Markdown 行块，支持任意层嵌套”（DOCX 链路本就支持，无需额外改动）

### Internal

- `MdRendererTest` 原 `nestedLoopThrows` 用例替换为 8 个嵌套循环用例（首批 4 个 + 补充分支覆盖 4 个）
  - 新增 `nestedLoopExpand`：双层嵌套按风险/措施各自展开，断言完整输出
  - 新增 `nestedLoopInnerListMissing`：内层 key 缺失时整块内层删除，外层仍展开
  - 新增 `loopKeyMismatchThrows`：`for`/`endfor` key 不匹配抛异常
  - 新增 `missingEndforThrows`：缺少 `endfor` 抛异常
  - 新增 `nestedLoopInnerAccessesOuterVariable`：内层 item 通过 `FallbackMap` 作用域访问外层变量，断言 `"风险A: 措施1"`
  - 新增 `threeLevelNestedLoop`：三层嵌套（区域/风险/措施）各自展开
  - 新增 `siblingInnerLoops`：同一外层下两个并列内层 for 各自展开
  - 新增 `nestedLoopInnerListEmpty`：内层列表为空 `[]` 时整块内层删除（与 key 缺失的 `null` 分属不同代码路径）

### Compatibility

- 不改变 DOCX 主链路对外 API 和业务语义
- 1.2.0 已禁止嵌套循环的模板，1.2.1 起正常展开；此前依赖“嵌套即抛异常”的业务需调整为正常嵌套模板

### Validation

- `:sdk:template:simple-doc-template-starter:compileJava`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:compileTestJava`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:test`：BUILD SUCCESSFUL（129 tests，0 failures / 0 errors / 0 skipped，Gradle 8.5 + Java 11 + `--no-daemon`）

---
