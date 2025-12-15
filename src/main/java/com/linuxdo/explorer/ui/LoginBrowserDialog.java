package com.linuxdo.explorer.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.linuxdo.explorer.settings.LinuxDoSettings;
import com.linuxdo.explorer.util.LinuxDoBundle;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
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

    private void showHelp() {
        String helpMessage = LinuxDoBundle.message("login.helpMessage");
        JOptionPane.showMessageDialog(getContentPanel(), helpMessage, LinuxDoBundle.message("login.help"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    protected void doOKAction() {
        if (credentialsCaptured && !capturedCookie.isEmpty()) {
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
