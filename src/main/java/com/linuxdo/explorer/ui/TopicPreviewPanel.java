package com.linuxdo.explorer.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.jcef.JBCefBrowser;
import com.linuxdo.explorer.model.Post;
import com.linuxdo.explorer.settings.LinuxDoSettings;
import com.linuxdo.explorer.util.LinuxDoBundle;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 话题预览面板 - 使用 JCEF 浏览器渲染 HTML
 */
public class TopicPreviewPanel extends JPanel {

    private static final String BASE_URL = "https://linux.do";
    private static TopicPreviewPanel currentInstance;
    private static JDialog currentDialog;  // 保存当前打开的对话框，用于关闭

    private final JBCefBrowser browser;
    
    // 保存功能所需的数据
    private Project project;
    private String title;
    private List<Post> posts;
    private int topicId;

    public TopicPreviewPanel() {
        setLayout(new BorderLayout());
        browser = new JBCefBrowser();
        add(browser.getComponent(), BorderLayout.CENTER);
    }

    public void showTopic(String title, List<Post> posts) {
        this.title = title;
        this.posts = posts;
        String html = buildHtml(title, posts);
        browser.loadHTML(html);
    }
    
    public void setProjectAndTopicId(Project project, int topicId) {
        this.project = project;
        this.topicId = topicId;
    }

    private String buildHtml(String title, List<Post> posts) {
        LinuxDoSettings settings = LinuxDoSettings.getInstance();

        StringBuilder postSections = new StringBuilder();
        for (Post post : posts) {
            String cooked = post.getCooked();

            // 根据设置处理内容
            if (!settings.isShowEmoji()) {
                cooked = removeEmojiSyntax(cooked);
            }
            if (!settings.isShowImages()) {
                cooked = cooked.replaceAll("<img[^>]*>", "");
            } else {
                cooked = fixImageUrls(cooked);
            }
            if (settings.isIgnoreLineBreaks()) {
                cooked = cooked.replaceAll("(\\r\\n|\\n|\\r)", "");
                cooked = cooked.replaceAll("<br\\s*/?>", "");
            }

            String postHeader = "";
            if (settings.isShowPostInfo()) {
                postHeader = "<header class=\"post-header\">" +
                        "<span class=\"post-number\">#" + post.getPostNumber() + "</span>" +
                        "<span class=\"post-username\">@" + escapeHtml(post.getUsername()) + "</span>" +
                        "<span class=\"post-date\">" + escapeHtml(formatDate(post.getCreatedAt())) + "</span>" +
                        "</header>";
            }

            postSections.append("<article class=\"post\">")
                    .append(postHeader)
                    .append("<section class=\"post-body\">")
                    .append(cooked)
                    .append("</section>")
                    .append("</article>");
        }

        String bodyFontSize = switch (settings.getTopicFontSize()) {
            case "small" -> "12px";
            case "large" -> "16px";
            default -> "14px";
        };

        String compactClass = settings.isCompactMode() ? "compact" : "";
        String grayscaleClass = settings.isGrayscaleImages() ? "grayscale" : "";
        
        // 检测 IDE 是否为暗色主题（JBColor.isBright() 返回 true 表示亮色主题）
        boolean isDarkTheme = !JBColor.isBright();
        String themeClass = isDarkTheme ? "dark" : "light";
        String bodyClasses = (themeClass + " " + compactClass + " " + grayscaleClass).trim();

        String css = """
                <style>
                    /* 暗色主题（默认） */
                    body {
                        margin: 0;
                        padding: 16px 24px 40px;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        font-size: FONT_SIZE_PLACEHOLDER;
                        line-height: 1.6;
                        color: #ddd;
                        background-color: #2b2b2b;
                    }
                    /* 亮色主题 */
                    body.light {
                        color: #333;
                        background-color: #fff;
                    }
                    body.light a {
                        color: #2470B3;
                    }
                    body.light pre {
                        background-color: #f5f5f5;
                    }
                    body.light .post {
                        border-top-color: #ddd;
                    }
                    body.light .topic-subtitle,
                    body.light .post-header {
                        color: #666;
                    }
                    body.light .post-number {
                        color: #444;
                    }
                    a {
                        color: #589df6;
                    }
                    a:hover {
                        text-decoration: underline;
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                        display: block;
                        margin: 8px 0;
                    }
                    pre {
                        background-color: #3c3f41;
                        padding: 8px 12px;
                        border-radius: 4px;
                        overflow-x: auto;
                        font-family: Consolas, "Courier New", monospace;
                    }
                    code {
                        font-family: Consolas, "Courier New", monospace;
                    }
                    .container {
                        max-width: 960px;
                        margin: 0 auto;
                    }
                    .topic-title {
                        font-size: 1.4rem;
                        font-weight: 600;
                        margin: 0 0 12px;
                    }
                    .topic-subtitle {
                        margin: 0 0 24px;
                        color: #999;
                        font-size: 0.9rem;
                    }
                    .post {
                        border-top: 1px solid #444;
                        padding-top: 16px;
                        margin-top: 16px;
                    }
                    .post:first-of-type {
                        border-top: none;
                        padding-top: 0;
                        margin-top: 0;
                    }
                    .post-header {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 8px;
                        align-items: baseline;
                        margin-bottom: 8px;
                        color: #aaa;
                        font-size: 0.85rem;
                    }
                    .post-number {
                        font-weight: 600;
                        color: #c5c5c5;
                    }
                    .post-username {
                        font-weight: 500;
                    }
                    .post-date {
                        opacity: 0.8;
                    }
                    .post-body {
                        word-wrap: break-word;
                        overflow-wrap: break-word;
                    }
                    body.compact {
                        line-height: 1.4;
                    }
                    body.compact .post {
                        padding-top: 8px;
                        margin-top: 8px;
                    }
                    body.compact .post-body p {
                        margin-top: 4px;
                        margin-bottom: 4px;
                    }
                    body.grayscale img {
                        filter: grayscale(1);
                        transition: all 0.5s ease-in-out;
                    }
                    body.grayscale img:hover {
                        filter: grayscale(0);
                        transform: scale(1.01);
                    }
                    /* 隐藏 .meta 元素 */
                    .meta {
                        display: none !important;
                    }
                </style>
                """.replace("FONT_SIZE_PLACEHOLDER", bodyFontSize);

        return "<!DOCTYPE html>\n" +
               "<html lang=\"zh-CN\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>" + escapeHtml(title) + " - Linux.do</title>\n" +
               css +
               "</head>\n" +
               "<body class=\"" + bodyClasses + "\">\n" +
               "    <div class=\"container\">\n" +
               "        <h1 class=\"topic-title\">" + escapeHtml(title) + "</h1>\n" +
               "        <p class=\"topic-subtitle\">" + LinuxDoBundle.message("ui.postsCount", posts.size()) + "</p>\n" +
               postSections.toString() +
               "    </div>\n" +
               "</body>\n" +
               "</html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String removeEmojiSyntax(String input) {
        String result = input;
        result = result.replaceAll("<img[^>]*class=\"[^\"]*emoji[^\"]*\"[^>]*>", "");
        result = result.replaceAll(":[a-zA-Z0-9_+\\-]+:", "");
        return result;
    }

    private String fixImageUrls(String html) {
        String result = html;
        // src="/path" -> src="https://linux.do/path"
        result = result.replaceAll("src=\"/(?!/)", "src=\"" + BASE_URL + "/");
        result = result.replaceAll("src='/(?!/)", "src='" + BASE_URL + "/");
        // src="//host/path" -> src="https://host/path"
        result = result.replaceAll("src=\"//", "src=\"https://");
        result = result.replaceAll("src='//", "src='https://");
        return result;
    }

    private String formatDate(String dateString) {
        if (dateString == null) return "";
        try {
            // 简单格式化，实际可用更复杂的日期处理
            return dateString.replace("T", " ").substring(0, 19);
        } catch (Exception e) {
            return dateString;
        }
    }
    
    /**
     * 保存为 Markdown 文件并在 IDE 中打开
     */
    public void saveAsMarkdown() {
        if (project == null || title == null || posts == null) {
            showError(LinuxDoBundle.message("message.dataNotLoaded"));
            return;
        }
        
        try {
            // 获取项目根目录
            String basePath = project.getBasePath();
            if (basePath == null) {
                showError(LinuxDoBundle.message("ui.cannotGetProjectPath"));
                return;
            }
            
            // 创建 .linuxdoexp/content 目录
            File dir = new File(basePath, ".linuxdoexp/content");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // 清理文件名中的非法字符
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safeTitle.length() > 100) {
                safeTitle = safeTitle.substring(0, 100);
            }
            
            // 生成 Markdown 内容
            String markdownContent = generateMarkdown();
            
            // 创建 Markdown 文件
            File mdFile = new File(dir, safeTitle + ".md");
            try (FileWriter writer = new FileWriter(mdFile, StandardCharsets.UTF_8)) {
                writer.write(markdownContent);
            }
            
            // 刷新 VFS 并打开文件
            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mdFile);
            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true);
                showInfo(LinuxDoBundle.message("message.contentSaved") + mdFile.getPath());
            } else {
                showError(LinuxDoBundle.message("message.saveFailed") + mdFile.getPath());
            }
        } catch (IOException e) {
            showError(LinuxDoBundle.message("message.saveFailed") + e.getMessage());
        }
    }
    
    private String generateMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("> ").append(LinuxDoBundle.message("ui.topicLink"))
          .append("https://linux.do/t/topic/").append(topicId).append("\n\n");
        sb.append("---\n\n");
        
        for (Post post : posts) {
            // 不使用 # 号，改用粗体和分隔符
            sb.append("**#").append(post.getPostNumber())
              .append(" @").append(post.getUsername());
            if (post.getCreatedAt() != null) {
                sb.append(" · ").append(formatDate(post.getCreatedAt()));
            }
            sb.append("**\n\n");
            
            // 将 HTML 内容转换为更适合阅读的格式
            String content = post.getCooked();
            // 简单清理 HTML 标签，保留基本格式
            content = content.replaceAll("<br\\s*/?>", "\n");
            content = content.replaceAll("<p>", "");
            content = content.replaceAll("</p>", "\n\n");
            content = content.replaceAll("</?strong>", "**");
            content = content.replaceAll("</?em>", "*");
            content = content.replaceAll("</?code>", "`");
            content = content.replaceAll("<pre[^>]*>", "\n```\n");
            content = content.replaceAll("</pre>", "\n```\n");
            content = content.replaceAll("<a[^>]*href=\"([^\"]*)\"[^>]*>([^<]*)</a>", "[$2]($1)");
            content = content.replaceAll("<img[^>]*src=\"([^\"]*)\"[^>]*>", "![]($1)");
            content = content.replaceAll("<[^>]+>", ""); // 移除其他标签
            content = content.replaceAll("&nbsp;", " ");
            content = content.replaceAll("&lt;", "<");
            content = content.replaceAll("&gt;", ">");
            content = content.replaceAll("&amp;", "&");
            content = content.replaceAll("&quot;", "\"");
            
            sb.append(content.trim()).append("\n\n");
            sb.append("---\n\n");
        }
        
        return sb.toString();
    }
    
    private void showError(String message) {
        Notifications.Bus.notify(new Notification(
                "Linux.do Explorer",
                LinuxDoBundle.message("message.error"),
                message,
                NotificationType.ERROR
        ));
    }
    
    private void showInfo(String message) {
        Notifications.Bus.notify(new Notification(
                "Linux.do Explorer",
                LinuxDoBundle.message("message.info"),
                message,
                NotificationType.INFORMATION
        ));
    }
    
    /**
     * AI 总结并保存
     */
    public void summarizeAndSave() {
        summarizeAndSave(null);
    }
    
    /**
     * AI 总结并保存（带按钮引用）
     */
    public void summarizeAndSave(JButton button) {
        if (project == null || title == null || posts == null) {
            showError(LinuxDoBundle.message("message.dataNotLoaded"));
            return;
        }
        
        LinuxDoSettings settings = LinuxDoSettings.getInstance();
        if (!settings.hasOpenaiConfig()) {
            showError(LinuxDoBundle.message("message.configureAI"));
            return;
        }
        
        // 禁用按钮并显示加载状态
        String originalText = null;
        if (button != null) {
            originalText = button.getText();
            button.setText(LinuxDoBundle.message("message.summarizing"));
            button.setEnabled(false);
        }
        final String origText = originalText;
        
        showInfo(LinuxDoBundle.message("message.summaryGenerating"));
        
        new Thread(() -> {
            try {
                String markdownContent = generateMarkdown();
                String summary = callOpenAI(markdownContent);
                
                if (summary != null && !summary.isEmpty()) {
                    saveSummary(summary);
                } else {
                    SwingUtilities.invokeLater(() -> showError(LinuxDoBundle.message("message.summaryEmpty")));
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> showError(LinuxDoBundle.message("message.summaryFailed") + e.getMessage()));
            } finally {
                // 恢复按钮状态
                if (button != null) {
                    SwingUtilities.invokeLater(() -> {
                        button.setText(origText != null ? origText : LinuxDoBundle.message("button.summarize"));
                        button.setEnabled(true);
                    });
                }
            }
        }).start();
    }
    
    private String callOpenAI(String content) throws IOException {
        LinuxDoSettings settings = LinuxDoSettings.getInstance();
        String apiUrl = settings.getOpenaiUrl();
        String apiKey = settings.getOpenaiKey();
        String model = settings.getOpenaiModel();
        
        if (model == null || model.isEmpty()) {
            model = "glm-4.6v-flash";
        }
        
        // 构建提示词
        String prompt = LinuxDoBundle.message("ai.prompt.summary") + "\n\n" + content;
        
        // 构建请求 JSON
        String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"user\", \"content\": %s}]}",
                model,
                escapeJsonString(prompt)
        );
        
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(120000);
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                String error = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException(LinuxDoBundle.message("ai.apiErrorWithBody", responseCode, error));
            }
            throw new IOException(LinuxDoBundle.message("ai.apiErrorCode", responseCode));
        }
        
        try (InputStream is = conn.getInputStream()) {
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return extractContentFromResponse(response);
        }
    }
    
    private String escapeJsonString(String str) {
        if (str == null) return "\"\"";
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
    
    private String extractContentFromResponse(String response) {
        // 简单解析 JSON 响应，提取 content 字段
        // 响应格式: {"choices":[{"message":{"content":"..."}}]}
        int contentStart = response.indexOf("\"content\":");
        if (contentStart == -1) return null;
        
        contentStart = response.indexOf('"', contentStart + 10);
        if (contentStart == -1) return null;
        contentStart++;
        
        StringBuilder content = new StringBuilder();
        boolean escape = false;
        for (int i = contentStart; i < response.length(); i++) {
            char c = response.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n': content.append('\n'); break;
                    case 'r': content.append('\r'); break;
                    case 't': content.append('\t'); break;
                    case '"': content.append('"'); break;
                    case '\\': content.append('\\'); break;
                    default: content.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                content.append(c);
            }
        }
        return content.toString();
    }
    
    private void saveSummary(String summary) {
        try {
            String basePath = project.getBasePath();
            if (basePath == null) {
                SwingUtilities.invokeLater(() -> showError(LinuxDoBundle.message("ui.cannotGetProjectPath")));
                return;
            }
            
            // 创建 .linuxdoexp/summary 目录
            File dir = new File(basePath, ".linuxdoexp/summary");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safeTitle.length() > 100) {
                safeTitle = safeTitle.substring(0, 100);
            }
            
            // 添加标题和原始链接
            String fullContent = "# " + LinuxDoBundle.message("ui.aiSummaryFileTitle", title) + "\n\n" +
                    "> " + LinuxDoBundle.message("ui.originalTopic") + "https://linux.do/t/topic/" + topicId + "\n\n" +
                    "---\n\n" + summary;
            
            File mdFile = new File(dir, safeTitle + ".md");
            try (FileWriter writer = new FileWriter(mdFile, StandardCharsets.UTF_8)) {
                writer.write(fullContent);
            }
            
            SwingUtilities.invokeLater(() -> {
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mdFile);
                if (virtualFile != null) {
                    FileEditorManager.getInstance(project).openFile(virtualFile, true);
                    showInfo(LinuxDoBundle.message("message.summarySavedTo") + mdFile.getPath());
                }
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> showError(LinuxDoBundle.message("message.saveSummaryFailed") + e.getMessage()));
        }
    }

    /**
     * 显示话题预览（兼容旧接口）
     */
    public static void showPreview(Project project, String title, List<Post> posts) {
        showPreview(project, title, posts, 0);
    }

    /**
     * 显示话题预览
     */
    public static void showPreview(Project project, String title, List<Post> posts, int topicId) {
        // 获取或创建预览工具窗口
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("Linux.do Preview");

        if (toolWindow == null) {
            // 关闭之前打开的对话框
            if (currentDialog != null && currentDialog.isVisible()) {
                currentDialog.dispose();
            }
            
            // 如果没有专门的预览窗口，使用对话框
            JDialog dialog = new JDialog();
            currentDialog = dialog;  // 保存当前对话框引用
            dialog.setTitle("Linux.do - " + title);
            dialog.setSize(900, 700);
            dialog.setLocationRelativeTo(null);
            dialog.setLayout(new BorderLayout());

            TopicPreviewPanel panel = new TopicPreviewPanel();
            panel.setProjectAndTopicId(project, topicId);
            panel.showTopic(title, posts);
            dialog.add(panel, BorderLayout.CENTER);
            
            // 添加底部工具栏
            JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveButton = new JButton(LinuxDoBundle.message("button.saveContent"));
            saveButton.addActionListener(e -> panel.saveAsMarkdown());
            bottomBar.add(saveButton);
            
            // 添加"总结并保存"按钮
            JButton summarizeButton = new JButton(LinuxDoBundle.message("button.summarize"));
            summarizeButton.addActionListener(e -> panel.summarizeAndSave(summarizeButton));
            bottomBar.add(summarizeButton);
            
            dialog.add(bottomBar, BorderLayout.SOUTH);
            
            dialog.setVisible(true);
        } else {
            TopicPreviewPanel panel;
            if (currentInstance == null) {
                panel = new TopicPreviewPanel();
                currentInstance = panel;
                toolWindow.getComponent().add(panel);
            } else {
                panel = currentInstance;
            }
            panel.setProjectAndTopicId(project, topicId);
            panel.showTopic(title, posts);
            toolWindow.show();
        }
    }
}
