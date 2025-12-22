package com.linuxdo.explorer.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import com.linuxdo.explorer.util.LinuxDoBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 设置界面配置
 */
public class LinuxDoSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private JTextArea cookieTextArea;
    private JTextArea userAgentTextArea;
    private JCheckBox showImagesCheckBox;
    private JCheckBox showEmojiCheckBox;
    private JCheckBox ignoreLineBreaksCheckBox;
    private JCheckBox compactModeCheckBox;
    private JComboBox<String> imageFilterModeComboBox;  // 替换原来的grayscaleImagesCheckBox
    private JCheckBox showPostInfoCheckBox;
    private JComboBox<String> autoRefreshComboBox;
    private JComboBox<String> fontSizeComboBox;
    private JComboBox<String> disguiseModeComboBox;
    // OpenAI 配置
    private JTextField openaiUrlField;
    private JPasswordField openaiKeyField;
    private JTextField openaiModelField;
    // 加载数量设置
    private JSpinner topicsPerLoadSpinner;
    private JSpinner repliesPerLoadSpinner;
    // 通知设置
    private JComboBox<String> notificationModeComboBox;
    // 预览模式设置
    private JComboBox<String> previewModeComboBox;

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "Linux.do Explorer";
    }

    @Override
    public @Nullable JComponent createComponent() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 显示设置面板
        JPanel displayPanel = createDisplaySettingsPanel();
        mainPanel.add(displayPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // 登录信息面板
        JPanel loginPanel = createLoginPanel();
        mainPanel.add(loginPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // OpenAI 配置面板
        JPanel openaiPanel = createOpenAIPanel();
        mainPanel.add(openaiPanel);

        loadSettings();
        return mainPanel;
    }

    private JPanel createDisplaySettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                LinuxDoBundle.message("settings.displayOptions"),
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        showImagesCheckBox = new JCheckBox(LinuxDoBundle.message("settings.showImages"));
        showEmojiCheckBox = new JCheckBox(LinuxDoBundle.message("settings.showEmoji"));
        ignoreLineBreaksCheckBox = new JCheckBox(LinuxDoBundle.message("settings.ignoreLineBreaks"));
        compactModeCheckBox = new JCheckBox(LinuxDoBundle.message("settings.compactMode"));
        showPostInfoCheckBox = new JCheckBox(LinuxDoBundle.message("settings.showPostInfo"));

        panel.add(showImagesCheckBox);
        panel.add(showEmojiCheckBox);
        panel.add(ignoreLineBreaksCheckBox);
        panel.add(compactModeCheckBox);
        panel.add(showPostInfoCheckBox);
        panel.add(Box.createVerticalStrut(5));

        // 图片滤镜模式
        JPanel imageFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        imageFilterPanel.add(new JLabel(LinuxDoBundle.message("settings.imageFilterMode")));
        imageFilterModeComboBox = new JComboBox<>(new String[]{
                LinuxDoBundle.message("settings.imageFilterMode.normal"),
                LinuxDoBundle.message("settings.imageFilterMode.grayscale"),
                LinuxDoBundle.message("settings.imageFilterMode.halftone")
        });
        imageFilterPanel.add(imageFilterModeComboBox);
        imageFilterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(imageFilterPanel);
        panel.add(Box.createVerticalStrut(10));

        // 自动刷新
        JPanel autoRefreshPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        autoRefreshPanel.add(new JLabel(LinuxDoBundle.message("settings.autoRefresh")));
        autoRefreshComboBox = new JComboBox<>(new String[]{
                LinuxDoBundle.message("settings.autoRefresh.off"),
                LinuxDoBundle.message("settings.autoRefresh.5min"),
                LinuxDoBundle.message("settings.autoRefresh.10min"),
                LinuxDoBundle.message("settings.autoRefresh.20min"),
                LinuxDoBundle.message("settings.autoRefresh.60min")
        });
        autoRefreshPanel.add(autoRefreshComboBox);
        autoRefreshPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(autoRefreshPanel);
        panel.add(Box.createVerticalStrut(5));

        // 字号设置
        JPanel fontSizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        fontSizePanel.add(new JLabel(LinuxDoBundle.message("settings.fontSize")));
        fontSizeComboBox = new JComboBox<>(new String[]{
                LinuxDoBundle.message("settings.fontSize.small"),
                LinuxDoBundle.message("settings.fontSize.medium"),
                LinuxDoBundle.message("settings.fontSize.large")
        });
        fontSizePanel.add(fontSizeComboBox);
        fontSizePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fontSizePanel);
        panel.add(Box.createVerticalStrut(5));
        
        // 伪装模式
        JPanel disguisePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        disguisePanel.add(new JLabel(LinuxDoBundle.message("settings.disguiseMode")));
        disguiseModeComboBox = new JComboBox<>(new String[]{
                LinuxDoBundle.message("settings.disguiseMode.off"),
                LinuxDoBundle.message("settings.disguiseMode.hide"),
                LinuxDoBundle.message("settings.disguiseMode.english")
        });
        disguiseModeComboBox.setToolTipText(LinuxDoBundle.message("settings.disguiseMode.tooltip"));
        disguisePanel.add(disguiseModeComboBox);
        disguisePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(disguisePanel);
        panel.add(Box.createVerticalStrut(5));

        // 单次加载话题数
        JPanel topicsPerLoadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topicsPerLoadPanel.add(new JLabel(LinuxDoBundle.message("settings.topicsPerLoad")));
        topicsPerLoadSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 30, 5));
        topicsPerLoadPanel.add(topicsPerLoadSpinner);
        topicsPerLoadPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(topicsPerLoadPanel);
        panel.add(Box.createVerticalStrut(5));

        // 单次加载回复数
        JPanel repliesPerLoadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        repliesPerLoadPanel.add(new JLabel(LinuxDoBundle.message("settings.repliesPerLoad")));
        repliesPerLoadSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 20, 5));
        repliesPerLoadPanel.add(repliesPerLoadSpinner);
        repliesPerLoadPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(repliesPerLoadPanel);
        panel.add(Box.createVerticalStrut(5));

        // 通知显示模式
        JPanel notificationModePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        notificationModePanel.add(new JLabel(LinuxDoBundle.message("settings.notificationMode")));
        notificationModeComboBox = new JComboBox<>(new String[]{
                LinuxDoBundle.message("settings.notificationMode.off"),
                LinuxDoBundle.message("settings.notificationMode.unread"),
                LinuxDoBundle.message("settings.notificationMode.all")
        });
        notificationModePanel.add(notificationModeComboBox);
        notificationModePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(notificationModePanel);
        panel.add(Box.createVerticalStrut(5));

        // 预览模式
        JPanel previewModePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        previewModePanel.add(new JLabel(LinuxDoBundle.message("settings.previewMode")));
        previewModeComboBox = new JComboBox<>(new String[]{
                LinuxDoBundle.message("settings.previewMode.panel"),
                LinuxDoBundle.message("settings.previewMode.tooltip")
        });
        previewModeComboBox.setToolTipText(LinuxDoBundle.message("settings.previewMode.tooltip.hint"));
        previewModePanel.add(previewModeComboBox);
        previewModePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewModePanel);

        return panel;
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                LinuxDoBundle.message("settings.loginInfo"),
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 浏览器登录按钮
        JPanel loginButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton browserLoginButton = new JButton(LinuxDoBundle.message("settings.browserLogin"));
        browserLoginButton.setToolTipText(LinuxDoBundle.message("settings.browserLogin.tooltip"));
        browserLoginButton.addActionListener(e -> {
            com.linuxdo.explorer.ui.LoginBrowserDialog.showLoginDialog((cookie, userAgent) -> {
                // 登录成功后更新文本框
                if (cookie != null && !cookie.isEmpty()) {
                    cookieTextArea.setText(cookie);
                }
                if (userAgent != null && !userAgent.isEmpty()) {
                    userAgentTextArea.setText(userAgent);
                }
            });
        });
        loginButtonPanel.add(browserLoginButton);
        loginButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(loginButtonPanel);
        panel.add(Box.createVerticalStrut(10));

        // Cookie 输入
        JLabel cookieLabel = new JLabel(LinuxDoBundle.message("settings.cookie"));
        cookieLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(cookieLabel);
        panel.add(Box.createVerticalStrut(5));

        cookieTextArea = new JTextArea(4, 50);
        cookieTextArea.setLineWrap(true);
        cookieTextArea.setWrapStyleWord(true);
        JScrollPane cookieScrollPane = new JScrollPane(cookieTextArea);
        cookieScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(cookieScrollPane);
        panel.add(Box.createVerticalStrut(10));

        // User-Agent 输入
        JLabel uaLabel = new JLabel(LinuxDoBundle.message("settings.userAgent"));
        uaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(uaLabel);
        panel.add(Box.createVerticalStrut(5));

        userAgentTextArea = new JTextArea(2, 50);
        userAgentTextArea.setLineWrap(true);
        userAgentTextArea.setWrapStyleWord(true);
        JScrollPane uaScrollPane = new JScrollPane(userAgentTextArea);
        uaScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(uaScrollPane);
        panel.add(Box.createVerticalStrut(10));

        // 提示信息
        JLabel tipLabel = new JLabel("<html><font color='gray'>" + LinuxDoBundle.message("settings.loginTip") + "</font></html>");
        tipLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(tipLabel);

        return panel;
    }

    private JPanel createOpenAIPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                LinuxDoBundle.message("settings.aiConfig"),
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // API URL
        JPanel urlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        urlPanel.add(new JLabel(LinuxDoBundle.message("settings.apiUrl")));
        openaiUrlField = new JTextField(40);
        openaiUrlField.setToolTipText(LinuxDoBundle.message("settings.apiUrl.tooltip"));
        urlPanel.add(openaiUrlField);
        urlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(urlPanel);
        panel.add(Box.createVerticalStrut(5));

        // API Key
        JPanel keyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        keyPanel.add(new JLabel(LinuxDoBundle.message("settings.apiKey")));
        openaiKeyField = new JPasswordField(40);
        openaiKeyField.setToolTipText(LinuxDoBundle.message("settings.apiKey.tooltip"));
        keyPanel.add(openaiKeyField);
        keyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(keyPanel);
        panel.add(Box.createVerticalStrut(5));

        // Model
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modelPanel.add(new JLabel(LinuxDoBundle.message("settings.model")));
        openaiModelField = new JTextField(20);
        openaiModelField.setToolTipText(LinuxDoBundle.message("settings.model.tooltip"));
        modelPanel.add(openaiModelField);
        modelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(modelPanel);
        panel.add(Box.createVerticalStrut(5));

        // 提示信息
        JLabel tipLabel = new JLabel("<html><font color='gray'>" + LinuxDoBundle.message("settings.aiTip") + "</font></html>");
        tipLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(tipLabel);

        return panel;
    }

    private void loadSettings() {
        LinuxDoSettings settings = LinuxDoSettings.getInstance();

        cookieTextArea.setText(settings.getCookie());
        userAgentTextArea.setText(settings.getUserAgent());
        showImagesCheckBox.setSelected(settings.isShowImages());
        showEmojiCheckBox.setSelected(settings.isShowEmoji());
        ignoreLineBreaksCheckBox.setSelected(settings.isIgnoreLineBreaks());
        compactModeCheckBox.setSelected(settings.isCompactMode());
        showPostInfoCheckBox.setSelected(settings.isShowPostInfo());
        
        // 设置图片滤镜模式
        String filterMode = settings.getImageFilterMode();
        if ("grayscale".equals(filterMode)) imageFilterModeComboBox.setSelectedIndex(1);
        else if ("halftone".equals(filterMode)) imageFilterModeComboBox.setSelectedIndex(2);
        else imageFilterModeComboBox.setSelectedIndex(0); // normal

        // 设置自动刷新下拉框
        int minutes = settings.getAutoRefreshMinutes();
        int index = 0;
        if (minutes == 5) index = 1;
        else if (minutes == 10) index = 2;
        else if (minutes == 20) index = 3;
        else if (minutes == 60) index = 4;
        autoRefreshComboBox.setSelectedIndex(index);

        // 设置字号下拉框
        String fontSize = settings.getTopicFontSize();
        if ("small".equals(fontSize)) fontSizeComboBox.setSelectedIndex(0);
        else if ("large".equals(fontSize)) fontSizeComboBox.setSelectedIndex(2);
        else fontSizeComboBox.setSelectedIndex(1);

        // 设置 OpenAI 配置
        openaiUrlField.setText(settings.getOpenaiUrl());
        openaiKeyField.setText(settings.getOpenaiKey());
        openaiModelField.setText(settings.getOpenaiModel());
        
        // 设置伪装模式下拉框
        String disguiseMode = settings.getDisguiseMode();
        if ("off".equals(disguiseMode)) disguiseModeComboBox.setSelectedIndex(0);
        else if ("hide".equals(disguiseMode)) disguiseModeComboBox.setSelectedIndex(1);
        else disguiseModeComboBox.setSelectedIndex(2); // english (默认)
        
        // 设置加载数量
        topicsPerLoadSpinner.setValue(settings.getTopicsPerLoad());
        repliesPerLoadSpinner.setValue(settings.getRepliesPerLoad());
        
        // 设置通知显示模式
        String notificationMode = settings.getNotificationMode();
        if ("off".equals(notificationMode)) notificationModeComboBox.setSelectedIndex(0);
        else if ("unread".equals(notificationMode)) notificationModeComboBox.setSelectedIndex(1);
        else notificationModeComboBox.setSelectedIndex(2); // all
        
        // 设置预览模式
        String previewMode = settings.getPreviewMode();
        if ("tooltip".equals(previewMode)) previewModeComboBox.setSelectedIndex(1);
        else previewModeComboBox.setSelectedIndex(0); // panel (默认)
    }

    @Override
    public boolean isModified() {
        LinuxDoSettings settings = LinuxDoSettings.getInstance();

        return !cookieTextArea.getText().equals(settings.getCookie()) ||
                !userAgentTextArea.getText().equals(settings.getUserAgent()) ||
                showImagesCheckBox.isSelected() != settings.isShowImages() ||
                showEmojiCheckBox.isSelected() != settings.isShowEmoji() ||
                ignoreLineBreaksCheckBox.isSelected() != settings.isIgnoreLineBreaks() ||
                compactModeCheckBox.isSelected() != settings.isCompactMode() ||
                !getImageFilterMode().equals(settings.getImageFilterMode()) ||
                showPostInfoCheckBox.isSelected() != settings.isShowPostInfo() ||
                getAutoRefreshMinutes() != settings.getAutoRefreshMinutes() ||
                !getTopicFontSize().equals(settings.getTopicFontSize()) ||
                !openaiUrlField.getText().equals(settings.getOpenaiUrl()) ||
                !new String(openaiKeyField.getPassword()).equals(settings.getOpenaiKey()) ||
                !openaiModelField.getText().equals(settings.getOpenaiModel()) ||
                !getDisguiseMode().equals(settings.getDisguiseMode()) ||
                (Integer) topicsPerLoadSpinner.getValue() != settings.getTopicsPerLoad() ||
                (Integer) repliesPerLoadSpinner.getValue() != settings.getRepliesPerLoad() ||
                !getNotificationMode().equals(settings.getNotificationMode()) ||
                !getPreviewMode().equals(settings.getPreviewMode());
    }

    @Override
    public void apply() throws ConfigurationException {
        LinuxDoSettings settings = LinuxDoSettings.getInstance();

        settings.setCookie(cookieTextArea.getText().trim());
        settings.setUserAgent(userAgentTextArea.getText().trim());
        settings.setShowImages(showImagesCheckBox.isSelected());
        settings.setShowEmoji(showEmojiCheckBox.isSelected());
        settings.setIgnoreLineBreaks(ignoreLineBreaksCheckBox.isSelected());
        settings.setCompactMode(compactModeCheckBox.isSelected());
        settings.setImageFilterMode(getImageFilterMode());
        settings.setShowPostInfo(showPostInfoCheckBox.isSelected());
        settings.setAutoRefreshMinutes(getAutoRefreshMinutes());
        settings.setTopicFontSize(getTopicFontSize());
        settings.setOpenaiUrl(openaiUrlField.getText().trim());
        settings.setOpenaiKey(new String(openaiKeyField.getPassword()).trim());
        settings.setOpenaiModel(openaiModelField.getText().trim());
        settings.setDisguiseMode(getDisguiseMode());
        settings.setTopicsPerLoad((Integer) topicsPerLoadSpinner.getValue());
        settings.setRepliesPerLoad((Integer) repliesPerLoadSpinner.getValue());
        settings.setNotificationMode(getNotificationMode());
        settings.setPreviewMode(getPreviewMode());
        
        // 发布设置变更消息，触发工具窗口刷新
        com.intellij.openapi.application.ApplicationManager.getApplication()
                .getMessageBus()
                .syncPublisher(SettingsChangeNotifier.TOPIC)
                .settingsChanged();
    }

    @Override
    public void reset() {
        loadSettings();
    }

    private int getAutoRefreshMinutes() {
        int index = autoRefreshComboBox.getSelectedIndex();
        switch (index) {
            case 1: return 5;
            case 2: return 10;
            case 3: return 20;
            case 4: return 60;
            default: return 0;
        }
    }

    private String getTopicFontSize() {
        int index = fontSizeComboBox.getSelectedIndex();
        switch (index) {
            case 0: return "small";
            case 2: return "large";
            default: return "medium";
        }
    }
    
    private String getDisguiseMode() {
        int index = disguiseModeComboBox.getSelectedIndex();
        switch (index) {
            case 0: return "off";
            case 1: return "hide";
            default: return "english";
        }
    }
    
    private String getNotificationMode() {
        int index = notificationModeComboBox.getSelectedIndex();
        switch (index) {
            case 0: return "off";
            case 1: return "unread";
            default: return "all";
        }
    }
    
    private String getPreviewMode() {
        int index = previewModeComboBox.getSelectedIndex();
        return index == 1 ? "tooltip" : "panel";
    }
    
    private String getImageFilterMode() {
        int index = imageFilterModeComboBox.getSelectedIndex();
        switch (index) {
            case 1: return "grayscale";
            case 2: return "halftone";
            default: return "normal";
        }
    }
}
