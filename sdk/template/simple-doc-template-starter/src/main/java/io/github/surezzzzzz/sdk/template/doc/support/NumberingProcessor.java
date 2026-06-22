package io.github.surezzzzzz.sdk.template.doc.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNum;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * 列表编号处理器：解析 numbering.xml，按段落顺序为每个 list 段落生成前缀字符串。
 * <p>
 * 维护 (numId, ilvl) 计数器，按 lvlText 模板替换 %1..%9 为对应级别当前计数值。
 * 支持的 numFmt：decimal / chineseCounting / chineseCountingThousand /
 * upperRoman / lowerRoman / upperLetter / lowerLetter / japaneseCounting / bullet。
 * 不支持的 numFmt 一律退化为 decimal。
 *
 * @author surezzzzzz
 */
@Slf4j
public class NumberingProcessor {

    /**
     * abstractNumId → ilvl → 级别定义
     */
    private final Map<BigInteger, Map<Integer, LevelDef>> abstractNumLevels = new HashMap<>();

    /**
     * numId → abstractNumId
     */
    private final Map<BigInteger, BigInteger> numToAbstract = new HashMap<>();

    /**
     * (numId, ilvl) 当前计数
     */
    private final Map<String, Integer> counters = new HashMap<>();

    /**
     * 创建空编号处理器
     *
     * @return 空编号处理器
     */
    public static NumberingProcessor empty() {
        return new NumberingProcessor();
    }

    /**
     * 从 XWPFDocument 构建（解析 numbering.xml）
     */
    public static NumberingProcessor from(XWPFDocument doc) {
        NumberingProcessor np = new NumberingProcessor();
        try {
            XWPFNumbering numbering = doc.getNumbering();
            if (numbering == null) return np;

            // abstract num
            for (XWPFAbstractNum xwpfAbs : numbering.getAbstractNums()) {
                if (xwpfAbs == null) continue;
                CTAbstractNum abs = xwpfAbs.getCTAbstractNum();
                if (abs == null) continue;
                BigInteger absId = abs.getAbstractNumId();
                if (absId == null) continue;
                Map<Integer, LevelDef> levels = new HashMap<>();
                for (CTLvl lvl : abs.getLvlList()) {
                    BigInteger ilvlBi = lvl.getIlvl();
                    int ilvl = ilvlBi != null ? ilvlBi.intValue() : 0;
                    String numFmt = "decimal";
                    if (lvl.getNumFmt() != null && lvl.getNumFmt().getVal() != null) {
                        numFmt = lvl.getNumFmt().getVal().toString();
                    }
                    String lvlText = "%" + (ilvl + 1) + ".";
                    if (lvl.getLvlText() != null && lvl.getLvlText().getVal() != null) {
                        lvlText = lvl.getLvlText().getVal();
                    }
                    int start = 1;
                    if (lvl.getStart() != null && lvl.getStart().getVal() != null) {
                        start = lvl.getStart().getVal().intValue();
                    }
                    levels.put(ilvl, new LevelDef(numFmt, lvlText, start));
                }
                np.abstractNumLevels.put(absId, levels);
            }
            // num → abstractNum
            for (XWPFNum xwpfNum : numbering.getNums()) {
                if (xwpfNum == null) continue;
                CTNum num = xwpfNum.getCTNum();
                if (num == null) continue;
                BigInteger numId = num.getNumId();
                if (numId == null) continue;
                if (num.getAbstractNumId() != null && num.getAbstractNumId().getVal() != null) {
                    np.numToAbstract.put(numId, num.getAbstractNumId().getVal());
                }
            }
        } catch (Exception e) {
            log.debug("解析 numbering.xml 失败，列表编号将退化为空: {}", e.getMessage());
        }
        return np;
    }

    /**
     * 为段落生成编号前缀（如「一、」「1.1」「a)」「• 」），段落不属于任何列表时返回 ""。
     * 同时推进对应级别的计数器。
     */
    public String nextPrefix(XWPFParagraph para) {
        BigInteger numId = para.getNumID();
        if (numId == null) return "";
        BigInteger absId = numToAbstract.get(numId);
        if (absId == null) return "";
        Map<Integer, LevelDef> levels = abstractNumLevels.get(absId);
        if (levels == null) return "";

        BigInteger ilvlBi = para.getNumIlvl();
        int ilvl = ilvlBi != null ? ilvlBi.intValue() : 0;
        LevelDef def = levels.get(ilvl);
        if (def == null) return "";

        // 推进当前级别计数；若是 bullet，无需计数
        boolean isBullet = "bullet".equalsIgnoreCase(def.numFmt);
        int current = isBullet ? 0 : advance(numId, ilvl, def.start);

        // 组装 lvlText：替换 %1..%9
        StringBuilder out = new StringBuilder();
        String tpl = def.lvlText;
        int i = 0;
        while (i < tpl.length()) {
            char c = tpl.charAt(i);
            if (c == '%' && i + 1 < tpl.length() && Character.isDigit(tpl.charAt(i + 1))) {
                int level = tpl.charAt(i + 1) - '0' - 1;  // %1 → ilvl 0
                int value;
                if (level == ilvl) {
                    value = current;
                } else {
                    LevelDef ldef = levels.get(level);
                    int start = ldef != null ? ldef.start : 1;
                    Integer cur = counters.get(numId.toString() + ":" + level);
                    value = cur != null ? cur : start;
                }
                out.append(formatNumber(value, level == ilvl ? def.numFmt
                        : (levels.get(level) != null ? levels.get(level).numFmt : def.numFmt)));
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        if (isBullet) {
            // bullet 模板里通常是装饰字符（•/▪），直接当成纯文本前缀
            return out.toString() + " ";
        }
        return out.toString() + " ";
    }

    private int advance(BigInteger numId, int ilvl, int start) {
        String key = numId.toString() + ":" + ilvl;
        Integer cur = counters.get(key);
        int next = cur == null ? start : cur + 1;
        counters.put(key, next);
        // 推进上层级也会重置下层计数；这里简单实现：推进时重置所有更深层级（>ilvl）
        counters.entrySet().removeIf(e -> {
            String[] parts = e.getKey().split(":");
            if (!parts[0].equals(numId.toString())) return false;
            int l = Integer.parseInt(parts[1]);
            return l > ilvl;
        });
        return next;
    }

    /**
     * 把 1/2/3... 格式化成对应 numFmt
     */
    private String formatNumber(int n, String numFmt) {
        if (n <= 0) return "";
        switch (numFmt == null ? "decimal" : numFmt.toLowerCase()) {
            case "chinesecounting":
            case "japanesecounting":
                return chineseLowerNumber(n);
            case "chinesecountingthousand":
                return chineseUpperNumber(n);
            case "upperroman":
                return toRoman(n);
            case "lowerroman":
                return toRoman(n).toLowerCase();
            case "upperletter":
                return toLetter(n).toUpperCase();
            case "lowerletter":
                return toLetter(n).toLowerCase();
            case "bullet":
                return "";
            case "decimal":
            default:
                return String.valueOf(n);
        }
    }

    private static final char[] CN_LOWER = "零一二三四五六七八九".toCharArray();
    private static final char[] CN_UPPER = "零壹贰叁肆伍陆柒捌玖".toCharArray();

    /**
     * 简化中文小写：覆盖 1..99，超过 99 直接拼数字。「一二三四…」「十一」「二十」「九十九」
     */
    private String chineseLowerNumber(int n) {
        if (n < 0) return "";
        if (n < 10) return String.valueOf(CN_LOWER[n]);
        if (n < 20) {
            return n == 10 ? "十" : "十" + CN_LOWER[n - 10];
        }
        if (n < 100) {
            int tens = n / 10;
            int ones = n % 10;
            return CN_LOWER[tens] + "十" + (ones == 0 ? "" : String.valueOf(CN_LOWER[ones]));
        }
        return String.valueOf(n);
    }

    private String chineseUpperNumber(int n) {
        if (n < 0) return "";
        if (n < 10) return String.valueOf(CN_UPPER[n]);
        if (n < 20) {
            return n == 10 ? "拾" : "拾" + CN_UPPER[n - 10];
        }
        if (n < 100) {
            int tens = n / 10;
            int ones = n % 10;
            return CN_UPPER[tens] + "拾" + (ones == 0 ? "" : String.valueOf(CN_UPPER[ones]));
        }
        return String.valueOf(n);
    }

    private String toRoman(int n) {
        int[] vals = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] syms = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length && n > 0; i++) {
            while (n >= vals[i]) {
                sb.append(syms[i]);
                n -= vals[i];
            }
        }
        return sb.toString();
    }

    private String toLetter(int n) {
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /**
     * 单级定义
     */
    private static class LevelDef {
        final String numFmt;
        final String lvlText;
        final int start;

        LevelDef(String numFmt, String lvlText, int start) {
            this.numFmt = numFmt;
            this.lvlText = lvlText;
            this.start = start;
        }
    }
}
