package io.github.surezzzzzz.sdk.template.doc.test.helper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * 图表数据 helper - 为 Word 原生 Chart 测试提供假数据
 * <p>
 * 数据来源说明：
 * <ul>
 *   <li>数值参考测试类 {@link io.github.surezzzzzz.sdk.template.doc.test.cases.TemplateEngineRenderTest} 中的 buildFullData()</li>
 *   <li>图表类型基于图注文字和占位符 key 推断：外联/攻击 态势为柱状图，趋势为折线图</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public class ChartDataHelper {

    private ChartDataHelper() {}

    // ==================== Chart 1：国内态势-外联（柱状图） ====================

    /**
     * Chart 1 数据：国内态势-外联 Top 行业
     * <p>
     * 占位符 key: outboundChart<br>
     * 模板图注文字: "图1 国内态势-外联 Top 行业分布"（推断）
     *
     * <table>
     *   <caption>数据来源</caption>
     *   <tr><th>字段</th><th>来源</th></tr>
     *   <tr><td>行业列表</td><td>topOutboundIndustry = "教育"，outboundIndustryList = "电信、经营性公众互联网、能源"</td></tr>
     *   <tr><td>威胁类型</td><td>topOutboundThreat = "银狐"，outboundThreatList = "木马、后门、GH0st、病毒"</td></tr>
     *   <tr><td>数值</td><td>topOutboundCount = "12345"（教育行业外联量，Top1）</td></tr>
     * </table>
     */
    public static Chart1Data buildChart1Data() {
        Chart1Data d = new Chart1Data();
        d.setTitle("国内态势-外联 Top 行业分布");
        d.setXAxisTitle("行业");
        d.setYAxisTitle("外联量");
        // 数值按外联量排序
        d.setCategories(new String[]{"教育", "电信", "经营性公众互联网", "能源"});
        d.setSeries(new SeriesData[]{
            new SeriesData("外联量", new double[]{12345, 9800, 7200, 5100}),
        });
        return d;
    }

    // ==================== Chart 2：国内态势-攻击（柱状图） ====================

    /**
     * Chart 2 数据：国内态势-攻击 Top 行业
     * <p>
     * 占位符 key: inboundChart<br>
     * 模板图注文字: "图2 国内态势-攻击 Top 行业分布"（推断）
     *
     * <table>
     *   <caption>数据来源</caption>
     *   <tr><th>字段</th><th>来源</th></tr>
     *   <tr><td>行业列表</td><td>topInboundIndustry = "教育"，inboundIndustryList = "电信、其他、银行、广电"</td></tr>
     *   <tr><td>规则命中</td><td>topInboundRules = "请求中包含zgrab扫描器特征..."</td></tr>
     *   <tr><td>数值</td><td>topInboundCount = "8888"（教育行业攻击量，Top1）</td></tr>
     * </table>
     */
    public static Chart2Data buildChart2Data() {
        Chart2Data d = new Chart2Data();
        d.setTitle("国内态势-攻击 Top 行业分布");
        d.setXAxisTitle("行业");
        d.setYAxisTitle("攻击次数");
        d.setCategories(new String[]{"教育", "电信", "其他", "银行", "广电"});
        d.setSeries(new SeriesData[]{
            new SeriesData("攻击次数", new double[]{8888, 7600, 6200, 4500, 3200}),
        });
        return d;
    }

    // ==================== Chart 3：本单位外联趋势（折线图） ====================

    /**
     * Chart 3 数据：本单位外联趋势（近7天折线图）
     * <p>
     * 占位符 key: unitOutboundChart<br>
     * 模板图注文字: "图3 本单位外联趋势"（推断）<br>
     * 数据对应周期: 2026-03-30 ~ 2026-04-05（7天）
     *
     * <table>
     *   <caption>数据来源</caption>
     *   <tr><th>字段</th><th>来源</th></tr>
     *   <tr><td>周总计</td><td>unitOutboundTotal = "63712"</td></tr>
     *   <tr><td>环比变化</td><td>unitOutboundChangeDir = "下降"，unitOutboundChangeCount = "9026"</td></tr>
     *   <tr><td>峰值日期/次数</td><td>unitOutboundPeakDate = "2026-04-28"，unitOutboundPeakCount = "11500"</td></tr>
     *   <tr><td>Top 目标</td><td>unitOutboundTargetTop3 = "vs.haifti.com、1.uqidashi.com、185.27.134.11"</td></tr>
     *   <tr><td>威胁组织</td><td>topOutboundIntelOrg = "银狐"，unitOutboundIntelOrgList = "mylobot、phorpiex、dorkbot"</td></tr>
     * </table>
     */
    public static Chart3Data buildChart3Data() {
        Chart3Data d = new Chart3Data();
        d.setTitle("本单位外联趋势（近7天）");
        d.setXAxisTitle("日期");
        d.setYAxisTitle("外联量");
        // 7天数据：周总计 63712，均值约 9100，峰值 11500
        // 分布：周一低点 → 周三高峰 → 周末回落
        d.setCategories(new String[]{
            "03-30", "03-31", "04-01", "04-02", "04-03", "04-04", "04-05"
        });
        d.setSeries(new SeriesData[]{
            new SeriesData("外联量", new double[]{7200, 8500, 9800, 11500, 10200, 8800, 7712}),
        });
        return d;
    }

    // ==================== Chart 4：本单位攻击趋势（折线图） ====================

    /**
     * Chart 4 数据：本单位攻击趋势（近7天折线图）
     * <p>
     * 占位符 key: unitInboundChart<br>
     * 模板图注文字: "图4 本单位攻击趋势"（推断）<br>
     * 数据对应周期: 2026-03-30 ~ 2026-04-05（7天）
     *
     * <table>
     *   <caption>数据来源</caption>
     *   <tr><th>字段</th><th>来源</th></tr>
     *   <tr><td>峰值日期/次数</td><td>unitInboundPeakDate = "2026-04-24"，unitInboundPeakCount = "178000"</td></tr>
     *   <tr><td>Top 攻击源</td><td>unitInboundAttackerTop3 = "11.53.73.197、11.53.73.195、11.53.73.186"</td></tr>
     *   <tr><td>攻击类型</td><td>unitInboundAttackTypeList = "SQL注入、Webshell攻击、隐蔽隧道..."</td></tr>
     * </table>
     */
    public static Chart4Data buildChart4Data() {
        Chart4Data d = new Chart4Data();
        d.setTitle("本单位攻击趋势（近7天）");
        d.setXAxisTitle("日期");
        d.setYAxisTitle("攻击次数");
        // 攻击量远大于外联量，峰值 178000，远超周均值
        // 分布：周初激增后持续高位
        d.setCategories(new String[]{
            "03-30", "03-31", "04-01", "04-02", "04-03", "04-04", "04-05"
        });
        d.setSeries(new SeriesData[]{
            new SeriesData("攻击次数", new double[]{145000, 162000, 178000, 155000, 140000, 135000, 128000}),
        });
        return d;
    }

    // ==================== 数据模型 ====================

    @Data
    public static class Chart1Data {
        private String title;
        private String xAxisTitle;
        private String yAxisTitle;
        private String[] categories;
        private SeriesData[] series;
    }

    @Data
    public static class Chart2Data {
        private String title;
        private String xAxisTitle;
        private String yAxisTitle;
        private String[] categories;
        private SeriesData[] series;
    }

    @Data
    public static class Chart3Data {
        private String title;
        private String xAxisTitle;
        private String yAxisTitle;
        private String[] categories;
        private SeriesData[] series;
    }

    @Data
    public static class Chart4Data {
        private String title;
        private String xAxisTitle;
        private String yAxisTitle;
        private String[] categories;
        private SeriesData[] series;
    }

    @Getter
    @AllArgsConstructor
    public static class SeriesData {
        private final String name;
        private final double[] values;
    }
}