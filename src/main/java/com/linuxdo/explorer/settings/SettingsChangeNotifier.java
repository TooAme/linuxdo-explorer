package com.linuxdo.explorer.settings;

import com.intellij.util.messages.Topic;

/**
 * 设置变更通知接口
 */
public interface SettingsChangeNotifier {
    
    Topic<SettingsChangeNotifier> TOPIC = Topic.create("LinuxDo Settings Changed", SettingsChangeNotifier.class);
    
    /**
     * 设置已变更，需要刷新数据
     */
    void settingsChanged();
}
