# simple-doc-template-starter

文档模板渲染 SDK。提供统一的模板语法和渲染接口，当前支持 Word（.docx）格式，后续可扩展 Markdown、PDF、HTML 等格式。

模板语法统一为 `[suredt.指令:key]`，与输出格式无关，切换格式无需修改模板。其中 `suredt` 为默认标签前缀，可通过 `tagPrefix` 配置项自定义，模板中的占位符需与配置保持一致。

## 快速开始

### 1. 引入依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-doc-template-starter:1.0.0'
```

### 2. 开启配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        template:
          doc:
            enable: true
```

### 3. 准备模板

将模板文件放到任意位置，通过路径前缀指定加载方式：

| 前缀 | 说明 | 示例 |
|------|------|------|
| `classpath:` | 从类路径加载（推荐，打包后也能用） | `classpath:templates/report.docx` |
| `file:` | 从文件系统加载（绝对或相对路径） | `file:/data/templates/report.docx` |
| `http:` / `https:` | 从 URL 加载 | `https://example.com/report.docx` |

使用 `classpath:` 时，模板文件放在 `src/main/resources/templates/` 目录下即可。

### 4. 注入并使用

```java
@Autowired
private TemplateEngine templateEngine;

// 构造渲染数据（key 必须与模板中占位符的 key 完全一致，大小写敏感）
Map<String, Object> data = new HashMap<>();
data.put("clientName", "某某单位");
data.put("reportDate", "2026年4月10日");

// 快捷方法：直接拿 bytes（最常用，适合 HTTP 接口返回）
byte[] bytes = templateEngine.renderToBytes("classpath:templates/report.docx", data);

// 快捷方法：直接写文件（目录 + 文件名分开）
templateEngine.renderToFile("classpath:templates/report.docx", data, "/output", "report.docx");

// 快捷方法：直接写文件（完整路径）
templateEngine.renderToFile("classpath:templates/report.docx", data, "/output/report.docx");

// 快捷方法：写出到流（适合 HTTP 响应 OutputStream）
templateEngine.renderToStream("classpath:templates/report.docx", data, response.getOutputStream());

// 链式 API：自动推断格式（根据模板后缀）
templateEngine.render("classpath:templates/report.docx", data)
    .output()
    .toFile("/output", "report.docx");

// 链式 API：显式指定格式
templateEngine.render("classpath:templates/report.docx", data)
    .output(OutputFormat.DOCX)
    .toBytes();

// 链式 API：输出前检查是否为空
TemplateRenderResult result = templateEngine.render("classpath:templates/report.docx", data);
if (!result.isEmpty()) {
    result.output().toFile("/output/report.docx");
}
```

---

## 模板语法

所有占位符均以 `[suredt.` 开头（`suredt` 为默认前缀，可配置），`]` 结尾。以下示例均使用默认前缀。

> **Word 模板注意**：占位符必须在同一个 run 内，不能被 Word 格式断开。建议先输入纯文本，再统一设置字体格式。

### 语法汇总

| 语法 | 说明 | data 类型 |
|------|------|-----------|
| `[suredt.var:key]` | 文本变量替换 | 任意类型 |
| `[suredt.img:key]` | 插入图片 | `Image` |
| `[suredt.chart:key]` | 插入原生可编辑图表（当前仅 Word） | `Chart` |
| `[suredt.start:key]` / `[suredt.end:key]` | 条件块（成对使用） | `Boolean`（或其他真值） |
| `[suredt.for:key]` / `[suredt.endfor:key]` | 循环展开（成对使用，当前仅 Word 表格行） | `List<Map>` |

### 文本变量

```
[suredt.var:key]
```

data 中对应 key 的值会被替换为字符串。

```java
data.put("clientName", "某某单位");
data.put("reportDate", "2026年4月10日");
```

模板中：
```
尊敬的 [suredt.var:clientName]，本报告生成日期为 [suredt.var:reportDate]。
```

---

### 图片

```
[suredt.img:key]
```

data 中对应 key 的值为 `Image` 对象。宽高单位为像素（px），图片类型根据文件后缀自动推断：`.png` → PNG，`.jpg` / `.jpeg` → JPEG，`.gif` → GIF，其他后缀默认 PNG。

**宽高语义：**

| width | height | 实际行为 |
|-------|--------|----------|
| `> 0` | `> 0` | 使用指定宽高 |
| `0` | `0` | 使用原图宽高（自然尺寸） |
| `0` | `> 0` | 宽用原图宽度，高用指定值 |
| `> 0` | `0` | 宽用指定值，高按原图比例缩放 |

```java
// 指定宽高
data.put("logo", new Image("src/main/resources/logo.png", 200, 80));

// 使用原图尺寸（width=0, height=0）
data.put("photo", new Image("/path/to/photo.jpg"));

// 宽用原图，高指定
data.put("banner", new Image("/path/banner.jpg", 0, 120));

// 宽指定，高按比例
data.put("cover", new Image("/path/cover.png", 500, 0));

// 带描述
data.put("cover", new Image("/absolute/path/cover.png", 500, 300, "封面图"));

// 显式指定类型
data.put("banner", new Image("/path/banner.jpg", 600, 200, "横幅", Image.ImageType.JPEG));
```

> 图片路径支持相对路径（相对于项目工作目录）和绝对路径。

---

### Word 原生可编辑图表

```
[suredt.chart:key]
```

data 中对应 key 的值为 `Chart` 对象，渲染后在 Word 中可双击编辑数据。支持折线图、柱状图、饼图。

**基础用法：**

```java
// 折线图（默认）
data.put("trendChart", new Chart(
    "外联告警趋势",
    Arrays.asList("3/30", "3/31", "4/1", "4/2", "4/3"),
    Collections.singletonList(new Chart.Series("告警数", Arrays.<Number>asList(120, 98, 145, 200, 178))),
    500, 300
));

// 柱状图
data.put("industryChart", new Chart(
    "行业分布",
    Arrays.asList("教育", "金融", "能源", "电信"),
    Collections.singletonList(new Chart.Series("数量", Arrays.<Number>asList(350, 280, 210, 190))),
    500, 300,
    Chart.ChartType.BAR
));

// 饼图（categories 为各扇区名称，values 为各扇区数值）
data.put("typeChart", new Chart(
    "攻击类型分布",
    Arrays.asList("SQL注入", "Webshell", "暴力破解", "其他"),
    Collections.singletonList(new Chart.Series("占比", Arrays.<Number>asList(35, 25, 20, 20))),
    500, 300,
    Chart.ChartType.PIE
));
```

**样式定制（全量构造器）：**

```java
// 多系列 + 自定义颜色 + 样式
data.put("styledChart", new Chart(
    "多系列折线图",
    Arrays.asList("Q1", "Q2", "Q3", "Q4"),
    Arrays.asList(
        new Chart.Series("系列A", Arrays.<Number>asList(100, 120, 90, 150), "4F81BD"),  // 蓝色
        new Chart.Series("系列B", Arrays.<Number>asList(80, 95, 110, 130), "C0504D")   // 红色
    ),
    500, 300,
    Chart.ChartType.LINE,
    16,                          // 标题字号（px）
    Chart.LegendPosition.BOTTOM, // 图例位置：BOTTOM / TOP / LEFT / RIGHT / NONE
    true,                        // 折线图：是否平滑曲线
    true,                        // 折线图：是否显示数据点标记
    true                         // 是否显示网格线
));
```

**`Chart.Series` 颜色参数：**

```java
// 不指定颜色（使用默认色）
new Chart.Series("系列名", valueList)

// 指定颜色（RGB 十六进制，不含 #）
new Chart.Series("系列名", valueList, "4F81BD")
```

**宽高说明：** `width=0` 时使用渲染器默认宽度（约 952px），`height=0` 时使用默认高度（300px）。建议传入明确的像素值。

> 图表占位符所在段落会被整段替换为 Word 原生图表，建议在模板中单独占一行。

---

### 条件块

```
[suredt.start:key]
...任意内容...
[suredt.end:key]
```

- key 对应真值（`Boolean.TRUE`、非空 String、非空 List 等）：保留块内内容，删除首尾标记行
- key 对应假值（`Boolean.FALSE` / 空字符串 / `null` / key 不存在）：整块删除（含标记行和块内所有内容）

> **Word**：`start`/`end` 标记必须各自独占一个段落，整行只有该标记，无其他文字。

```java
data.put("hasRiskList", Boolean.TRUE);   // 保留该块
data.put("hasAssetList", Boolean.FALSE); // 删除该块
```

---

### 表格行循环

> **当前仅 Word 支持。**

在 Word 表格中使用，表格结构如下：

```
| 表头行    | [suredt.var:th_name]    | [suredt.var:th_status] | ...
| for 行    | [suredt.for:listKey]    |                        |
| 数据行    | [suredt.var:name]       | [suredt.var:status]    | ...
| endfor 行 | [suredt.endfor:listKey] |                        |
```

> for 行和 endfor 行只需第 1 列填标记，其余列留空即可。

渲染后：for 行和 endfor 行被删除，数据行按列表条数展开。

**key 命名规则**：大小写敏感，支持字母、数字、下划线、中文，不允许空格和特殊符号（如 `/`、`]`、`[`）。

**表头行**从主 data 中取值（与数据行不同，表头行直接在主 data 放 key，不需要前缀，`th_` 只是示例命名）：

```java
data.put("th_name", "线索名称");
data.put("th_status", "处置情况");
```

**数据行**从列表每条 item 中取值：

```java
List<Map<String, Object>> riskList = new ArrayList<>();

Map<String, Object> row1 = new HashMap<>();
row1.put("name", "远控木马风险");
row1.put("status", "已处置");
riskList.add(row1);

Map<String, Object> row2 = new HashMap<>();
row2.put("name", "后门程序风险");
row2.put("status", "处置中");
riskList.add(row2);

data.put("riskList", riskList);  // key 与模板中 [suredt.for:riskList] 一致
```

---

## 完整示例（Word）

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TemplateEngine templateEngine;

    public byte[] generateWeeklyReport(ReportDTO dto) {
        Map<String, Object> data = new HashMap<>();

        // 文本变量
        data.put("clientName", dto.getClientName());
        data.put("reportDate", dto.getReportDate());
        data.put("periodStart", dto.getPeriodStart());
        data.put("periodEnd", dto.getPeriodEnd());

        // 条件块（列表不为空时才显示对应模块）
        data.put("hasRiskList", !dto.getRiskList().isEmpty());
        data.put("hasAssetList", !dto.getAssetList().isEmpty());

        // 表格循环（表头 + 数据行）
        data.put("th_name", "线索名称");
        data.put("th_status", "处置情况");
        data.put("riskList", buildRiskRows(dto.getRiskList()));

        // 图片（宽高单位 px）
        data.put("logoImg", new Image("/static/logo.png", 120, 40));

        // 图表
        data.put("trendChart", new Chart(
            "本周外联趋势",
            dto.getTrendDates(),
            Collections.singletonList(new Chart.Series("告警数", dto.getTrendValues())),
            500, 280,
            Chart.ChartType.LINE
        ));

        return templateEngine.renderToBytes("classpath:templates/weekly-report.docx", data);
    }

    private List<Map<String, Object>> buildRiskRows(List<RiskItem> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RiskItem item : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", item.getName());
            row.put("status", item.getStatus());
            rows.add(row);
        }
        return rows;
    }
}
```

---

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enable` | boolean | `false` | 是否启用 SDK，必须设为 `true` |
| `templateLocation` | String | `classpath:templates/` | 模板根路径。调用时传入不含协议前缀的文件名时自动拼接；传入完整路径（含 `classpath:`、`file:` 等前缀）时忽略此配置 |
| `tagPrefix` | String | `suredt` | 标签前缀，需与模板中占位符一致 |

---

## Word 模板制作注意事项

- 占位符必须在同一个 run 内，不能被 Word 格式断开。建议先输入纯文本，再统一设置格式
- 图表占位符所在段落会被整段替换，建议单独占一行
- 条件块的 `start`/`end` 标记必须各自独占一个段落
- 表格循环的 `for`/`endfor` 行必须是表格中的独立行，其余列留空
- `[suredt.for:key]` 传入空列表时，for 行、endfor 行和中间数据行全部被删除

---

## 边界情况说明

| 场景 | 行为 |
|------|------|
| `[suredt.var:key]` 在 data 中不存在 | 替换为空字符串 |
| `[suredt.for:key]` 在 data 中不存在或类型不是 List | 删除整块（含 for/endfor 行和中间数据行） |
| `[suredt.for:key]` 传入空列表 | 同上，删除整块 |
| `[suredt.start:key]` 在 data 中不存在或对应 `null` | 整块删除 |
| `[suredt.start:key]` 对应 `Boolean.FALSE` | 整块删除 |
| `[suredt.start:key]` 对应真值（`Boolean.TRUE`、非空 String、非空 List 等） | 保留块内容，删除 start/end 标记行 |
| `[suredt.img:key]` 对应图片文件不存在 | 跳过图片插入，占位符位置留空 |

---

## 异常说明

| 异常类 | errorCode | 触发场景 |
|--------|-----------|----------|
| `TemplateNotFoundException` | `TEMPLATE_001` | 模板文件不存在 |
| `TemplateNotFoundException` | `TEMPLATE_002` | 模板后缀无对应 Renderer |
| `TemplateRenderException` | `OUTPUT_001` | 输出格式无对应 Handler |
| `TemplateRenderException` | `OUTPUT_002` | Document 类型与 Handler 不匹配 |
| `TemplateRenderException` | `OUTPUT_003` | 文件写出异常 |
| `TemplateRenderException` | `RENDER_001` | 渲染过程异常 |
| `ConditionBlockException` | `COND_001` | 条件块 start/end 的 key 不匹配 |
| `ConditionBlockException` | `COND_002` | 检测到嵌套条件块（不支持） |
| `ConditionBlockException` | `COND_003` | 条件块处理过程异常 |

