package top.iloved.visiteditor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * 阅读量管理插件：提供文章阅读量的可视化查看与修改能力。
 */
@Slf4j
@Component
public class VisitEditorPlugin extends BasePlugin {

    public VisitEditorPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        log.info("Visit editor plugin started.");
    }

    @Override
    public void stop() {
        log.info("Visit editor plugin stopped.");
    }
}
