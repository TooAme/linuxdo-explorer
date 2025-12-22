package com.linuxdo.explorer.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.linuxdo.explorer.api.DiscourseApiClient;
import com.linuxdo.explorer.model.*;
import com.linuxdo.explorer.settings.LinuxDoSettings;
import com.linuxdo.explorer.settings.LinuxDoSettingsConfigurable;
import com.linuxdo.explorer.settings.SettingsChangeNotifier;
import com.linuxdo.explorer.ui.TopicPreviewPanel;
import com.linuxdo.explorer.util.LinuxDoBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主面板 - 包含树形视图和工具栏
 */
public class LinuxDoToolWindowPanel extends SimpleToolWindowPanel {

    private final Project project;
    private final ToolWindow toolWindow;
    private final DiscourseApiClient apiClient;
    private final Tree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    // 存储已加载的话题帖子数据
    private final Map<Integer, TopicDetail> topicDataCache = new ConcurrentHashMap<>();
    private final Set<Integer> readNotificationIds = new HashSet<>();
    // 标记已加载子节点的节点
    private final Set<DefaultMutableTreeNode> loadedNodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // 跟踪每个分类的当前页码 (key: categoryId, value: page)
    private final Map<Integer, Integer> categoryPageMap = new ConcurrentHashMap<>();
    private int latestTopicsPage = 0;  // 全部话题的当前页码
    
    // 搜索相关
    private JTextField searchField;
    private DefaultMutableTreeNode searchResultsNode;
    private String currentSearchQuery = "";
    private int searchPage = 0;

    private Timer autoRefreshTimer;
    
    // 内置预览面板相关
    private JBCefBrowser previewBrowser;
    private JPanel previewPanel;
    private JLabel previewTitleLabel;
    private JSplitPane splitPane;
    
    // tooltip模式相关
    private JBPopup currentPopup;
    private JBCefBrowser tooltipBrowser;
    private TreePath lastHoveredPath;
    private javax.swing.Timer popupShowTimer;
    private javax.swing.Timer popupHideTimer;
    private static final int POPUP_SHOW_DELAY = 500;
    private static final int POPUP_HIDE_DELAY = 300;

    public LinuxDoToolWindowPanel(Project project, ToolWindow toolWindow) {
        super(true, true);
        this.project = project;
        this.toolWindow = toolWindow;
        this.apiClient = new DiscourseApiClient();

        // 创建树结构
        rootNode = new DefaultMutableTreeNode("Linux.do");
        treeModel = new DefaultTreeModel(rootNode) {
            @Override
            public boolean isLeaf(Object node) {
                if (node instanceof DefaultMutableTreeNode) {
                    Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
                    if (userObject instanceof TreeNodeData) {
                        TreeNodeData data = (TreeNodeData) userObject;
                        // 分类、全部话题、话题节点都不是叶子节点（可以展开）
                        return data.type == NodeType.POST || 
                               data.type == NodeType.NOTIFICATION || 
                               data.type == NodeType.LOAD_MORE ||
                               data.type == NodeType.LOAD_MORE_TOPICS ||
                               data.type == NodeType.INFO;
                    }
                }
                return super.isLeaf(node);
            }
        };
        
        tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new LinuxDoTreeCellRenderer());
        
        // 禁用双击展开/折叠行为（只允许通过点击展开符号来展开）
        tree.setToggleClickCount(0);
        
        // 禁用 hover 时的文本延伸显示（使用 tooltip 代替）
        tree.putClientProperty("JTree.lineStyle", "None");
        
        // 启用 tooltip，悬浮显示完整内容
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);

        // 添加双击事件和右键菜单
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    // 单击处理 - 用于"加载更多"节点
                    handleSingleClick();
                } else if (e.getClickCount() == 2) {
                    handleDoubleClick();
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }
            
            private void handlePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                
                tree.setSelectionPath(path);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = node.getUserObject();
                
                if (userObject instanceof TreeNodeData) {
                    TreeNodeData data = (TreeNodeData) userObject;
                    showContextMenu(e, data, node);
                }
            }
        });

        // 添加树展开事件
        tree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) {
                TreePath path = event.getPath();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = node.getUserObject();

                if (userObject instanceof TreeNodeData) {
                    TreeNodeData data = (TreeNodeData) userObject;
                    
                    // 检查是否已经加载过
                    if (loadedNodes.contains(node)) {
                        return;
                    }
                    
                    if (data.type == NodeType.CATEGORY) {
                        loadCategoryTopics(node, data.id);
                    } else if (data.type == NodeType.ALL_TOPICS) {
                        loadLatestTopics(node);
                    } else if (data.type == NodeType.TOPIC) {
                        loadTopicPosts(node, data.id);
                    }
                }
            }

            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) {
            }
        });

        // 设置工具栏
        setToolbar(createToolbar());

        // 创建树形视图滚动面板
        JBScrollPane treeScrollPane = new JBScrollPane(tree);
        treeScrollPane.setBorder(null);  // 去掉边框
        
        // 创建内置预览面板
        previewPanel = createPreviewPanel();
        previewPanel.setBorder(null);  // 去掉边框
        
        // 创建分割面板（上下分割）
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScrollPane, previewPanel);
        splitPane.setResizeWeight(0.6);  // 树形视图占60%
        splitPane.setDividerSize(3);  // 细分割条
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);  // 去掉边框
        // 设置分割条颜色为暗色
        splitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(JBColor.background());
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        
        // 设置内容
        setContent(splitPane);

        // 初始加载
        refreshData();

        // 设置自动刷新
        setupAutoRefresh();
        
        // 设置伪装模式监听器
        setupDisguiseMode(treeScrollPane);
        
        // 根据设置初始化预览模式
        setupPreviewMode();
        
        // 订阅设置变更消息，自动刷新
        ApplicationManager.getApplication().getMessageBus()
                .connect()
                .subscribe(SettingsChangeNotifier.TOPIC, new SettingsChangeNotifier() {
                    @Override
                    public void settingsChanged() {
                        // 在 EDT 上执行刷新和预览模式切换
                        ApplicationManager.getApplication().invokeLater(() -> {
                            refreshData();
                            setupPreviewMode();
                        });
                    }
                });
    }
    
    // 伪装模式相关变量
    private boolean isDisguised = false;
    private DefaultTreeModel originalModel;
    private int savedDividerLocation = -1;  // 保存分割条位置
    
    /**
     * 设置伪装模式 - Shift+L 快捷键切换
     */
    private void setupDisguiseMode(JBScrollPane scrollPane) {
        // 使用 KeyListener 监听 Shift+L
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            // 只处理按下事件
            if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED) {
                return false;
            }
            
            // 检查是否是 Shift+L
            if (e.isShiftDown() && e.getKeyCode() == java.awt.event.KeyEvent.VK_L) {
                String mode = LinuxDoSettings.getInstance().getDisguiseMode();
                if (!"off".equals(mode)) {
                    if (isDisguised) {
                        restoreContent();
                    } else {
                        disguiseContent(mode);
                    }
                    return true; // 阻止事件继续传播
                }
            }
            return false;
        });
    }
    
    /**
     * 伪装内容
     */
    private void disguiseContent(String mode) {
        if (isDisguised) return;
        
        originalModel = treeModel;
        isDisguised = true;
        
        // 保存分割条位置并隐藏预览面板
        savedDividerLocation = splitPane.getDividerLocation();
        previewPanel.setVisible(false);
        
        if ("hide".equals(mode)) {
            // 隐藏内容 - 显示空面板
            DefaultMutableTreeNode fakeRoot = new DefaultMutableTreeNode("root");
            fakeRoot.add(new DefaultMutableTreeNode(new TreeNodeData(NodeType.INFO, 0, "Loading...", "")));
            tree.setModel(new DefaultTreeModel(fakeRoot));
        } else {
            // 英文填充
            DefaultMutableTreeNode fakeRoot = new DefaultMutableTreeNode("root");
            fakeRoot.add(new DefaultMutableTreeNode(new TreeNodeData(NodeType.INFO, 0, "Project Structure", "")));
            
            DefaultMutableTreeNode src = new DefaultMutableTreeNode(new TreeNodeData(NodeType.CATEGORY, 1, "src", "4 files"));
            src.add(new DefaultMutableTreeNode(new TreeNodeData(NodeType.INFO, 0, "main.java", "")));
            src.add(new DefaultMutableTreeNode(new TreeNodeData(NodeType.INFO, 0, "utils.java", "")));
            src.add(new DefaultMutableTreeNode(new TreeNodeData(NodeType.INFO, 0, "config.java", "")));
            fakeRoot.add(src);
            
            DefaultMutableTreeNode lib = new DefaultMutableTreeNode(new TreeNodeData(NodeType.CATEGORY, 2, "lib", "3 files"));
            lib.add(new DefaultMutableTreeNode(new TreeNodeData(NodeType.INFO, 0, "commons-io.jar", "")));
            lib.add(new DefaultMutableTreeNode(new TreeNodeData(NodeType.INFO, 0, "gson.jar", "")));
            fakeRoot.add(lib);
            
            DefaultMutableTreeNode test = new DefaultMutableTreeNode(new TreeNodeData(NodeType.CATEGORY, 3, "test", "2 files"));
            test.add(new DefaultMutableTreeNode(new TreeNodeData(NodeType.INFO, 0, "MainTest.java", "")));
            fakeRoot.add(test);
            
            tree.setModel(new DefaultTreeModel(fakeRoot));
        }
    }
    
    /**
     * 恢复真实内容
     */
    private void restoreContent() {
        if (!isDisguised || originalModel == null) return;
        
        tree.setModel(originalModel);
        isDisguised = false;
        
        // 恢复预览面板显示
        previewPanel.setVisible(true);
        
        // 刷新分割面板布局并恢复分割条位置
        splitPane.revalidate();
        splitPane.repaint();
        
        // 在布局完成后恢复分割条位置
        if (savedDividerLocation > 0) {
            SwingUtilities.invokeLater(() -> {
                splitPane.setDividerLocation(savedDividerLocation);
            });
        }
    }

    private JComponent createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();

        // 刷新按钮
        AnAction refreshAction = new AnAction(LinuxDoBundle.message("toolbar.refresh"), LinuxDoBundle.message("toolbar.refresh.tooltip"), AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refreshData();
            }
        };
        group.add(refreshAction);

        // 设置按钮
        AnAction settingsAction = new AnAction(LinuxDoBundle.message("toolbar.settings"), LinuxDoBundle.message("toolbar.settings.tooltip"), AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, LinuxDoSettingsConfigurable.class);
            }
        };
        group.add(settingsAction);

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("LinuxDoExplorer", group, true);
        toolbar.setTargetComponent(this);
        
        // 创建包含工具栏和搜索框的面板
        JPanel toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.add(toolbar.getComponent(), BorderLayout.WEST);
        
        // 创建搜索框
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", LinuxDoBundle.message("search.placeholder"));
        searchField.addActionListener(e -> performSearch(searchField.getText().trim()));
        searchField.setPreferredSize(new Dimension(150, 24));
        
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        searchPanel.add(searchField, BorderLayout.CENTER);
        toolbarPanel.add(searchPanel, BorderLayout.CENTER);
        
        return toolbarPanel;
    }

    private void refreshData() {
        rootNode.removeAllChildren();
        loadedNodes.clear();
        topicDataCache.clear();
        treeModel.reload();

        if (!LinuxDoSettings.getInstance().hasCookie()) {
            DefaultMutableTreeNode loginNode = new DefaultMutableTreeNode(
                    new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.pleaseLogin"), "")
            );
            rootNode.add(loginNode);
            treeModel.reload();
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadData"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText(LinuxDoBundle.message("progress.loadNotifications"));
                    List<com.linuxdo.explorer.model.Notification> notifications = apiClient.getNotifications();

                    indicator.setText(LinuxDoBundle.message("progress.loadCategories"));
                    List<Category> categories = apiClient.getCategories();

                    ApplicationManager.getApplication().invokeLater(() -> {
                        rootNode.removeAllChildren();

                        // 根据设置添加通知
                        String notificationMode = LinuxDoSettings.getInstance().getNotificationMode();
                        if (!"off".equals(notificationMode)) {
                            for (com.linuxdo.explorer.model.Notification notification : notifications) {
                                boolean shouldShow = false;
                                if ("all".equals(notificationMode)) {
                                    // 显示全部（排除已在本地标记为已读的）
                                    shouldShow = !readNotificationIds.contains(notification.getId());
                                } else if ("unread".equals(notificationMode)) {
                                    // 仅显示未读
                                    shouldShow = !notification.isRead() && !readNotificationIds.contains(notification.getId());
                                }
                                
                                if (shouldShow) {
                                    DefaultMutableTreeNode notifNode = new DefaultMutableTreeNode(
                                            new TreeNodeData(NodeType.NOTIFICATION, notification.getId(),
                                                    notification.getDisplayText(), notification.getUrl())
                                    );
                                    rootNode.add(notifNode);
                                }
                            }
                        }

                        // 添加"全部话题"节点
                        DefaultMutableTreeNode allTopicsNode = new DefaultMutableTreeNode(
                                new TreeNodeData(NodeType.ALL_TOPICS, 0, LinuxDoBundle.message("node.allTopics"), "")
                        );
                        rootNode.add(allTopicsNode);

                        // 添加分类节点
                        for (Category category : categories) {
                            DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(
                                    new TreeNodeData(NodeType.CATEGORY, category.getId(),
                                            category.getName(), "x " + category.getTopicCount())
                            );
                            rootNode.add(catNode);
                        }

                        treeModel.reload();
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        showError(LinuxDoBundle.message("message.loadFailed") + e.getMessage());
                    });
                }
            }
        });
    }

    private void loadCategoryTopics(DefaultMutableTreeNode parentNode, int categoryId) {
        // 重置页码
        categoryPageMap.put(categoryId, 0);
        
        // 添加加载中提示
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode(
                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.loading"), "")
        );
        parentNode.add(loadingNode);
        treeModel.nodeStructureChanged(parentNode);

        int topicsPerLoad = LinuxDoSettings.getInstance().getTopicsPerLoad();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadTopics"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<Topic> topics = apiClient.getCategoryTopics(categoryId, 0);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        parentNode.removeAllChildren();
                        
                        if (topics.isEmpty()) {
                            DefaultMutableTreeNode emptyNode = new DefaultMutableTreeNode(
                                    new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.noData"), "")
                            );
                            parentNode.add(emptyNode);
                        } else {
                            for (Topic topic : topics) {
                                DefaultMutableTreeNode topicNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.TOPIC, topic.getId(), topic.getTitle(),
                                                "V." + topic.getViews() + " R." + topic.getReplyCount(),
                                                topic.getUrl())
                                );
                                parentNode.add(topicNode);
                            }
                            
                            // 只要有话题返回，就假设可能还有更多
                            // API 分页大小通常是固定的（约30个），与用户设置无关
                            if (topics.size() > 0) {
                                // 添加"加载更多话题"节点，id 存储 categoryId
                                DefaultMutableTreeNode loadMoreNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.LOAD_MORE_TOPICS, categoryId, 
                                                LinuxDoBundle.message("node.loadMore"), "")
                                );
                                parentNode.add(loadMoreNode);
                            }
                        }
                        
                        loadedNodes.add(parentNode);
                        treeModel.nodeStructureChanged(parentNode);
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        parentNode.removeAllChildren();
                        DefaultMutableTreeNode errorNode = new DefaultMutableTreeNode(
                                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("message.loadFailed") + e.getMessage(), "")
                        );
                        parentNode.add(errorNode);
                        treeModel.nodeStructureChanged(parentNode);
                        showError(LinuxDoBundle.message("message.loadTopicFailed") + e.getMessage());
                    });
                }
            }
        });
    }

    private void loadLatestTopics(DefaultMutableTreeNode parentNode) {
        // 重置页码
        latestTopicsPage = 0;
        
        // 添加加载中提示
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode(
                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.loading"), "")
        );
        parentNode.add(loadingNode);
        treeModel.nodeStructureChanged(parentNode);

        int topicsPerLoad = LinuxDoSettings.getInstance().getTopicsPerLoad();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadLatest"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<Topic> topics = apiClient.getLatestTopics(0);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        parentNode.removeAllChildren();
                        
                        if (topics.isEmpty()) {
                            DefaultMutableTreeNode emptyNode = new DefaultMutableTreeNode(
                                    new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.noData"), "")
                            );
                            parentNode.add(emptyNode);
                        } else {
                            for (Topic topic : topics) {
                                DefaultMutableTreeNode topicNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.TOPIC, topic.getId(), topic.getTitle(),
                                                "V." + topic.getViews() + " R." + topic.getReplyCount(),
                                                topic.getUrl())
                                );
                                parentNode.add(topicNode);
                            }
                            
                            // 只要有话题返回，就假设可能还有更多
                            if (topics.size() > 0) {
                                // 添加"加载更多话题"节点，id = -1 表示全部话题
                                DefaultMutableTreeNode loadMoreNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.LOAD_MORE_TOPICS, -1, 
                                                LinuxDoBundle.message("node.loadMore"), "")
                                );
                                parentNode.add(loadMoreNode);
                            }
                        }
                        
                        loadedNodes.add(parentNode);
                        treeModel.nodeStructureChanged(parentNode);
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        parentNode.removeAllChildren();
                        DefaultMutableTreeNode errorNode = new DefaultMutableTreeNode(
                                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("message.loadFailed") + e.getMessage(), "")
                        );
                        parentNode.add(errorNode);
                        treeModel.nodeStructureChanged(parentNode);
                        showError(LinuxDoBundle.message("message.loadTopicFailed") + e.getMessage());
                    });
                }
            }
        });
    }

    private void loadTopicPosts(DefaultMutableTreeNode parentNode, int topicId) {
        // 添加加载中提示
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode(
                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.loading"), "")
        );
        parentNode.add(loadingNode);
        treeModel.nodeStructureChanged(parentNode);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadPosts"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    TopicDetail detail = apiClient.getTopicDetail(topicId);
                    topicDataCache.put(topicId, detail);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        parentNode.removeAllChildren();
                        
                        for (Post post : detail.getPosts()) {
                            String fullPreview = post.getTextPreview();
                            String preview = fullPreview;
                            if (preview.length() > 50) {
                                preview = preview.substring(0, 50) + "...";
                            }
                            DefaultMutableTreeNode postNode = new DefaultMutableTreeNode(
                                    new TreeNodeData(NodeType.POST, post.getId(),
                                            "@" + post.getUsername(),
                                            "#" + post.getPostNumber() + " - " + preview,
                                            null,
                                            "#" + post.getPostNumber() + " @" + post.getUsername() + "\n" + fullPreview,
                                            post.getCooked())  // HTML 内容，用于图片预览
                            );
                            parentNode.add(postNode);
                        }

                        // 如果还有更多帖子，添加"加载更多"节点
                        if (detail.getPosts().size() < detail.getPostsCount()) {
                            DefaultMutableTreeNode loadMoreNode = new DefaultMutableTreeNode(
                                    new TreeNodeData(NodeType.LOAD_MORE, topicId,
                                            LinuxDoBundle.message("node.loadMore"),
                                            "+" + (detail.getPostsCount() - detail.getPosts().size()))
                            );
                            parentNode.add(loadMoreNode);
                        }

                        loadedNodes.add(parentNode);
                        treeModel.nodeStructureChanged(parentNode);
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        parentNode.removeAllChildren();
                        DefaultMutableTreeNode errorNode = new DefaultMutableTreeNode(
                                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("message.loadFailed") + e.getMessage(), "")
                        );
                        parentNode.add(errorNode);
                        treeModel.nodeStructureChanged(parentNode);
                        showError(LinuxDoBundle.message("message.loadPostsFailed") + e.getMessage());
                    });
                }
            }
        });
    }

    /**
     * 单击处理 - 处理"加载更多"节点和POST节点预览
     */
    private void handleSingleClick() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof TreeNodeData) {
            TreeNodeData data = (TreeNodeData) userObject;

            switch (data.type) {
                case POST:
                    // 单击POST节点时在底部预览面板显示内容
                    showPostPreview(data);
                    break;
                case LOAD_MORE:
                    loadMorePosts(node, data.id);
                    break;
                case LOAD_MORE_TOPICS:
                    loadMoreTopics(node, data.id);
                    break;
            }
        }
    }

    private void handleDoubleClick() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof TreeNodeData) {
            TreeNodeData data = (TreeNodeData) userObject;

            switch (data.type) {
                case TOPIC:
                    openTopicPreview(data.id, data.label);
                    break;
                case NOTIFICATION:
                    if (data.url != null && !data.url.isEmpty()) {
                        BrowserUtil.browse(data.url);
                        readNotificationIds.add(data.id);
                        refreshData();
                    }
                    break;
                case LOAD_MORE:
                    loadMorePosts(node, data.id);
                    break;
                case LOAD_MORE_TOPICS:
                    loadMoreTopics(node, data.id);
                    break;
            }
        }
    }

    /**
     * 显示右键菜单
     */
    private void showContextMenu(MouseEvent e, TreeNodeData data, DefaultMutableTreeNode node) {
        JPopupMenu popup = new JPopupMenu();
        
        switch (data.type) {
            case CATEGORY:
                // 分类菜单 - 刷新分类
                JMenuItem refreshCategoryItem = new JMenuItem(LinuxDoBundle.message("menu.refreshCategory"));
                refreshCategoryItem.addActionListener(ev -> {
                    refreshCategoryNode(node, data.id);
                });
                popup.add(refreshCategoryItem);
                break;
                
            case ALL_TOPICS:
                // 全部话题菜单 - 刷新
                JMenuItem refreshAllTopicsItem = new JMenuItem(LinuxDoBundle.message("menu.refreshCategory"));
                refreshAllTopicsItem.addActionListener(ev -> {
                    refreshAllTopicsNode(node);
                });
                popup.add(refreshAllTopicsItem);
                break;
                
            case TOPIC:
                // 话题菜单
                JMenuItem refreshTopicItem = new JMenuItem(LinuxDoBundle.message("menu.refreshTopic"));
                refreshTopicItem.addActionListener(ev -> {
                    refreshTopicNode(node, data.id);
                });
                popup.add(refreshTopicItem);
                
                popup.addSeparator();
                
                JMenuItem summarizeItem = new JMenuItem(LinuxDoBundle.message("menu.summarize"));
                summarizeItem.addActionListener(ev -> {
                    // 先加载话题内容，然后执行总结
                    openAndSummarizeTopic(data.id, data.label);
                });
                popup.add(summarizeItem);
                
                JMenuItem saveItem = new JMenuItem(LinuxDoBundle.message("menu.saveContent"));
                saveItem.addActionListener(ev -> {
                    openAndSaveTopic(data.id, data.label);
                });
                popup.add(saveItem);
                
                popup.addSeparator();
                
                // 复制链接
                JMenuItem copyTopicLink = new JMenuItem(LinuxDoBundle.message("menu.copyLink"));
                copyTopicLink.addActionListener(ev -> {
                    String url = "https://linux.do/t/topic/" + data.id;
                    copyToClipboard(url);
                    showInfo(LinuxDoBundle.message("message.linkCopied"));
                });
                popup.add(copyTopicLink);
                
                // 在浏览器中打开
                JMenuItem openTopicInBrowser = new JMenuItem(LinuxDoBundle.message("menu.openInBrowser"));
                openTopicInBrowser.addActionListener(ev -> {
                    String url = "https://linux.do/t/topic/" + data.id;
                    BrowserUtil.browse(url);
                });
                popup.add(openTopicInBrowser);
                break;
                
            case POST:
                // 回复菜单 - 需要找到父话题的 ID
                int topicId = findParentTopicId(node);
                int postNumber = extractPostNumber(data.description);
                
                // 复制链接
                JMenuItem copyPostLink = new JMenuItem(LinuxDoBundle.message("menu.copyLink"));
                copyPostLink.addActionListener(ev -> {
                    String url = "https://linux.do/t/topic/" + topicId + "/" + postNumber;
                    copyToClipboard(url);
                    showInfo(LinuxDoBundle.message("message.linkCopied"));
                });
                popup.add(copyPostLink);
                
                // 在浏览器中打开
                JMenuItem openPostInBrowser = new JMenuItem(LinuxDoBundle.message("menu.openInBrowser"));
                openPostInBrowser.addActionListener(ev -> {
                    String url = "https://linux.do/t/topic/" + topicId + "/" + postNumber;
                    BrowserUtil.browse(url);
                });
                popup.add(openPostInBrowser);
                break;
                
            default:
                return; // 其他类型不显示菜单
        }
        
        popup.show(e.getComponent(), e.getX(), e.getY());
    }
    
    /**
     * 刷新单个分类节点
     */
    private void refreshCategoryNode(DefaultMutableTreeNode node, int categoryId) {
        // 记录当前是否展开
        TreePath path = new TreePath(node.getPath());
        boolean wasExpanded = tree.isExpanded(path);
        
        // 移除已加载标记，清空子节点
        loadedNodes.remove(node);
        node.removeAllChildren();
        treeModel.nodeStructureChanged(node);
        
        // 重新加载
        loadCategoryTopics(node, categoryId);
        
        // 保持展开状态
        if (wasExpanded) {
            ApplicationManager.getApplication().invokeLater(() -> {
                tree.expandPath(path);
            });
        }
    }
    
    /**
     * 刷新全部话题节点
     */
    private void refreshAllTopicsNode(DefaultMutableTreeNode node) {
        // 记录当前是否展开
        TreePath path = new TreePath(node.getPath());
        boolean wasExpanded = tree.isExpanded(path);
        
        // 移除已加载标记，清空子节点
        loadedNodes.remove(node);
        node.removeAllChildren();
        treeModel.nodeStructureChanged(node);
        
        // 重新加载
        loadLatestTopics(node);
        
        // 保持展开状态
        if (wasExpanded) {
            ApplicationManager.getApplication().invokeLater(() -> {
                tree.expandPath(path);
            });
        }
    }
    
    /**
     * 刷新单个话题节点（重新加载帖子）
     */
    private void refreshTopicNode(DefaultMutableTreeNode node, int topicId) {
        // 记录当前是否展开
        TreePath path = new TreePath(node.getPath());
        boolean wasExpanded = tree.isExpanded(path);
        
        // 移除已加载标记，清空子节点，清除缓存
        loadedNodes.remove(node);
        topicDataCache.remove(topicId);
        node.removeAllChildren();
        treeModel.nodeStructureChanged(node);
        
        // 重新加载
        loadTopicPosts(node, topicId);
        
        // 保持展开状态
        if (wasExpanded) {
            ApplicationManager.getApplication().invokeLater(() -> {
                tree.expandPath(path);
            });
        }
    }

    
    /**
     * 复制文本到剪贴板
     */
    private void copyToClipboard(String text) {
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }
    
    /**
     * 查找父话题的 ID
     */
    private int findParentTopicId(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
        while (parent != null) {
            Object userObject = parent.getUserObject();
            if (userObject instanceof TreeNodeData) {
                TreeNodeData data = (TreeNodeData) userObject;
                if (data.type == NodeType.TOPIC) {
                    return data.id;
                }
            }
            parent = (DefaultMutableTreeNode) parent.getParent();
        }
        return 0;
    }
    
    /**
     * 从描述中提取帖子编号
     */
    private int extractPostNumber(String description) {
        // 格式如: "#1 - 内容预览..."
        if (description != null && description.startsWith("#")) {
            int spaceIndex = description.indexOf(' ');
            if (spaceIndex > 1) {
                try {
                    return Integer.parseInt(description.substring(1, spaceIndex));
                } catch (NumberFormatException ignored) {}
            }
        }
        return 1;
    }
    
    /**
     * 加载话题并执行 AI 总结
     */
    private void openAndSummarizeTopic(int topicId, String title) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadContent"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    TopicDetail detail = apiClient.getTopicDetail(topicId);
                    List<Post> allPosts = loadAllPosts(detail);
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        TopicPreviewPanel panel = new TopicPreviewPanel();
                        panel.setProjectAndTopicId(project, topicId);
                        panel.showTopic(title, allPosts);
                        panel.summarizeAndSave();
                    });
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        showError(LinuxDoBundle.message("message.loadTopicFailed") + ex.getMessage());
                    });
                }
            }
        });
    }
    
    /**
     * 加载话题并保存为 Markdown
     */
    private void openAndSaveTopic(int topicId, String title) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadContent"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    TopicDetail detail = apiClient.getTopicDetail(topicId);
                    List<Post> allPosts = loadAllPosts(detail);
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        TopicPreviewPanel panel = new TopicPreviewPanel();
                        panel.setProjectAndTopicId(project, topicId);
                        panel.showTopic(title, allPosts);
                        panel.saveAsMarkdown();
                    });
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        showError(LinuxDoBundle.message("message.loadTopicFailed") + ex.getMessage());
                    });
                }
            }
        });
    }
    
    /**
     * 加载话题的所有帖子
     */
    private List<Post> loadAllPosts(TopicDetail detail) throws IOException {
        List<Post> allPosts = new ArrayList<>(detail.getPosts());
        Set<Integer> loadedIds = new HashSet<>();
        for (Post p : allPosts) {
            loadedIds.add(p.getId());
        }

        List<Integer> stream = detail.getStream();
        List<Integer> unloadedIds = new ArrayList<>();
        for (Integer id : stream) {
            if (!loadedIds.contains(id)) {
                unloadedIds.add(id);
            }
        }

        int batchSize = 50;
        for (int i = 0; i < unloadedIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, unloadedIds.size());
            List<Integer> batch = unloadedIds.subList(i, end);
            List<Post> morePosts = apiClient.loadMorePosts(detail.getId(), batch);
            allPosts.addAll(morePosts);
        }
        
        return allPosts;
    }

    private void openTopicPreview(int topicId, String title) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadContent"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    TopicDetail detail = apiClient.getTopicDetail(topicId);

                    // 加载所有帖子
                    List<Post> allPosts = new ArrayList<>(detail.getPosts());
                    Set<Integer> loadedIds = new HashSet<>();
                    for (Post p : allPosts) {
                        loadedIds.add(p.getId());
                    }

                    List<Integer> stream = detail.getStream();
                    List<Integer> unloadedIds = new ArrayList<>();
                    for (Integer id : stream) {
                        if (!loadedIds.contains(id)) {
                            unloadedIds.add(id);
                        }
                    }

                    // 分批加载剩余帖子
                    int batchSize = 50;
                    for (int i = 0; i < unloadedIds.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, unloadedIds.size());
                        List<Integer> batchIds = unloadedIds.subList(i, end);
                        List<Post> morePosts = apiClient.loadMorePosts(topicId, batchIds);
                        allPosts.addAll(morePosts);
                    }

                    // 按楼层排序
                    allPosts.sort(Comparator.comparingInt(Post::getPostNumber));

                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 使用原来的预览窗口，并传递 project 和 topicId 用于保存功能
                        TopicPreviewPanel.showPreview(project, title, allPosts, topicId);
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        showError(LinuxDoBundle.message("message.loadTopicFailed") + e.getMessage());
                    });
                }
            }
        });
    }

    private void loadMorePosts(DefaultMutableTreeNode loadMoreNode, int topicId) {
        TopicDetail detail = topicDataCache.get(topicId);
        if (detail == null) return;

        Set<Integer> loadedIds = new HashSet<>();
        for (Post p : detail.getPosts()) {
            loadedIds.add(p.getId());
        }

        List<Integer> unloadedIds = new ArrayList<>();
        for (Integer id : detail.getStream()) {
            if (!loadedIds.contains(id)) {
                unloadedIds.add(id);
            }
        }

        if (unloadedIds.isEmpty()) {
            showInfo(LinuxDoBundle.message("message.noMorePosts"));
            return;
        }

        int repliesPerLoad = LinuxDoSettings.getInstance().getRepliesPerLoad();
        List<Integer> nextBatch = unloadedIds.subList(0, Math.min(repliesPerLoad, unloadedIds.size()));

        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadMore"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<Post> morePosts = apiClient.loadMorePosts(topicId, nextBatch);
                    detail.getPosts().addAll(morePosts);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) loadMoreNode.getParent();
                        int loadMoreIndex = parentNode.getIndex(loadMoreNode);
                        parentNode.remove(loadMoreNode);

                        for (Post post : morePosts) {
                            String fullPreview = post.getTextPreview();
                            String preview = fullPreview;
                            if (preview.length() > 50) {
                                preview = preview.substring(0, 50) + "...";
                            }
                            DefaultMutableTreeNode postNode = new DefaultMutableTreeNode(
                                    new TreeNodeData(NodeType.POST, post.getId(),
                                            "@" + post.getUsername(),
                                            "#" + post.getPostNumber() + " - " + preview,
                                            null,
                                            "#" + post.getPostNumber() + " @" + post.getUsername() + "\n" + fullPreview,
                                            post.getCooked())  // HTML 内容，用于图片预览
                            );
                            parentNode.insert(postNode, loadMoreIndex++);
                        }

                        // 如果还有更多，再添加"加载更多"节点
                        int remaining = detail.getPostsCount() - detail.getPosts().size();
                        if (remaining > 0) {
                            DefaultMutableTreeNode newLoadMore = new DefaultMutableTreeNode(
                                    new TreeNodeData(NodeType.LOAD_MORE, topicId, LinuxDoBundle.message("node.loadMore"), "+" + remaining)
                            );
                            parentNode.add(newLoadMore);
                        }

                        treeModel.nodeStructureChanged(parentNode);
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        showError(LinuxDoBundle.message("message.loadMoreFailed") + e.getMessage());
                    });
                }
            }
        });
    }

    /**
     * 加载更多话题
     * @param loadMoreNode 加载更多节点
     * @param categoryId 分类ID，-1 表示全部话题，-2 表示搜索结果
     */
    private void loadMoreTopics(DefaultMutableTreeNode loadMoreNode, int categoryId) {
        // 如果是搜索结果，使用专门的方法处理
        if (categoryId == -2) {
            loadMoreSearchResults(loadMoreNode);
            return;
        }
        
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) loadMoreNode.getParent();
        if (parentNode == null) return;

        // 获取并递增页码
        int nextPage;
        if (categoryId == -1) {
            // 全部话题
            nextPage = ++latestTopicsPage;
        } else {
            // 分类话题
            nextPage = categoryPageMap.getOrDefault(categoryId, 0) + 1;
            categoryPageMap.put(categoryId, nextPage);
        }

        // 更新节点显示正在加载
        loadMoreNode.setUserObject(new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.loading"), ""));
        treeModel.nodeChanged(loadMoreNode);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.loadMore"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<Topic> topics;
                    if (categoryId == -1) {
                        topics = apiClient.getLatestTopics(nextPage);
                    } else {
                        topics = apiClient.getCategoryTopics(categoryId, nextPage);
                    }

                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 移除加载更多节点
                        int loadMoreIndex = parentNode.getIndex(loadMoreNode);
                        parentNode.remove(loadMoreNode);

                        if (!topics.isEmpty()) {
                            // 添加新话题
                            for (Topic topic : topics) {
                                DefaultMutableTreeNode topicNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.TOPIC, topic.getId(), topic.getTitle(),
                                                "V." + topic.getViews() + " R." + topic.getReplyCount(),
                                                topic.getUrl())
                                );
                                parentNode.insert(topicNode, loadMoreIndex++);
                            }

                            // 只要有话题返回，就假设可能还有更多
                            if (topics.size() > 0) {
                                DefaultMutableTreeNode newLoadMore = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.LOAD_MORE_TOPICS, categoryId, 
                                                LinuxDoBundle.message("node.loadMore"), "")
                                );
                                parentNode.add(newLoadMore);
                            }
                        }

                        treeModel.nodeStructureChanged(parentNode);
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 恢复加载更多节点
                        loadMoreNode.setUserObject(new TreeNodeData(NodeType.LOAD_MORE_TOPICS, categoryId, 
                                LinuxDoBundle.message("node.loadMore"), ""));
                        treeModel.nodeChanged(loadMoreNode);
                        showError(LinuxDoBundle.message("message.loadMoreFailed") + e.getMessage());
                    });
                }
            }
        });
    }

    /**
     * 执行搜索
     */
    private void performSearch(String query) {
        if (query.isEmpty()) {
            // 清除搜索结果，刷新原始数据
            if (searchResultsNode != null) {
                rootNode.remove(searchResultsNode);
                searchResultsNode = null;
                treeModel.nodeStructureChanged(rootNode);
            }
            currentSearchQuery = "";
            return;
        }

        currentSearchQuery = query;
        searchPage = 0;

        // 创建或更新搜索结果节点
        if (searchResultsNode == null) {
            searchResultsNode = new DefaultMutableTreeNode(
                    new TreeNodeData(NodeType.SEARCH_RESULTS, 0, LinuxDoBundle.message("node.searchResults"), "")
            );
            rootNode.insert(searchResultsNode, 0);
        } else {
            searchResultsNode.removeAllChildren();
        }

        // 添加加载中提示
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode(
                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.loading"), "")
        );
        searchResultsNode.add(loadingNode);
        treeModel.nodeStructureChanged(rootNode);
        tree.expandPath(new TreePath(new Object[]{rootNode, searchResultsNode}));

        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.search"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<Topic> topics = apiClient.searchTopics(query, 0);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        searchResultsNode.removeAllChildren();
                        
                        if (topics.isEmpty()) {
                            DefaultMutableTreeNode emptyNode = new DefaultMutableTreeNode(
                                    new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("search.noResults"), "")
                            );
                            searchResultsNode.add(emptyNode);
                        } else {
                            for (Topic topic : topics) {
                                DefaultMutableTreeNode topicNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.TOPIC, topic.getId(), topic.getTitle(),
                                                "V." + topic.getViews() + " R." + topic.getReplyCount(),
                                                topic.getUrl())
                                );
                                searchResultsNode.add(topicNode);
                            }
                            
                            // 如果有结果，添加加载更多
                            if (topics.size() > 0) {
                                DefaultMutableTreeNode loadMoreNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.LOAD_MORE_TOPICS, -2, 
                                                LinuxDoBundle.message("node.loadMore"), "")
                                );
                                searchResultsNode.add(loadMoreNode);
                            }
                        }
                        
                        treeModel.nodeStructureChanged(searchResultsNode);
                        tree.expandPath(new TreePath(new Object[]{rootNode, searchResultsNode}));
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        searchResultsNode.removeAllChildren();
                        DefaultMutableTreeNode errorNode = new DefaultMutableTreeNode(
                                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("message.loadFailed") + e.getMessage(), "")
                        );
                        searchResultsNode.add(errorNode);
                        treeModel.nodeStructureChanged(searchResultsNode);
                    });
                }
            }
        });
    }

    /**
     * 加载更多搜索结果
     */
    private void loadMoreSearchResults(DefaultMutableTreeNode loadMoreNode) {
        searchPage++;

        loadMoreNode.setUserObject(new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.loading"), ""));
        treeModel.nodeChanged(loadMoreNode);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, LinuxDoBundle.message("progress.search"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<Topic> topics = apiClient.searchTopics(currentSearchQuery, searchPage);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        int loadMoreIndex = searchResultsNode.getIndex(loadMoreNode);
                        searchResultsNode.remove(loadMoreNode);

                        if (!topics.isEmpty()) {
                            for (Topic topic : topics) {
                                DefaultMutableTreeNode topicNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.TOPIC, topic.getId(), topic.getTitle(),
                                                "V." + topic.getViews() + " R." + topic.getReplyCount(),
                                                topic.getUrl())
                                );
                                searchResultsNode.insert(topicNode, loadMoreIndex++);
                            }

                            // 继续添加加载更多
                            if (topics.size() > 0) {
                                DefaultMutableTreeNode newLoadMore = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.LOAD_MORE_TOPICS, -2, 
                                                LinuxDoBundle.message("node.loadMore"), "")
                                );
                                searchResultsNode.add(newLoadMore);
                            }
                        }

                        treeModel.nodeStructureChanged(searchResultsNode);
                    });
                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        loadMoreNode.setUserObject(new TreeNodeData(NodeType.LOAD_MORE_TOPICS, -2, 
                                LinuxDoBundle.message("node.loadMore"), ""));
                        treeModel.nodeChanged(loadMoreNode);
                        showError(LinuxDoBundle.message("message.loadMoreFailed") + e.getMessage());
                    });
                }
            }
        });
    }

    private void setupAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
            autoRefreshTimer = null;
        }

        int minutes = LinuxDoSettings.getInstance().getAutoRefreshMinutes();
        if (minutes > 0) {
            autoRefreshTimer = new Timer();
            autoRefreshTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    ApplicationManager.getApplication().invokeLater(() -> refreshData());
                }
            }, minutes * 60 * 1000L, minutes * 60 * 1000L);
        }
    }

    private void showError(String message) {
        Notification notification = new Notification(
                "Linux.do Explorer",
                LinuxDoBundle.message("message.error"),
                message,
                NotificationType.ERROR
        );
        
        // 添加"刷新"按钮
        notification.addAction(NotificationAction.createSimple(LinuxDoBundle.message("button.refresh"), () -> {
            notification.expire();
            refreshData();
        }));
        
        // 添加"重新登录"按钮
        notification.addAction(NotificationAction.createSimple(LinuxDoBundle.message("button.relogin"), () -> {
            notification.expire();
            ShowSettingsUtil.getInstance().showSettingsDialog(project, LinuxDoSettingsConfigurable.class);
        }));
        
        Notifications.Bus.notify(notification, project);
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
     * 创建内置预览面板
     */
    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        // 不设置边框，保持简洁
        
        // 标题栏
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        titleBar.setBackground(JBColor.background());
        
        previewTitleLabel = new JLabel(LinuxDoBundle.message("preview.selectPost"));
        previewTitleLabel.setForeground(JBColor.foreground());
        titleBar.add(previewTitleLabel, BorderLayout.CENTER);
        
        // 关闭按钮
        JButton closeButton = new JButton("×");
        closeButton.setMargin(new Insets(0, 4, 0, 4));
        closeButton.setFocusPainted(false);
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setToolTipText(LinuxDoBundle.message("preview.close"));
        closeButton.addActionListener(e -> clearPreview());
        titleBar.add(closeButton, BorderLayout.EAST);
        
        panel.add(titleBar, BorderLayout.NORTH);
        
        // 浏览器预览区域
        previewBrowser = new JBCefBrowser();
        panel.add(previewBrowser.getComponent(), BorderLayout.CENTER);
        
        // 初始显示空白提示
        showEmptyPreview();
        
        return panel;
    }
    
    /**
     * 显示空白预览提示
     */
    private void showEmptyPreview() {
        boolean isDark = !JBColor.isBright();
        String bgColor = isDark ? "#2b2b2b" : "#ffffff";
        String textColor = isDark ? "#888888" : "#999999";
        
        String html = "<html><head><style>" +
                "body { font-family: 'Microsoft YaHei', sans-serif; " +
                "background-color: " + bgColor + "; " +
                "color: " + textColor + "; " +
                "display: flex; justify-content: center; align-items: center; " +
                "height: 100vh; margin: 0; text-align: center; }" +
                "</style></head><body>" +
                "<div>" + LinuxDoBundle.message("preview.clickToPreview") + "</div>" +
                "</body></html>";
        
        previewBrowser.loadHTML(html);
    }
    
    /**
     * 清空预览内容
     */
    private void clearPreview() {
        previewTitleLabel.setText(LinuxDoBundle.message("preview.selectPost"));
        showEmptyPreview();
    }
    
    /**
     * 在底部预览面板显示POST内容
     */
    private void showPostPreview(TreeNodeData data) {
        if (data == null || data.type != NodeType.POST) {
            return;
        }
        
        // 更新标题
        String title = "<html><b>" + escapeHtml(data.label) + "</b> " +
                "<span style='color:gray;'>" + escapeHtml(data.description) + "</span></html>";
        previewTitleLabel.setText(title);
        
        // 构建HTML内容并加载
        String htmlContent = buildPreviewHtml(data);
        previewBrowser.loadHTML(htmlContent);
    }
    
    /**
     * 为预览面板构建HTML内容
     */
    private String buildPreviewHtml(TreeNodeData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>");
        
        // 判断是否是暗色主题
        boolean isDark = !JBColor.isBright();
        String bgColor = isDark ? "#2b2b2b" : "#ffffff";
        String textColor = isDark ? "#a9b7c6" : "#000000";
        String linkColor = "#589df6";
        
        // 获取黑白模式设置
        LinuxDoSettings settings = LinuxDoSettings.getInstance();
        boolean isGrayscale = settings.isGrayscaleImages();
        
        sb.append("body { font-family: 'Microsoft YaHei', sans-serif; font-size: 13px; ");
        sb.append("background-color: ").append(bgColor).append("; ");
        sb.append("color: ").append(textColor).append("; ");
        sb.append("padding: 12px; margin: 0; line-height: 1.6; }");
        sb.append("img { max-width: 100%; height: auto; display: block; margin: 8px 0; border-radius: 4px; ");
        // 黑白模式 - 图片灰度滤镜
        if (isGrayscale) {
            sb.append("filter: grayscale(1); transition: filter 0.3s; }");
            sb.append("img:hover { filter: grayscale(0); }");  // 鼠标悬停时恢复彩色
        } else {
            sb.append("}");
        }
        sb.append("a { color: ").append(linkColor).append("; }");
        sb.append("pre { white-space: pre-wrap; word-wrap: break-word; background: ").append(isDark ? "#1e1e1e" : "#f5f5f5").append("; padding: 8px; border-radius: 4px; }");
        sb.append("code { background: ").append(isDark ? "#1e1e1e" : "#f5f5f5").append("; padding: 2px 4px; border-radius: 3px; }");
        sb.append("blockquote { border-left: 3px solid ").append(linkColor).append("; margin: 8px 0; padding-left: 12px; color: ").append(isDark ? "#888" : "#666").append("; }");
        // 隐藏图片的meta信息元素
        sb.append(".meta, .image-wrapper .meta, .lightbox-wrapper .meta, span.meta { display: none !important; }");
        // 滚动条样式 - 暗色主题
        sb.append("::-webkit-scrollbar { width: 8px; height: 8px; }");
        sb.append("::-webkit-scrollbar-track { background: ").append(isDark ? "#1e1e1e" : "#f0f0f0").append("; }");
        sb.append("::-webkit-scrollbar-thumb { background: ").append(isDark ? "#555" : "#ccc").append("; border-radius: 4px; }");
        sb.append("::-webkit-scrollbar-thumb:hover { background: ").append(isDark ? "#666" : "#bbb").append("; }");
        sb.append("</style></head><body>");
        
        // 如果有HTML内容，使用HTML内容（包含图片）
        if (data.htmlContent != null && !data.htmlContent.isEmpty()) {
            // 处理相对路径的图片URL
            String html = data.htmlContent;
            html = html.replaceAll("src=\"/uploads/", "src=\"https://linux.do/uploads/");
            html = html.replaceAll("src='/uploads/", "src='https://linux.do/uploads/");
            sb.append(html);
        } else if (data.fullContent != null) {
            // 回退到纯文本
            sb.append("<pre>").append(escapeHtml(data.fullContent)).append("</pre>");
        }
        
        sb.append("</body></html>");
        return sb.toString();
    }
    
    /**
     * HTML转义
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
    
    // ============ 预览模式切换 ============
    
    private boolean isTooltipMode = false;
    private MouseMotionAdapter tooltipMouseMotionAdapter;
    private MouseAdapter tooltipMouseAdapter;
    private Point lastMouseScreenPoint;  // 保存鼠标屏幕位置
    
    /**
     * 根据设置切换预览模式
     */
    private void setupPreviewMode() {
        String mode = LinuxDoSettings.getInstance().getPreviewMode();
        boolean newTooltipMode = "tooltip".equals(mode);
        
        if (newTooltipMode == isTooltipMode) {
            return; // 模式没变化
        }
        
        isTooltipMode = newTooltipMode;
        
        if (isTooltipMode) {
            // 切换到tooltip模式 - 先保存分割条位置
            savedDividerLocation = splitPane.getDividerLocation();
            previewPanel.setVisible(false);
            splitPane.revalidate();
            splitPane.repaint();
            setupTooltipListeners();
        } else {
            // 切换到面板模式
            removeTooltipListeners();
            hideTooltipPopup();
            previewPanel.setVisible(true);
            
            // 强制刷新布局并恢复分割条位置
            splitPane.revalidate();
            splitPane.repaint();
            
            // 使用多次invokeLater确保布局完成后再设置位置
            SwingUtilities.invokeLater(() -> {
                if (savedDividerLocation > 0) {
                    splitPane.setDividerLocation(savedDividerLocation);
                } else {
                    // 如果没有保存的位置，设置为60%
                    splitPane.setDividerLocation(0.6);
                }
                splitPane.revalidate();
            });
        }
    }
    
    /**
     * 设置tooltip模式监听器
     */
    private void setupTooltipListeners() {
        if (popupShowTimer == null) {
            popupShowTimer = new javax.swing.Timer(POPUP_SHOW_DELAY, e -> {
                if (lastHoveredPath != null) {
                    showTooltipPopup(lastHoveredPath);
                }
            });
            popupShowTimer.setRepeats(false);
        }
        
        if (popupHideTimer == null) {
            popupHideTimer = new javax.swing.Timer(POPUP_HIDE_DELAY, e -> {
                hideTooltipPopup();
            });
            popupHideTimer.setRepeats(false);
        }
        
        tooltipMouseMotionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!isTooltipMode) return;
                
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                
                if (path == null) {
                    scheduleHideTooltip();
                    lastHoveredPath = null;
                    return;
                }
                
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = node.getUserObject();
                
                if (userObject instanceof TreeNodeData) {
                    TreeNodeData data = (TreeNodeData) userObject;
                    
                    if (data.type == NodeType.POST) {
                        if (path.equals(lastHoveredPath)) {
                            popupHideTimer.stop();
                            return;
                        }
                        
                        // 保存鼠标屏幕位置
                        lastMouseScreenPoint = e.getLocationOnScreen();
                        lastHoveredPath = path;
                        popupHideTimer.stop();
                        popupShowTimer.restart();
                    } else {
                        scheduleHideTooltip();
                        lastHoveredPath = null;
                    }
                }
            }
        };
        
        tooltipMouseAdapter = new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (!isTooltipMode) return;
                if (isMouseOverPopup(e)) return;
                scheduleHideTooltip();
                lastHoveredPath = null;
            }
        };
        
        tree.addMouseMotionListener(tooltipMouseMotionAdapter);
        tree.addMouseListener(tooltipMouseAdapter);
    }
    
    private void removeTooltipListeners() {
        if (tooltipMouseMotionAdapter != null) {
            tree.removeMouseMotionListener(tooltipMouseMotionAdapter);
            tooltipMouseMotionAdapter = null;
        }
        if (tooltipMouseAdapter != null) {
            tree.removeMouseListener(tooltipMouseAdapter);
            tooltipMouseAdapter = null;
        }
    }
    
    private void showTooltipPopup(TreePath path) {
        hideTooltipPopup();
        
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        
        if (!(userObject instanceof TreeNodeData)) return;
        
        TreeNodeData data = (TreeNodeData) userObject;
        if (data.type != NodeType.POST) return;
        
        JPanel contentPanel = new JPanel(new BorderLayout());
        
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel titleLabel = new JLabel("<html><b>" + escapeHtml(data.label) + "</b> <span style='color:gray;'>" + escapeHtml(data.description) + "</span></html>");
        titlePanel.add(titleLabel, BorderLayout.CENTER);
        contentPanel.add(titlePanel, BorderLayout.NORTH);
        
        tooltipBrowser = new JBCefBrowser();
        tooltipBrowser.loadHTML(buildPreviewHtml(data));
        JComponent browserComponent = tooltipBrowser.getComponent();
        browserComponent.setPreferredSize(new Dimension(500, 350));
        contentPanel.add(browserComponent, BorderLayout.CENTER);
        
        MouseAdapter popupMouseListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                popupHideTimer.stop();
                popupShowTimer.stop();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                Component source = e.getComponent();
                Point p = e.getPoint();
                SwingUtilities.convertPointToScreen(p, source);
                if (currentPopup != null && !currentPopup.isDisposed()) {
                    Component popupContent = currentPopup.getContent();
                    if (popupContent != null) {
                        Point popupLocation = popupContent.getLocationOnScreen();
                        Rectangle popupBounds = new Rectangle(popupLocation, popupContent.getSize());
                        if (!popupBounds.contains(p)) {
                            scheduleHideTooltip();
                        }
                    }
                }
            }
        };
        
        contentPanel.addMouseListener(popupMouseListener);
        titlePanel.addMouseListener(popupMouseListener);
        browserComponent.addMouseListener(popupMouseListener);
        
        currentPopup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(contentPanel, null)
                .setResizable(true)
                .setMovable(true)
                .setRequestFocus(false)
                .setCancelOnClickOutside(false)
                .setCancelOnOtherWindowOpen(false)
                .setCancelOnWindowDeactivation(false)
                .createPopup();
        
        // 在鼠标位置显示（左下角为鼠标位置）
        if (lastMouseScreenPoint != null) {
            currentPopup.showInScreenCoordinates(tree, lastMouseScreenPoint);
        }
    }
    
    private void hideTooltipPopup() {
        if (currentPopup != null && !currentPopup.isDisposed()) {
            currentPopup.cancel();
            currentPopup = null;
        }
        if (tooltipBrowser != null) {
            tooltipBrowser.dispose();
            tooltipBrowser = null;
        }
    }
    
    private void scheduleHideTooltip() {
        if (popupShowTimer != null) popupShowTimer.stop();
        if (popupHideTimer != null) popupHideTimer.restart();
    }
    
    private boolean isMouseOverPopup(MouseEvent e) {
        if (currentPopup == null || currentPopup.isDisposed()) return false;
        try {
            Component popupContent = currentPopup.getContent();
            if (popupContent != null && popupContent.isShowing()) {
                Point mouseScreenPos = e.getLocationOnScreen();
                Point popupLocation = popupContent.getLocationOnScreen();
                Rectangle popupBounds = new Rectangle(popupLocation, popupContent.getSize());
                return popupBounds.contains(mouseScreenPos);
            }
        } catch (Exception ex) {}
        return false;
    }

    /**
     * 节点类型枚举
     */
    public enum NodeType {
        ALL_TOPICS,
        CATEGORY,
        TOPIC,
        POST,
        NOTIFICATION,
        LOAD_MORE,
        LOAD_MORE_TOPICS,  // 加载更多话题
        SEARCH_RESULTS,    // 搜索结果
        INFO
    }

    /**
     * 树节点数据类
     */
    public static class TreeNodeData {
        public final NodeType type;
        public final int id;
        public final String label;
        public final String description;
        public final String url;
        public final String fullContent;  // 完整内容，用于 tooltip 显示
        public final String htmlContent;  // HTML 内容，用于图片预览

        public TreeNodeData(NodeType type, int id, String label, String description) {
            this(type, id, label, description, null, null, null);
        }

        public TreeNodeData(NodeType type, int id, String label, String description, String url) {
            this(type, id, label, description, url, null, null);
        }

        public TreeNodeData(NodeType type, int id, String label, String description, String url, String fullContent) {
            this(type, id, label, description, url, fullContent, null);
        }

        public TreeNodeData(NodeType type, int id, String label, String description, String url, String fullContent, String htmlContent) {
            this.type = type;
            this.id = id;
            this.label = label;
            this.description = description;
            this.url = url;
            this.fullContent = fullContent;
            this.htmlContent = htmlContent;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * 自定义树节点渲染器 - 使用 ColoredTreeCellRenderer 实现透明背景
     */
    private static class LinuxDoTreeCellRenderer extends ColoredTreeCellRenderer {
        
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof TreeNodeData) {
                    TreeNodeData data = (TreeNodeData) userObject;

                    switch (data.type) {
                        case ALL_TOPICS:
                            setIcon(AllIcons.Nodes.Folder);
                            append(data.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                            append("  " + data.description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                            setToolTipText(data.label + " - " + data.description);
                            break;
                        case SEARCH_RESULTS:
                            setIcon(AllIcons.Actions.Search);
                            append(data.label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                            setToolTipText(LinuxDoBundle.message("node.searchResults"));
                            break;
                        case CATEGORY:
                            setIcon(AllIcons.Nodes.Folder);
                            append(data.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                            append("  " + data.description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                            setToolTipText(data.label + " (" + data.description + ")");
                            break;
                        case TOPIC:
                            setIcon(AllIcons.Nodes.Folder);
                            append(data.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                            append("  " + data.description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                            setToolTipText("<html><b>" + escapeHtml(data.label) + "</b><br/>" + escapeHtml(data.description) + "</html>");
                            break;
                        case POST:
                            // 不设置图标
                            append(data.label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                            append("  " + data.description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                            // POST节点使用JBPopup悬浮预览，不使用tooltip
                            setToolTipText(null);
                            break;
                        case NOTIFICATION:
                            setIcon(AllIcons.Toolwindows.Notifications);
                            append(data.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                            setToolTipText(data.label);
                            break;
                        case LOAD_MORE:
                            setIcon(AllIcons.General.Add);
                            append(data.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                            append("  " + data.description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                            setToolTipText(data.label + " " + data.description);
                            break;
                        case LOAD_MORE_TOPICS:
                            setIcon(AllIcons.General.Add);
                            append(data.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                            setToolTipText(LinuxDoBundle.message("node.loadMore"));
                            break;
                        case INFO:
                            // 不设置图标，避免加载中时显示蓝色 i 符号
                            append(data.label, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                            setToolTipText(data.label);
                            break;
                    }
                }
            }
        }
        
        private String escapeHtml(String text) {
            if (text == null) return "";
            return text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }
}
