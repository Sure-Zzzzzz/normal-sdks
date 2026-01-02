package io.github.surezzzzzz.sdk.naturallanguage.parser.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * 自然语言解析器配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.naturallanguage.parser")
public class NLParserProperties {

    /**
     * 是否启用自然语言解析
     */
    private boolean enabled = true;

    /**
     * 自定义停用词
     */
    private Set<String> customStopWords = new HashSet<>();
}
