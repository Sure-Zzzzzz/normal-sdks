package io.github.surezzzzzz.sdk.template.doc.handler.pdf;

import lombok.Getter;

import java.io.File;
import java.util.*;

/**
 * PDF Font Registration Plan
 *
 * @author surezzzzzz
 */
@Getter
public class PdfFontRegistrationPlan {

    /** 所有字体文件 */
    private final List<File> fontFiles;

    /** 每个字体文件对应的族名列表（仅用于注册） */
    private final Map<File, List<String>> fileFamilyNames;

    /** 所有族名（不去重，供 CSS font-family 链使用） */
    private final List<String> familyNames;

    /** CSS font-family 字符串，已加引号逗号分隔 */
    private final String cssFontFamily;

    public PdfFontRegistrationPlan(List<File> fontFiles,
                                   Map<File, List<String>> fileFamilyNames,
                                   List<String> familyNames,
                                   String cssFontFamily) {
        this.fontFiles = fontFiles == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(fontFiles));
        this.fileFamilyNames = fileFamilyNames == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fileFamilyNames));
        this.familyNames = familyNames == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(familyNames));
        this.cssFontFamily = cssFontFamily;
    }
}
