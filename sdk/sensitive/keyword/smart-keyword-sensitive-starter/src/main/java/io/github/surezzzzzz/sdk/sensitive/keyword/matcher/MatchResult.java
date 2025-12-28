package io.github.surezzzzzz.sdk.sensitive.keyword.matcher;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Match Result Entity
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 匹配到的关键词
     */
    private String keyword;

    /**
     * 开始位置
     */
    private int startIndex;

    /**
     * 结束位置
     */
    private int endIndex;

    /**
     * 匹配的文本内容
     */
    private String matchedText;

    /**
     * 获取匹配文本长度
     *
     * @return 长度
     */
    public int getLength() {
        return endIndex - startIndex;
    }

    @Override
    public String toString() {
        return String.format("MatchResult{keyword='%s', range=[%d,%d), text='%s'}",
                keyword, startIndex, endIndex, matchedText);
    }
}
