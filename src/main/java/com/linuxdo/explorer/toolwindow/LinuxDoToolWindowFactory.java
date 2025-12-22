package com.linuxdo.explorer.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Tool Window 工厂类
 */
public class LinuxDoToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        LinuxDoToolWindowPanel panel = new LinuxDoToolWindowPanel(project, toolWindow);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        content.setCloseable(false);
        content.setPreferredFocusableComponent(panel);
        toolWindow.getContentManager().addContent(content);
        
        // 移除工具窗口组件边框
        toolWindow.getComponent().setBorder(null);
    }
}
