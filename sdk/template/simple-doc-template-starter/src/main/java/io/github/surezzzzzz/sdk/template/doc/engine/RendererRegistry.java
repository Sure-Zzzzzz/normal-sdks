package io.github.surezzzzzz.sdk.template.doc.engine;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.renderer.Renderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renderer Registry - 渲染策略注册表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
public class RendererRegistry {

    private final Map<String, Renderer> registry = new HashMap<>();

    @Autowired
    private List<Renderer> allRenderers;

    @PostConstruct
    void init() {
        for (Renderer renderer : allRenderers) {
            register(renderer);
        }
    }

    /**
     * 注册渲染策略
     *
     * @param renderer 渲染策略
     */
    public void register(Renderer renderer) {
        registry.put(renderer.supportedSuffix(), renderer);
    }

    /**
     * 根据文件后缀查找渲染策略
     *
     * @param suffix 文件后缀，如 ".docx"
     * @return 渲染策略，未找到返回 null
     */
    public Renderer find(String suffix) {
        return registry.get(suffix);
    }
}