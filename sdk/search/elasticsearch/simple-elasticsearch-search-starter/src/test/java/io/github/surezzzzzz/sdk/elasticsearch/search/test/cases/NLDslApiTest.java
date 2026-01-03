package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 自然语言转DSL API测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
@AutoConfigureMockMvc
class NLDslApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("查询DSL - QueryRequest (README示例)")
    void testQueryDsl() throws Exception {
        // 目标DSL: README中的查询示例
        // index: user_behavior
        // query: age >= 18 AND city IN ["Beijing", "Shanghai"]
        // pagination: offset, page=1, size=50, sort by createTime desc
        String nlQuery = "查询user_behavior索引，age大于等于18并且city在Beijing、Shanghai，按createTime降序，取50条";

        MvcResult result = mockMvc.perform(get("/api/nl/dsl")
                        .param("text", nlQuery)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        log.info("QueryRequest DSL:\n{}", responseBody);
    }

    @Test
    @DisplayName("聚合DSL - AggRequest (README示例)")
    void testAggDsl() throws Exception {
        // 目标DSL: README中的聚合示例
        // index: user_behavior
        // query: status == "active"
        // aggs:
        //   1. city_distribution (terms on city, size=10, with nested avg_age)
        //   2. daily_stats (date_histogram on createTime, interval=1d)
        //
        // 注意：这需要nlparser支持：
        // - 自定义聚合名称
        // - 嵌套聚合
        // - 多个顶级聚合
        String nlQuery = "查询user_behavior索引，status等于active，按city分组前10名计算age平均值，按createTime每天统计";

        MvcResult result = mockMvc.perform(get("/api/nl/dsl")
                        .param("text", nlQuery)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        log.info("AggRequest DSL:\n{}", responseBody);
    }
}
