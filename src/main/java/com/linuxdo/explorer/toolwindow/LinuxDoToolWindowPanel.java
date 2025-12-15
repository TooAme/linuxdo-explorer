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
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.linuxdo.explorer.api.DiscourseApiClient;
import com.linuxdo.explorer.model.*;
import com.linuxdo.explorer.settings.LinuxDoSettings;
import com.linuxdo.explorer.settings.LinuxDoSettingsConfigurable;
import com.linuxdo.explorer.ui.TopicPreviewPanel;
import com.linuxdo.explorer.util.LinuxDoBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    private Timer autoRefreshTimer;

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
                if (e.getClickCount() == 2) {
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

        // 设置内容
        JBScrollPane scrollPane = new JBScrollPane(tree);
        setContent(scrollPane);

        // 初始加载
        refreshData();

        // 设置自动刷新
        setupAutoRefresh();
        
        // 设置伪装模式监听器
        setupDisguiseMode(scrollPane);
    }
    
    // 伪装模式相关变量
    private boolean isDisguised = false;
    private DefaultTreeModel originalModel;
    
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
        return toolbar.getComponent();
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

                        // 添加未读通知
                        for (com.linuxdo.explorer.model.Notification notification : notifications) {
                            if (!notification.isRead() && !readNotificationIds.contains(notification.getId())) {
                                DefaultMutableTreeNode notifNode = new DefaultMutableTreeNode(
                                        new TreeNodeData(NodeType.NOTIFICATION, notification.getId(),
                                                notification.getDisplayText(), notification.getUrl())
                                );
                                rootNode.add(notifNode);
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
        // 添加加载中提示
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode(
                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.loading"), "")
        );
        parentNode.add(loadingNode);
        treeModel.nodeStructureChanged(parentNode);

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
        // 添加加载中提示
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode(
                new TreeNodeData(NodeType.INFO, 0, LinuxDoBundle.message("node.loading"), "")
        );
        parentNode.add(loadingNode);
        treeModel.nodeStructureChanged(parentNode);

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

        List<Integer> nextBatch = unloadedIds.subList(0, Math.min(20, unloadedIds.size()));

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
     * 节点类型枚举
     */
    public enum NodeType {
        ALL_TOPICS,
        CATEGORY,
        TOPIC,
        POST,
        NOTIFICATION,
        LOAD_MORE,
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
                            // 根据设置决定是否显示图片预览
                            setToolTipText(buildPostTooltip(data));
                            break;
                        case NOTIFICATION:
                            setIcon(AllIcons.Toolwindows.Notifications);
                            append(data.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                            setToolTipText(data.label);
                            break;
                        case LOAD_MORE:
                            setIcon(AllIcons.General.ChevronDown);
                            append(data.label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                            append("  " + data.description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                            setToolTipText(data.label + " " + data.description);
                            break;
                        case INFO:
                            setIcon(AllIcons.General.Information);
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
        
        /**
         * 构建帖子的 tooltip
         */
        private String buildPostTooltip(TreeNodeData data) {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><div style='max-width:500px;'>");
            
            // 显示 meta 信息
            sb.append("<b>").append(escapeHtml(data.label)).append("</b>");
            if (data.description != null && !data.description.isEmpty()) {
                sb.append(" <span style='color:gray;'>").append(escapeHtml(data.description)).append("</span>");
            }
            sb.append("<hr/>");
            
            // 只显示纯文本内容
            String textContent = data.fullContent != null ? data.fullContent : "";
            sb.append("<pre style='white-space:pre-wrap;margin:0;'>").append(escapeHtml(textContent)).append("</pre>");
            
            sb.append("</div></html>");
            return sb.toString();
        }
    }
}
