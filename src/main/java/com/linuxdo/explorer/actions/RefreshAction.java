package com.linuxdo.explorer.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * 刷新动作
 */
public class RefreshAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getProject() == null) return;

        ToolWindow toolWindow = ToolWindowManager.getInstance(e.getProject())
                .getToolWindow("Linux.do");

        if (toolWindow != null) {
            // 触发面板刷新
            // 由于面板内部管理刷新逻辑，这里我们可以通过重新激活窗口来触发
            toolWindow.activate(null);
        }
    }
}
