package com.linuxdo.explorer.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.linuxdo.explorer.settings.LinuxDoSettings;
import com.linuxdo.explorer.util.LinuxDoBundle;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCookieVisitor;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 登录浏览器对话框 - 使用内置浏览器登录并自动获取 Cookie 和 User-Agent
 */
public class LoginBrowserDialog extends DialogWrapper {

    private static final String LOGIN_URL = "https://linux.do/login";
    private static final String TARGET_DOMAIN = "linux.do";

    private JBCefBrowser browser;
    private String capturedCookie = "";
    private String capturedUserAgent = "";
    private boolean credentialsCaptured = false;
    private final BiConsumer<String, String> onCredentialsCaptured;
    private JLabel statusLabel;

    public LoginBrowserDialog(@Nullable BiConsumer<String, String> onCredentialsCaptured) {
        super(true);
        this.onCredentialsCaptured = onCredentialsCaptured;
        setTitle(LinuxDoBundle.message("login.title"));
        setModal(true);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(1000, 700));

        // 创建状态栏
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusLabel = new JLabel(LinuxDoBundle.message("login.statusPrompt"));
        statusLabel.setForeground(new Color(100, 100, 100));
        statusBar.add(statusLabel, BorderLayout.WEST);
        
        JButton helpButton = new JButton(LinuxDoBundle.message("login.help"));
        helpButton.addActionListener(e -> showHelp());
        statusBar.add(helpButton, BorderLayout.EAST);
        
        mainPanel.add(statusBar, BorderLayout.NORTH);

        // 创建浏览器
        browser = new JBCefBrowser(LOGIN_URL);
        setupRequestInterceptor();
        setupLoadHandler();

        mainPanel.add(browser.getComponent(), BorderLayout.CENTER);

        return mainPanel;
    }

    private void setupRequestInterceptor() {
        JBCefClient client = browser.getJBCefClient();

        client.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public CefResourceRequestHandler getResourceRequestHandler(
                    CefBrowser browser, CefFrame frame, CefRequest request,
                    boolean isNavigation, boolean isDownload, String requestInitiator,
                    BoolRef disableDefaultHandling) {

                return new CefResourceRequestHandlerAdapter() {
                    @Override
                    public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
                        String url = request.getURL();

                        // 检查是否是 linux.do 域名的请求
                        if (url != null && url.contains(TARGET_DOMAIN)) {
                            // 获取请求头
                            Map<String, String> headers = new HashMap<>();
                            request.getHeaderMap(headers);

                            // 提取 Cookie
                            String cookie = headers.get("Cookie");
                            if (cookie == null) {
                                cookie = headers.get("cookie");
                            }

                            // 提取 User-Agent
                            String userAgent = headers.get("User-Agent");
                            if (userAgent == null) {
                                userAgent = headers.get("user-agent");
                            }

                            // 检查是否成功获取到凭证
                            if (cookie != null && !cookie.isEmpty() && cookie.contains("_t=")) {
                                capturedCookie = cookie;
                                if (userAgent != null && !userAgent.isEmpty()) {
                                    capturedUserAgent = userAgent;
                                }

                                if (!credentialsCaptured) {
                                    credentialsCaptured = true;
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        updateStatus(LinuxDoBundle.message("login.success"), new Color(0, 128, 0));
                                    });
                                }
                            }
                        }

                        return false; // 继续加载资源
                    }
                };
            }
        }, browser.getCefBrowser());
    }

    private void setupLoadHandler() {
        JBCefClient client = browser.getJBCefClient();

        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                                             boolean canGoBack, boolean canGoForward) {
                if (!isLoading && !credentialsCaptured) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        String url = browser.getURL();
                        if (url.contains("linux.do") && !url.contains("/login")) {
                            updateStatus(LinuxDoBundle.message("login.pageLoaded"), new Color(100, 100, 100));
                        }
                    });
                }
            }
        }, browser.getCefBrowser());
    }

    private void updateStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }

    /**
     * 使用 CookieManager 获取浏览器中存储的所有 Cookie
     * 这比从请求头中获取更完整可靠
     */
    private String getAllCookiesFromBrowser() {
        try {
            CefCookieManager cookieManager = CefCookieManager.getGlobalManager();
            if (cookieManager == null) {
                System.err.println("CookieManager is null");
                return capturedCookie; // 回退到之前捕获的 cookie
            }

            List<CefCookie> cookies = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);

            boolean success = cookieManager.visitUrlCookies(
                "https://linux.do",
                true,
                new CefCookieVisitor() {
                    @Override
                    public boolean visit(CefCookie cookie, int count, int total, BoolRef delete) {
                        if (cookie != null && cookie.domain != null && 
                            cookie.domain.contains("linux.do")) {
                            cookies.add(cookie);
                        }
                        // 如果是最后一个 cookie，通知完成
                        if (count == total - 1) {
                            latch.countDown();
                        }
                        return true; // 继续访问
                    }
                }
            );

            if (!success) {
                System.err.println("Failed to visit cookies");
                return capturedCookie;
            }

            // 等待 Cookie 访问完成（最多3秒）
            boolean completed = latch.await(3, TimeUnit.SECONDS);
            if (!completed && cookies.isEmpty()) {
                System.err.println("Cookie visit timeout");
                return capturedCookie;
            }

            // 构建 Cookie 字符串
            if (!cookies.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cookies.size(); i++) {
                    CefCookie cookie = cookies.get(i);
                    if (i > 0) {
                        sb.append("; ");
                    }
                    sb.append(cookie.name).append("=").append(cookie.value);
                }
                String fullCookie = sb.toString();
                System.out.println("Got " + cookies.size() + " cookies from CookieManager");
                return fullCookie;
            }
        } catch (Exception e) {
            System.err.println("Error getting cookies from CookieManager: " + e.getMessage());
            e.printStackTrace();
        }
        return capturedCookie;
    }

    private void showHelp() {
        String helpMessage = LinuxDoBundle.message("login.helpMessage");
        JOptionPane.showMessageDialog(getContentPanel(), helpMessage, LinuxDoBundle.message("login.help"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    protected void doOKAction() {
        if (credentialsCaptured && !capturedCookie.isEmpty()) {
            // 尝试使用 CookieManager 获取完整的 Cookie
            String fullCookie = getAllCookiesFromBrowser();
            if (fullCookie != null && !fullCookie.isEmpty()) {
                capturedCookie = fullCookie;
                System.out.println("Using full cookie from CookieManager, length: " + fullCookie.length());
            }
            
            // 保存凭证到设置
            LinuxDoSettings settings = LinuxDoSettings.getInstance();
            settings.setCookie(capturedCookie);
            if (!capturedUserAgent.isEmpty()) {
                settings.setUserAgent(capturedUserAgent);
            }

            // 回调通知
            if (onCredentialsCaptured != null) {
                onCredentialsCaptured.accept(capturedCookie, capturedUserAgent);
            }
            
            System.out.println("Cookie saved, length: " + capturedCookie.length());
            System.out.println("User-Agent saved: " + capturedUserAgent);
        }
        super.doOKAction();
    }

    @Override
    protected void dispose() {
        if (browser != null) {
            browser.dispose();
        }
        super.dispose();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "LinuxDoLoginBrowserDialog";
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected @Nullable String getHelpId() {
        return null;
    }

    /**
     * 显示登录对话框
     */
    public static void showLoginDialog(@Nullable BiConsumer<String, String> onCredentialsCaptured) {
        LoginBrowserDialog dialog = new LoginBrowserDialog(onCredentialsCaptured);
        dialog.show();
    }
}
