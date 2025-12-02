import * as vscode from 'vscode';
import { TreeItemNode, NodeType, NodeData } from './TreeItemNode';
import { CategoryService } from '../services/CategoryService';
import { TopicService } from '../services/TopicService';
import { PostService } from '../services/PostService';
import type { Topic, Post, TopicDetail, Notification } from '../api/ApiTypes';

export class LinuxDoTreeDataProvider implements vscode.TreeDataProvider<TreeItemNode> {
  private _onDidChangeTreeData: vscode.EventEmitter<TreeItemNode | undefined | null | void>;
  readonly onDidChangeTreeData: vscode.Event<TreeItemNode | undefined | null | void>;

  // 存储每个话题的完整数据
  private topicPostsData: Map<number, {
    allPostIds: number[];
    loadedPosts: Post[];
    postsCount: number;
  }> = new Map();

  // 存储已读通知的 ID
  private readNotificationIds: Set<number> = new Set();

  constructor(
    private categoryService: CategoryService,
    private topicService: TopicService,
    private postService: PostService
  ) {
    this._onDidChangeTreeData = new vscode.EventEmitter<TreeItemNode | undefined | null | void>();
    this.onDidChangeTreeData = this._onDidChangeTreeData.event;
  }

  refresh(): void {
    this._onDidChangeTreeData.fire();
  }

  refreshNode(node?: TreeItemNode): void {
    this._onDidChangeTreeData.fire(node);
  }

  getTreeItem(element: TreeItemNode): vscode.TreeItem {
    return element;
  }

  async getChildren(element?: TreeItemNode): Promise<TreeItemNode[]> {
    try {
      if (!element) {
        return this.getRootNodes();
      }

      console.log(`[LinuxDoTreeDataProvider] 加载节点类型: ${element.data.type}`);

      switch (element.data.type) {
        case NodeType.ALL_TOPICS:
          console.log('[LinuxDoTreeDataProvider] 加载全部话题...');
          return this.getAllTopicsNodes();
        case NodeType.CATEGORY:
          console.log(`[LinuxDoTreeDataProvider] 加载分类 ${element.data.categoryId} 的话题...`);
          return this.getCategoryTopics(element.data.categoryId!);
        case NodeType.TOPIC:
          console.log(`[LinuxDoTreeDataProvider] 加载话题 ${element.data.topicId} 的回复...`);
          return this.getTopicPosts(element.data.topicId!);
        case NodeType.POST:
          return [];
        default:
          return [];
      }
    } catch (error: any) {
      console.error(`[LinuxDoTreeDataProvider] 加载失败:`, error);

      if (error.message.includes('登录已过期') || error.message.includes('请先登录')) {
        vscode.window.showWarningMessage('登录已过期或未登录，请先登录', '登录').then(result => {
          if (result === '登录') {
            vscode.commands.executeCommand('linuxdo.login');
          }
        });
      } else {
        vscode.window.showErrorMessage(`加载失败: ${error.message}`, '查看日志').then(result => {
          if (result === '查看日志') {
            vscode.commands.executeCommand('workbench.action.toggleDevTools');
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

      // 过滤掉已读通知
      const unreadNotifications = notifications.filter(n => !n.read && !this.readNotificationIds.has(n.id));

      console.log(`[LinuxDoTreeDataProvider] 获取到 ${notifications.length} 条通知，未读 ${unreadNotifications.length} 条`);

      // 添加未读通知节点
      for (const notification of unreadNotifications) {
        const notificationText = this.getNotificationText(notification);
        nodes.push(new TreeItemNode(
          {
            type: NodeType.NOTIFICATION,
            id: notification.id,
            notificationId: notification.id,
            topicId: notification.topic_id,
            label: `${notificationText}`,
            description: this.formatDate(notification.created_at),
            url: `https://linux.do/t/${notification.slug}/${notification.topic_id}/${notification.post_number}`
          },
          vscode.TreeItemCollapsibleState.None
        ));
      }
    } catch (error: any) {
      console.error('[LinuxDoTreeDataProvider] 获取通知失败:', error);
    }

    nodes.push(new TreeItemNode(
      {
        type: NodeType.ALL_TOPICS,
        label: '全部',
        description: '最新话题'
      },
      vscode.TreeItemCollapsibleState.Collapsed
    ));

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
      if (!error.message.includes('登录已过期')) {
        vscode.window.showErrorMessage(`${error.message}`);
      }
    }

    return nodes;
  }

  /**
   * 将通知转换为可读文本
   */
  private getNotificationText(notification: Notification): string {
    const username = notification.data.display_username || notification.data.original_username;
    const topicTitle = notification.data.topic_title;

    // notification_type: 1=提及 2=回复 6=点赞 等
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

  /**
   * 标记通知为已读
   */
  async markNotificationAsRead(notificationId: number): Promise<void> {
    try {
      const apiClient = (this.postService as any).apiClient;
      await apiClient.markNotificationAsRead(notificationId);

      // 添加到已读集合
      this.readNotificationIds.add(notificationId);

      console.log(`[LinuxDoTreeDataProvider] 通知 ${notificationId} 已标记为已读`);

      // 刷新视图
      this.refresh();
    } catch (error: any) {
      console.error('[LinuxDoTreeDataProvider] 标记通知已读失败:', error);
    }
  }

  private async getAllTopicsNodes(): Promise<TreeItemNode[]> {
    const topics = await this.topicService.getLatestTopics();
    console.log(`[LinuxDoTreeDataProvider] 获取到 ${topics.length} 个话题`);

    if (topics.length === 0) {
      vscode.window.showInformationMessage('没有找到话题');
    }

    return this.createTopicNodes(topics);
  }

  private async getCategoryTopics(categoryId: number): Promise<TreeItemNode[]> {
    const topics = await this.topicService.getCategoryTopics(categoryId);
    return this.createTopicNodes(topics);
  }

  private async getTopicPosts(topicId: number): Promise<TreeItemNode[]> {
    // 如果还没有加载过这个话题的数据，先加载
    if (!this.topicPostsData.has(topicId)) {
      // 通过 postService 获取话题详情（它会调用 getTopic）
      const apiClient = (this.postService as any).apiClient;
      const topicDetail: TopicDetail = await apiClient.getTopic(topicId);

      console.log(`[LinuxDoTreeDataProvider] 首次加载话题 ${topicId}: 总回复数=${topicDetail.posts_count}, stream长度=${topicDetail.post_stream.stream?.length || 0}`);

      // 存储话题的完整数据
      this.topicPostsData.set(topicId, {
        allPostIds: topicDetail.post_stream.stream || [],
        loadedPosts: topicDetail.post_stream.posts || [],
        postsCount: topicDetail.posts_count
      });
    }

    const topicData = this.topicPostsData.get(topicId)!;
    const loadedPosts = topicData.loadedPosts;
    const postsCount = topicData.postsCount;

    console.log(`[LinuxDoTreeDataProvider] 话题 ${topicId}: 已加载=${loadedPosts.length}, 总数=${postsCount}`);

    // 按顺序排序
    const sortedPosts = loadedPosts.sort((a, b) => a.post_number - b.post_number);

    // 创建回复节点
    const postNodes = sortedPosts.map(post => {
      const previewText = this.extractTextFromHtml(post.cooked).substring(0, 50);
      return new TreeItemNode(
        {
          type: NodeType.POST,
          id: post.id,
          postId: post.id,
          topicId: topicId,
          label: ` ${post.username}`,
          description: `#${post.post_number} - ${previewText}}`
        },
        vscode.TreeItemCollapsibleState.None
      );
    });

    // 如果还有更多回复，添加"加载更多"节点
    if (loadedPosts.length < postsCount) {
      console.log(`[LinuxDoTreeDataProvider] 添加"加载更多"节点，还有 ${postsCount - loadedPosts.length} 条回复`);
      postNodes.push(new TreeItemNode(
        {
          type: NodeType.LOAD_MORE,
          topicId: topicId,
          label: '加载更多',
          description: `+${postsCount - loadedPosts.length}`
        },
        vscode.TreeItemCollapsibleState.None
      ));
    } else {
      console.log(`[LinuxDoTreeDataProvider] 没有更多回复`);
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

    // 获取已加载的 post ID 集合
    const loadedPostIds = new Set(loadedPosts.map(p => p.id));

    console.log(`[LinuxDoTreeDataProvider] 话题 ${topicId}: 所有ID数=${allPostIds.length}, 已加载=${loadedPostIds.size}`);
    console.log(`[LinuxDoTreeDataProvider] 已加载的ID:`, Array.from(loadedPostIds).slice(0, 5), '...');

    // 找出还未加载的 post ID
    const unloadedPostIds = allPostIds.filter(id => !loadedPostIds.has(id));

    if (unloadedPostIds.length === 0) {
      console.log(`[LinuxDoTreeDataProvider] 没有更多未加载的回复`);
      vscode.window.showInformationMessage('没有更多回复了');
      return;
    }

    // 获取下一批20个未加载的 post ID
    const nextBatchIds = unloadedPostIds.slice(0, 20);

    console.log(`[LinuxDoTreeDataProvider] 将加载下一批 ${nextBatchIds.length} 条回复，ID:`, nextBatchIds.slice(0, 5), '...');

    try {
      // 调用 API 加载更多回复
      const apiClient = (this.postService as any).apiClient;
      const morePosts: Post[] = await apiClient.loadMoreTopicPosts(topicId, nextBatchIds);

      console.log(`[LinuxDoTreeDataProvider] API 返回 ${morePosts.length} 条回复`);
      console.log(`[LinuxDoTreeDataProvider] 返回的回复 ID:`, morePosts.map(p => p.id).slice(0, 5), '...');

      // 过滤掉已经存在的回复（防止重复）
      const newPosts = morePosts.filter(post => !loadedPostIds.has(post.id));

      console.log(`[LinuxDoTreeDataProvider] 过滤后有 ${newPosts.length} 条新回复`);

      // 将新加载的回复添加到已加载列表中
      topicData.loadedPosts = [...topicData.loadedPosts, ...newPosts];

      console.log(`[LinuxDoTreeDataProvider] 成功加载，当前总数=${topicData.loadedPosts.length}`);

      // 刷新树视图
      this._onDidChangeTreeData.fire();
    } catch (error: any) {
      console.error(`[LinuxDoTreeDataProvider] 加载更多回复失败:`, error);
      vscode.window.showErrorMessage(`加载更多回复失败: ${error.message}`);
    }
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
    return html
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, ' ')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"')
      .trim();
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
