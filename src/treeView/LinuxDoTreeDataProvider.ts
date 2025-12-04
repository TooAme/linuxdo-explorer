import * as vscode from 'vscode';
import { TreeItemNode, NodeType } from './TreeItemNode';
import { CategoryService } from '../services/CategoryService';
import { TopicService } from '../services/TopicService';
import { PostService } from '../services/PostService';
import { DISCOURSE_API } from '../constants/Config';
import type { Topic, Post, TopicDetail, Notification } from '../api/ApiTypes';

interface LinuxDoSettings {
  showImages: boolean;
  showEmoji: boolean;
  autoRefreshMinutes: number;
  ignoreLineBreaks: boolean;
  compactMode: boolean;
  topicFontSize: 'small' | 'medium' | 'large';
  grayscaleImages: boolean;
  showPostInfo: boolean;
}

const SETTINGS_KEY = 'linuxdo-settings';

export class LinuxDoTreeDataProvider implements vscode.TreeDataProvider<TreeItemNode> {
  private readonly _onDidChangeTreeData =
    new vscode.EventEmitter<TreeItemNode | undefined | null | void>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  // 存储每个话题的完整数据
  private readonly topicPostsData = new Map<number, {
    allPostIds: number[];
    loadedPosts: Post[];
    postsCount: number;
  }>();

  // 存储已读通知的 ID
  private readonly readNotificationIds = new Set<number>();

  // 存储当前打开的话题预览面板
  private currentTopicPanel: vscode.WebviewPanel | undefined;

  // 加载状态标志，防止重复点击
  private isLoadingPreview = false;

  constructor(
    private readonly categoryService: CategoryService,
    private readonly topicService: TopicService,
    private readonly postService: PostService,
    private readonly context: vscode.ExtensionContext
  ) { }

  refresh(): void {
    this._onDidChangeTreeData.fire();
  }

  refreshNode(node?: TreeItemNode): void {
    this._onDidChangeTreeData.fire(node);
  }

  getTreeItem(element: TreeItemNode): vscode.TreeItem {
    return element;
  }

  private getSettings(): LinuxDoSettings {
    const stored = this.context.globalState.get<
      Partial<LinuxDoSettings> & { ignoreSpaces?: boolean }
    >(SETTINGS_KEY);

    return {
      showImages: stored?.showImages ?? true,
      showEmoji: stored?.showEmoji ?? false,
      autoRefreshMinutes: stored?.autoRefreshMinutes ?? 0,
      ignoreLineBreaks: stored?.ignoreLineBreaks ?? false,
      // 兼容旧版 ignoreSpaces，映射到 compactMode
      compactMode: stored?.compactMode ?? stored?.ignoreSpaces ?? false,
      topicFontSize: stored?.topicFontSize ?? 'medium',
      grayscaleImages: stored?.grayscaleImages ?? false,
      showPostInfo: stored?.showPostInfo ?? true
    };
  }

  async getChildren(element?: TreeItemNode): Promise<TreeItemNode[]> {
    try {
      if (!element) {
        return this.getRootNodes();
      }

      switch (element.data.type) {
        case NodeType.ALL_TOPICS:
          return this.getAllTopicsNodes();
        case NodeType.CATEGORY:
          return this.getCategoryTopics(element.data.categoryId!);
        case NodeType.TOPIC:
          return this.getTopicPosts(element.data.topicId!);
        case NodeType.POST:
          return [];
        default:
          return [];
      }
    } catch (error: any) {
      console.error('[LinuxDoTreeDataProvider] 加载失败:', error);

      if (error.message?.includes('登录已过期') || error.message?.includes('请先登录')) {
        vscode.window.showWarningMessage(
          '登录已过期或未登录，请先登录',
          '登录'
        ).then(result => {
          if (result === '登录') {
            void vscode.commands.executeCommand('linuxdo.login');
          }
        });
      } else {
        vscode.window.showErrorMessage(
          `加载失败: ${error.message}`,
          '查看日志'
        ).then(result => {
          if (result === '查看日志') {
            void vscode.commands.executeCommand('workbench.action.toggleDevTools');
          }
        });
      }
      return [];
    }
  }

  private async getRootNodes(): Promise<TreeItemNode[]> {
    const nodes: TreeItemNode[] = [];

    // 获取未读通知并添加到顶部
    try {
      const apiClient = (this.postService as any).apiClient;
      const notifications: Notification[] = await apiClient.getNotifications();

      const unreadNotifications = notifications.filter(
        n => !n.read && !this.readNotificationIds.has(n.id)
      );

      for (const notification of unreadNotifications) {
        const notificationText = this.getNotificationText(notification);
        nodes.push(new TreeItemNode(
          {
            type: NodeType.NOTIFICATION,
            id: notification.id,
            notificationId: notification.id,
            topicId: notification.topic_id,
            label: notificationText,
            description: this.formatDate(notification.created_at),
            url: `https://linux.do/t/${notification.slug}/${notification.topic_id}/${notification.post_number}`
          },
          vscode.TreeItemCollapsibleState.None
        ));
      }
    } catch (error: any) {
      console.error('[LinuxDoTreeDataProvider] 获取通知失败:', error);
    }

    // "全部话题"节点
    nodes.push(new TreeItemNode(
      {
        type: NodeType.ALL_TOPICS,
        label: '全部',
        description: '最新话题'
      },
      vscode.TreeItemCollapsibleState.Collapsed
    ));

    // 分类节点
    try {
      const categories = await this.categoryService.getCategories();
      for (const category of categories) {
        nodes.push(new TreeItemNode(
          {
            type: NodeType.CATEGORY,
            id: category.id,
            categoryId: category.id,
            label: ` ${category.name}`,
            description: `x ${category.topic_count}`
          },
          vscode.TreeItemCollapsibleState.Collapsed
        ));
      }
    } catch (error: any) {
      if (!error.message?.includes('登录已过期')) {
        vscode.window.showErrorMessage(error.message);
      }
    }

    return nodes;
  }

  private getNotificationText(notification: Notification): string {
    const username = notification.data.display_username || notification.data.original_username;
    const topicTitle = notification.data.topic_title;

    switch (notification.notification_type) {
      case 1:
        return `${username} 在 "${topicTitle}" 中提到了你`;
      case 2:
        return `${username} 回复了你在 "${topicTitle}" 的帖子`;
      case 5:
        return `${username} 回复了 "${topicTitle}"`;
      case 6:
        return `${username} 点赞了你的帖子`;
      case 9:
        return `${username} 回复了你`;
      default:
        return `来自 ${username} 的通知: ${topicTitle}`;
    }
  }

  async markNotificationAsRead(notificationId: number): Promise<void> {
    try {
      const apiClient = (this.postService as any).apiClient;
      await apiClient.markNotificationAsRead(notificationId);

      this.readNotificationIds.add(notificationId);
      this.refresh();
    } catch (error: any) {
      console.error('[LinuxDoTreeDataProvider] 标记通知已读失败:', error);
    }
  }

  private async getAllTopicsNodes(): Promise<TreeItemNode[]> {
    const topics = await this.topicService.getLatestTopics();

    if (topics.length === 0) {
      vscode.window.showInformationMessage('没有找到话题');
    }

    return this.createTopicNodes(topics);
  }

  private async getCategoryTopics(categoryId: number): Promise<TreeItemNode[]> {
    const topics = await this.topicService.getCategoryTopics(categoryId);
    return this.createTopicNodes(topics);
  }

  /**
   * 在编辑器右侧打开话题预览，展示完整帖子内容（含回复和图片）。
   * 如果已有打开的预览面板，则复用该面板。
   */
  async openTopicPreview(topicId: number, title: string): Promise<void> {
    // 如果正在加载，忽略点击
    if (this.isLoadingPreview) {
      return;
    }

    try {
      this.isLoadingPreview = true;
      vscode.window.setStatusBarMessage('正在加载话题...', 10000);

      const posts = await this.getAllPostsForTopic(topicId);

      if (!posts || posts.length === 0) {
        vscode.window.showInformationMessage('没有找到该话题的内容');
        return;
      }

      // 如果已有面板，则复用；否则创建新面板
      if (this.currentTopicPanel) {
        // 复用现有面板，更新标题和内容
        this.currentTopicPanel.title = `Linux.do - ${title}`;
        this.currentTopicPanel.webview.html = this.buildTopicHtml(title, posts);
        // 确保面板可见
        this.currentTopicPanel.reveal(vscode.ViewColumn.Beside);
      } else {
        // 创建新面板
        const panel = vscode.window.createWebviewPanel(
          'linuxdoTopicPreview',
          `Linux.do - ${title}`,
          vscode.ViewColumn.Beside,
          {
            enableScripts: false,
            retainContextWhenHidden: true
          }
        );

        panel.webview.html = this.buildTopicHtml(title, posts);

        // 保存面板引用
        this.currentTopicPanel = panel;

        // 当面板关闭时清除引用
        panel.onDidDispose(() => {
          this.currentTopicPanel = undefined;
        });
      }

      vscode.window.setStatusBarMessage('话题加载完成', 2000);
    } catch (error: any) {
      console.error('[LinuxDoTreeDataProvider] 打开话题预览失败:', error);
      vscode.window.showErrorMessage(`打开话题预览失败: ${error.message}`);
    } finally {
      this.isLoadingPreview = false;
    }
  }

  async getTopicPosts(topicId: number): Promise<TreeItemNode[]> {
    // 如果还没有加载过这个话题的数据，先加载
    if (!this.topicPostsData.has(topicId)) {
      const apiClient = (this.postService as any).apiClient;
      const topicDetail: TopicDetail = await apiClient.getTopic(topicId);

      this.topicPostsData.set(topicId, {
        allPostIds: topicDetail.post_stream.stream || [],
        loadedPosts: topicDetail.post_stream.posts || [],
        postsCount: topicDetail.posts_count
      });
    }

    const topicData = this.topicPostsData.get(topicId)!;
    const loadedPosts = topicData.loadedPosts;
    const postsCount = topicData.postsCount;

    const sortedPosts = loadedPosts.slice().sort((a, b) => a.post_number - b.post_number);

    const postNodes = sortedPosts.map(post => {
      const previewText = this.extractTextFromHtml(post.cooked);
      const tooltip = this.buildPostTooltip(post);

      return new TreeItemNode(
        {
          type: NodeType.POST,
          id: post.id,
          postId: post.id,
          topicId: topicId,
          label: ` ${post.username}`,
          description: `#${post.post_number} - ${previewText}`,
          tooltip
        },
        vscode.TreeItemCollapsibleState.None
      );
    });

    if (loadedPosts.length < postsCount) {
      postNodes.push(new TreeItemNode(
        {
          type: NodeType.LOAD_MORE,
          topicId: topicId,
          label: '加载更多',
          description: `+${postsCount - loadedPosts.length}`
        },
        vscode.TreeItemCollapsibleState.None
      ));
    }

    return postNodes;
  }

  /**
   * 加载更多回复
   */
  async loadMorePosts(topicId: number): Promise<void> {
    const topicData = this.topicPostsData.get(topicId);
    if (!topicData) {
      console.error(`[LinuxDoTreeDataProvider] 未找到话题 ${topicId} 的数据`);
      return;
    }

    const allPostIds = topicData.allPostIds;
    const loadedPosts = topicData.loadedPosts;

    const loadedPostIds = new Set(loadedPosts.map(p => p.id));

    const unloadedPostIds = allPostIds.filter(id => !loadedPostIds.has(id));
    if (unloadedPostIds.length === 0) {
      vscode.window.showInformationMessage('没有更多回复了');
      return;
    }

    const nextBatchIds = unloadedPostIds.slice(0, 20);

    try {
      const apiClient = (this.postService as any).apiClient;
      const morePosts: Post[] = await apiClient.loadMoreTopicPosts(topicId, nextBatchIds);

      const newPosts = morePosts.filter(post => !loadedPostIds.has(post.id));
      topicData.loadedPosts = [...topicData.loadedPosts, ...newPosts];

      this._onDidChangeTreeData.fire();
    } catch (error: any) {
      console.error('[LinuxDoTreeDataProvider] 加载更多回复失败:', error);
      vscode.window.showErrorMessage(`加载更多回复失败: ${error.message}`);
    }
  }

  /**
   * 获取某个话题的全部回复（包含首帖和后续回复）。
   * 优先使用 /t/{id}.json 返回的 posts，再用 /posts 接口补齐未加载部分。
   */
  private async getAllPostsForTopic(topicId: number): Promise<Post[]> {
    const apiClient = (this.postService as any).apiClient;
    const topicDetail: TopicDetail = await apiClient.getTopic(topicId);

    const allPostIds = topicDetail.post_stream.stream || [];
    const posts: Post[] = [...(topicDetail.post_stream.posts || [])];
    const postsCount = topicDetail.posts_count;

    const loadedPostIds = new Set(posts.map(p => p.id));

    if (allPostIds.length === 0 || posts.length >= postsCount) {
      return posts.sort((a, b) => a.post_number - b.post_number);
    }

    const unloadedPostIds = allPostIds.filter(id => !loadedPostIds.has(id));
    const batchSize = 50;

    for (let i = 0; i < unloadedPostIds.length; i += batchSize) {
      const batchIds = unloadedPostIds.slice(i, i + batchSize);
      try {
        const morePosts: Post[] = await apiClient.loadMoreTopicPosts(topicId, batchIds);
        for (const post of morePosts) {
          if (!loadedPostIds.has(post.id)) {
            loadedPostIds.add(post.id);
            posts.push(post);
          }
        }
      } catch (error: any) {
        console.error('[LinuxDoTreeDataProvider] 加载全部回复时部分批次失败:', error);
      }
    }

    return posts.sort((a, b) => a.post_number - b.post_number);
  }

  private createTopicNodes(topics: Topic[]): TreeItemNode[] {
    return topics.map(topic => {
      const url = `https://linux.do/t/${topic.slug}/${topic.id}`;
      return new TreeItemNode(
        {
          type: NodeType.TOPIC,
          id: topic.id,
          topicId: topic.id,
          label: topic.title,
          description: `V. ${topic.views} R. ${topic.reply_count}`,
          url: url
        },
        vscode.TreeItemCollapsibleState.Collapsed
      );
    });
  }

  private extractTextFromHtml(html: string): string {
    const settings = this.getSettings();
    let result = html;

    if (!settings.showEmoji) {
      result = this.removeEmojiSyntax(result);
    }

    result = result
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, ' ')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"');

    result = this.applyTextFiltersForPlainText(result, settings);
    return result.trim();
  }

  private buildTopicHtml(title: string, posts: Post[]): string {
    const settings = this.getSettings();

    const escapeHtml = (value: string): string =>
      value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

    const postSections = posts
      .map(post => {
        const createdAt = new Date(post.created_at).toLocaleString('zh-CN');
        let cookedHtml = post.cooked;

        if (!settings.showEmoji) {
          cookedHtml = this.removeEmojiSyntax(cookedHtml);
        }
        if (!settings.showImages) {
          cookedHtml = cookedHtml.replace(/<img[^>]*>/gi, '');
        } else {
          cookedHtml = this.fixImageUrls(cookedHtml);
        }

        cookedHtml = this.applyTextFiltersForHtml(cookedHtml, settings);

        // 根据设置决定是否显示帖子信息
        const postHeader = settings.showPostInfo ? `
  <header class="post-header">
    <span class="post-number">#${post.post_number}</span>
    <span class="post-username">@${escapeHtml(post.username)}</span>
    <span class="post-date">${escapeHtml(createdAt)}</span>
  </header>` : '';

        return `
<article class="post">
  ${postHeader}
  <section class="post-body">
    ${cookedHtml}
  </section>
</article>`;
      })
      .join('\n');

    const escapedTitle = escapeHtml(title);

    let bodyFontSizeCss = 'var(--vscode-editor-font-size, 13px)';
    switch (settings.topicFontSize) {
      case 'small':
        bodyFontSizeCss = 'calc(var(--vscode-editor-font-size, 13px) * 0.9)';
        break;
      case 'large':
        bodyFontSizeCss = 'calc(var(--vscode-editor-font-size, 13px) * 1.2)';
        break;
      case 'medium':
      default:
        bodyFontSizeCss = 'var(--vscode-editor-font-size, 13px)';
        break;
    }

    const compactClass = settings.compactMode ? 'compact' : '';
    const grayscaleClass = settings.grayscaleImages ? 'grayscale' : '';
    const bodyClasses = [compactClass, grayscaleClass].filter(Boolean).join(' ');

    return `<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${escapedTitle} - Linux.do</title>
    <style>
      :root {
        color-scheme: light dark;
      }
      body {
        margin: 0;
        padding: 16px 24px 40px;
        font-family: var(--vscode-editor-font-family, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif);
        font-size: ${bodyFontSizeCss};
        line-height: 1.6;
        color: var(--vscode-editor-foreground, #ddd);
        background-color: var(--vscode-editor-background, #1e1e1e);
      }
      a {
        color: var(--vscode-textLink-foreground, #4ea6ea);
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
        background-color: var(--vscode-editorCodeLens-foreground, #2d2d2d);
        padding: 8px 12px;
        border-radius: 4px;
        overflow-x: auto;
        font-family: var(--vscode-editor-font-family, Consolas, "Courier New", monospace);
      }
      code {
        font-family: var(--vscode-editor-font-family, Consolas, "Courier New", monospace);
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
        color: var(--vscode-descriptionForeground, #999);
        font-size: 0.9rem;
      }
      .post {
        border-top: 1px solid var(--vscode-editorIndentGuide-activeBackground, #444);
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
        color: var(--vscode-descriptionForeground, #aaa);
        font-size: 0.85rem;
      }
      .post-number {
        font-weight: 600;
        color: var(--vscode-editorLineNumber-activeForeground, #c5c5c5);
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

      /* 紧凑模式：通过 CSS 减少空白，不改动原始内容 */
      body.compact {
        line-height: 1.4;
      }
      body.compact .topic-subtitle {
        margin-bottom: 12px;
      }
      body.compact .post {
        padding-top: 8px;
        margin-top: 8px;
      }
      body.compact .post-header {
        margin-bottom: 4px;
      }
      body.compact .post-body p {
        margin-top: 4px;
        margin-bottom: 4px;
      }
      body.compact img {
        margin: 4px 0;
      }
      /* 隐藏图片下方的 meta 元素 */
      img + .meta,
      .lightbox-wrapper .meta,
      div.meta {
        display: none !important;
      }
      /* 黑白模式：图片灰度滤镜，悬停恢复彩色 */
      body.grayscale img {
        filter: gray;
        -webkit-filter: grayscale(1);
        -webkit-transition: all .5s ease-in-out;
        transition: all .5s ease-in-out;
      }
      body.grayscale img:hover {
        filter: none;
        -webkit-filter: grayscale(0);
        -webkit-transform: scale(1.01);
        transform: scale(1.01);
      }
    </style>
  </head>
  <body class="${bodyClasses}">
    <div class="container">
      <h1 class="topic-title">${escapedTitle}</h1>
      <p class="topic-subtitle">共 ${posts.length} 条帖子</p>
      ${postSections}
    </div>
  </body>
</html>`;
  }

  /**
   * 为回复节点构建 tooltip，支持根据设置显示/隐藏图片和表情。
   */
  private buildPostTooltip(post: Post): vscode.MarkdownString {
    const settings = this.getSettings();
    let htmlContent = post.cooked;

    if (!settings.showEmoji) {
      htmlContent = this.removeEmojiSyntax(htmlContent);
    }
    if (!settings.showImages) {
      htmlContent = htmlContent.replace(/<img[^>]*>/gi, '');
    } else {
      htmlContent = this.fixImageUrls(htmlContent);
      // 如果启用黑白模式，在 tooltip 中也添加灰度样式
      if (settings.grayscaleImages) {
        // 使用更完整的内联样式确保兼容性
        htmlContent = htmlContent.replace(
          /<img([^>]*)>/gi,
          '<img$1 style="filter: grayscale(1); -webkit-filter: grayscale(1);">'
        );
      }
    }

    htmlContent = this.applyTextFiltersForHtml(htmlContent, settings);

    const headerHtml = `<strong>#${post.post_number} · @${post.username}</strong>`;
    const html = `${headerHtml}<br/><br/>${htmlContent}`;

    const md = new vscode.MarkdownString(html);
    md.supportHtml = true;
    return md;
  }

  private fixImageUrls(html: string): string {
    let result = html;
    const baseUrl = DISCOURSE_API.BASE_URL.replace(/\/+$/, '');

    // src="/path"
    result = result.replace(/src=\"\/(?!\/)([^\"]*)\"/g, (_match, path) => {
      const url = `${baseUrl}/${path}`;
      return `src="${url}"`;
    });

    // src='/path'
    result = result.replace(/src=\'\/(?!\/)([^\']*)\'/g, (_match, path) => {
      const url = `${baseUrl}/${path}`;
      return `src='${url}'`;
    });

    // src="//host/path" -> 添加 https:
    result = result.replace(/src=\"\/\/([^\"]*)\"/g, (_match, hostPath) => {
      const url = `https://${hostPath}`;
      return `src="${url}"`;
    });
    result = result.replace(/src=\'\/\/([^\']*)\'/g, (_match, hostPath) => {
      const url = `https://${hostPath}`;
      return `src='${url}'`;
    });

    return result;
  }

  private removeEmojiSyntax(input: string): string {
    let result = input;
    // 移除 Discourse emoji 图片
    result = result.replace(/<img[^>]*class=\"[^\"]*emoji[^\"]*\"[^>]*>/gi, '');
    // 移除 :xxx: 语法
    result = result.replace(/:[a-zA-Z0-9_+\-]+:/g, '');
    return result;
  }

  private applyTextFiltersForHtml(html: string, settings: LinuxDoSettings): string {
    let result = html;

    if (settings.ignoreLineBreaks) {
      result = result.replace(/(\r\n|\n|\r)/g, '');
      result = result.replace(/<br\s*\/?>/gi, '');
    }

    // 紧凑模式不通过修改 HTML 文本，
    // 仅在话题说明页中通过 CSS 调整样式。
    return result;
  }

  private applyTextFiltersForPlainText(text: string, settings: LinuxDoSettings): string {
    let result = text;

    if (settings.ignoreLineBreaks) {
      result = result.replace(/(\r\n|\n|\r)/g, '');
    }

    // 紧凑模式只影响话题说明页样式，不修改样直接显示的文本
    return result;
  }

  private formatDate(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));

    if (days === 0) {
      return '今天';
    }
    if (days === 1) {
      return '昨天';
    }
    if (days < 7) {
      return `${days}天前`;
    }
    return date.toLocaleDateString('zh-CN');
  }
}
