package com.linuxdo.explorer.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 持久化设置存储
 */
@State(
        name = "LinuxDoExplorerSettings",
        storages = @Storage("LinuxDoExplorerSettings.xml")
)
public class LinuxDoSettings implements PersistentStateComponent<LinuxDoSettings.State> {
    
    private static final String CREDENTIAL_SERVICE_NAME = "LinuxDoExplorer";
    private static final String COOKIE_KEY = "cookie";
    private static final String USER_AGENT_KEY = "userAgent";
    
    private State state = new State();

    public static LinuxDoSettings getInstance() {
        return ApplicationManager.getApplication().getService(LinuxDoSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    // ============ Cookie 管理（安全存储） ============
    
    private CredentialAttributes createCredentialAttributes(String key) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(CREDENTIAL_SERVICE_NAME, key)
        );
    }

    public String getCookie() {
        CredentialAttributes attributes = createCredentialAttributes(COOKIE_KEY);
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : "";
    }

    public void setCookie(String cookie) {
        CredentialAttributes attributes = createCredentialAttributes(COOKIE_KEY);
        Credentials credentials = new Credentials(COOKIE_KEY, cookie);
        PasswordSafe.getInstance().set(attributes, credentials);
    }

    public String getUserAgent() {
        CredentialAttributes attributes = createCredentialAttributes(USER_AGENT_KEY);
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : "";
    }

    public void setUserAgent(String userAgent) {
        CredentialAttributes attributes = createCredentialAttributes(USER_AGENT_KEY);
        Credentials credentials = new Credentials(USER_AGENT_KEY, userAgent);
        PasswordSafe.getInstance().set(attributes, credentials);
    }

    public boolean hasCookie() {
        String cookie = getCookie();
        return cookie != null && !cookie.isEmpty();
    }

    public void clearCredentials() {
        CredentialAttributes cookieAttr = createCredentialAttributes(COOKIE_KEY);
        CredentialAttributes uaAttr = createCredentialAttributes(USER_AGENT_KEY);
        PasswordSafe.getInstance().set(cookieAttr, null);
        PasswordSafe.getInstance().set(uaAttr, null);
    }

    // ============ 显示设置 ============

    public boolean isShowImages() {
        return state.showImages;
    }

    public void setShowImages(boolean showImages) {
        state.showImages = showImages;
    }

    public boolean isShowEmoji() {
        return state.showEmoji;
    }

    public void setShowEmoji(boolean showEmoji) {
        state.showEmoji = showEmoji;
    }

    public int getAutoRefreshMinutes() {
        return state.autoRefreshMinutes;
    }

    public void setAutoRefreshMinutes(int minutes) {
        state.autoRefreshMinutes = minutes;
    }

    public boolean isIgnoreLineBreaks() {
        return state.ignoreLineBreaks;
    }

    public void setIgnoreLineBreaks(boolean ignoreLineBreaks) {
        state.ignoreLineBreaks = ignoreLineBreaks;
    }

    public boolean isCompactMode() {
        return state.compactMode;
    }

    public void setCompactMode(boolean compactMode) {
        state.compactMode = compactMode;
    }

    public String getTopicFontSize() {
        return state.topicFontSize;
    }

    public void setTopicFontSize(String topicFontSize) {
        state.topicFontSize = topicFontSize;
    }

    /**
     * 图片滤镜模式：normal=正常, grayscale=黑白, halftone=点阵
     */
    public String getImageFilterMode() {
        // 向后兼容：如果旧的grayscaleImages为true，返回grayscale
        if (state.imageFilterMode == null || state.imageFilterMode.isEmpty()) {
            return state.grayscaleImages ? "grayscale" : "normal";
        }
        return state.imageFilterMode;
    }

    public void setImageFilterMode(String mode) {
        state.imageFilterMode = mode;
        // 同时更新旧字段以保持兼容
        state.grayscaleImages = "grayscale".equals(mode);
    }

    // 保留旧方法以保持兼容
    public boolean isGrayscaleImages() {
        return "grayscale".equals(getImageFilterMode());
    }

    public void setGrayscaleImages(boolean grayscaleImages) {
        setImageFilterMode(grayscaleImages ? "grayscale" : "normal");
    }

    public boolean isShowPostInfo() {
        return state.showPostInfo;
    }

    public void setShowPostInfo(boolean showPostInfo) {
        state.showPostInfo = showPostInfo;
    }

    // ============ OpenAI 设置 ============

    public String getOpenaiUrl() {
        return state.openaiUrl;
    }

    public void setOpenaiUrl(String url) {
        state.openaiUrl = url;
    }

    public String getOpenaiKey() {
        // API Key 使用安全存储
        CredentialAttributes attributes = createCredentialAttributes("openaiKey");
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : "";
    }

    public void setOpenaiKey(String key) {
        CredentialAttributes attributes = createCredentialAttributes("openaiKey");
        Credentials credentials = new Credentials("openaiKey", key);
        PasswordSafe.getInstance().set(attributes, credentials);
    }

    public String getOpenaiModel() {
        return state.openaiModel;
    }

    public void setOpenaiModel(String model) {
        state.openaiModel = model;
    }

    public boolean hasOpenaiConfig() {
        String url = getOpenaiUrl();
        String key = getOpenaiKey();
        return url != null && !url.isEmpty() && key != null && !key.isEmpty();
    }

    // ============ 伪装模式设置 ============
    
    /**
     * 伪装模式：off=关闭, hide=隐藏内容, english=英文填充（默认）
     */
    public String getDisguiseMode() {
        return state.disguiseMode;
    }

    public void setDisguiseMode(String mode) {
        state.disguiseMode = mode;
    }

    // ============ 加载数量设置 ============

    public int getTopicsPerLoad() {
        return state.topicsPerLoad;
    }

    public void setTopicsPerLoad(int count) {
        state.topicsPerLoad = Math.max(5, Math.min(30, count));
    }

    public int getRepliesPerLoad() {
        return state.repliesPerLoad;
    }

    public void setRepliesPerLoad(int count) {
        state.repliesPerLoad = Math.max(5, Math.min(20, count));
    }

    // ============ 通知设置 ============

    public String getNotificationMode() {
        return state.notificationMode;
    }

    public void setNotificationMode(String mode) {
        state.notificationMode = mode;
    }

    // ============ 预览模式设置 ============

    /**
     * 预览模式：panel=底部面板, tooltip=悬浮提示
     */
    public String getPreviewMode() {
        return state.previewMode;
    }

    public void setPreviewMode(String mode) {
        state.previewMode = mode;
    }

    /**
     * 设置状态类
     */
    public static class State {
        public boolean showImages = true;
        public boolean showEmoji = false;
        public int autoRefreshMinutes = 0;
        public boolean ignoreLineBreaks = false;
        public boolean compactMode = false;
        public String topicFontSize = "medium";
        public boolean grayscaleImages = false;  // 保留用于向后兼容
        public String imageFilterMode = "normal";  // normal=正常, grayscale=黑白, halftone=点阵
        public boolean showPostInfo = true;
        // OpenAI 配置
        public String openaiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
        public String openaiModel = "glm-4.6v-flash";
        // 伪装模式：off=关闭, hide=隐藏内容, english=英文填充
        public String disguiseMode = "english";
        // 加载数量设置
        public int topicsPerLoad = 20;
        public int repliesPerLoad = 20;
        // 通知显示模式：off=关闭, unread=仅未读, all=全部
        public String notificationMode = "unread";
        // 预览模式：panel=底部面板, tooltip=悬浮提示
        public String previewMode = "panel";
    }
}
