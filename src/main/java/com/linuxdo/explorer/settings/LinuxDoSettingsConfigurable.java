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
    private JCheckBox grayscaleImagesCheckBox;
    private JCheckBox showPostInfoCheckBox;
    private JComboBox<String> autoRefreshComboBox;
    private JComboBox<String> fontSizeComboBox;
    private JComboBox<String> disguiseModeComboBox;
    // OpenAI 配置
    private JTextField openaiUrlField;
    private JPasswordField openaiKeyField;
    private JTextField openaiModelField;

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
        grayscaleImagesCheckBox = new JCheckBox(LinuxDoBundle.message("settings.grayscaleImages"));
        showPostInfoCheckBox = new JCheckBox(LinuxDoBundle.message("settings.showPostInfo"));

        panel.add(showImagesCheckBox);
        panel.add(showEmojiCheckBox);
        panel.add(ignoreLineBreaksCheckBox);
        panel.add(compactModeCheckBox);
        panel.add(grayscaleImagesCheckBox);
        panel.add(showPostInfoCheckBox);
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
        grayscaleImagesCheckBox.setSelected(settings.isGrayscaleImages());
        showPostInfoCheckBox.setSelected(settings.isShowPostInfo());

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
                grayscaleImagesCheckBox.isSelected() != settings.isGrayscaleImages() ||
                showPostInfoCheckBox.isSelected() != settings.isShowPostInfo() ||
                getAutoRefreshMinutes() != settings.getAutoRefreshMinutes() ||
                !getTopicFontSize().equals(settings.getTopicFontSize()) ||
                !openaiUrlField.getText().equals(settings.getOpenaiUrl()) ||
                !new String(openaiKeyField.getPassword()).equals(settings.getOpenaiKey()) ||
                !openaiModelField.getText().equals(settings.getOpenaiModel()) ||
                !getDisguiseMode().equals(settings.getDisguiseMode());
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
        settings.setGrayscaleImages(grayscaleImagesCheckBox.isSelected());
        settings.setShowPostInfo(showPostInfoCheckBox.isSelected());
        settings.setAutoRefreshMinutes(getAutoRefreshMinutes());
        settings.setTopicFontSize(getTopicFontSize());
        settings.setOpenaiUrl(openaiUrlField.getText().trim());
        settings.setOpenaiKey(new String(openaiKeyField.getPassword()).trim());
        settings.setOpenaiModel(openaiModelField.getText().trim());
        settings.setDisguiseMode(getDisguiseMode());
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
}
