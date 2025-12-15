package com.linuxdo.explorer.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.linuxdo.explorer.settings.LinuxDoSettingsConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * 打开设置动作
 */
public class OpenSettingsAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
                e.getProject(),
                LinuxDoSettingsConfigurable.class
        );
    }
}
