import * as vscode from 'vscode';
import { LinuxDoTreeDataProvider } from './treeView/LinuxDoTreeDataProvider';
import { DiscourseApiClient } from './api/DiscourseApiClient';
import { AuthenticationManager } from './auth/AuthenticationManager';
import { CookieStorage } from './auth/CookieStorage';
import { CategoryService } from './services/CategoryService';
import { TopicService } from './services/TopicService';
import { PostService } from './services/PostService';
import { TreeItemNode } from './treeView/TreeItemNode';

export async function activate(context: vscode.ExtensionContext) {
  console.log('Linux.do Explorer 插件已激活');

  const cookieStorage = new CookieStorage(context.secrets);

  const apiClient = new DiscourseApiClient(
    async () => {
      return await cookieStorage.getCookie();
    },
    async () => {
      return await cookieStorage.getUserAgent();
    }
  );

  const authManager = new AuthenticationManager(context, cookieStorage, apiClient);

  const categoryService = new CategoryService(apiClient);
  const topicService = new TopicService(apiClient);
  const postService = new PostService(apiClient);

  const treeDataProvider = new LinuxDoTreeDataProvider(
    categoryService,
    topicService,
    postService,
    context
  );

  const treeView = vscode.window.createTreeView('linuxdoExplorer', {
    treeDataProvider,
    showCollapseAll: true
  });

  let autoRefreshTimer: NodeJS.Timeout | undefined;

  async function applyAutoRefreshFromSettings() {
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer);
      autoRefreshTimer = undefined;
    }

    const settings = context.globalState.get<{
      showImages?: boolean;
      showEmoji?: boolean;
      autoRefreshMinutes?: number;
      ignoreLineBreaks?: boolean;
      compactMode?: boolean;
      // å…¼å®¹æ—§ç‰ˆæœ¬çš„ ignoreSpaces
      ignoreSpaces?: boolean;
      topicFontSize?: string;
    }>('linuxdo-settings') ?? {};

    const minutes = settings.autoRefreshMinutes ?? 0;
    if (!minutes || minutes <= 0) {
      return;
    }

    const intervalMs = minutes * 60 * 1000;
    autoRefreshTimer = setInterval(() => {
      treeDataProvider.refresh();
    }, intervalMs);
  }

  context.subscriptions.push(
    vscode.commands.registerCommand('linuxdo.updateAutoRefresh', async () => {
      await applyAutoRefreshFromSettings();
    }),
    vscode.commands.registerCommand('linuxdo.refresh', () => {
      vscode.window.showInformationMessage('正在刷新...');
      treeDataProvider.refresh();
    }),

    vscode.commands.registerCommand('linuxdo.login', async () => {
      await authManager.showLoginWebview();
    }),

    vscode.commands.registerCommand('linuxdo.logout', async () => {
      const result = await vscode.window.showWarningMessage(
        '确定要退出登录吗？',
        '确定',
        '取消'
      );
      if (result === '确定') {
        await cookieStorage.deleteCookie();
        vscode.window.showInformationMessage('已退出登录');
        treeDataProvider.refresh();
      }
    }),

    vscode.commands.registerCommand('linuxdo.openInBrowser', (node: TreeItemNode) => {
      if (node.data.url) {
        vscode.env.openExternal(vscode.Uri.parse(node.data.url));
      } else if (node.data.topicId) {
        const url = `https://linux.do/t/${node.data.topicId}`;
        vscode.env.openExternal(vscode.Uri.parse(url));
      }
    }),

    vscode.commands.registerCommand('linuxdo.openTopicPreview', async (node: TreeItemNode) => {
      if (!node.data.topicId) {
        vscode.window.showErrorMessage('无法获取话题 ID');
        return;
      }

      await treeDataProvider.openTopicPreview(node.data.topicId, node.data.label);
    }),

    vscode.commands.registerCommand('linuxdo.replyToTopic', async (node: TreeItemNode) => {
      if (!node.data.topicId) {
        vscode.window.showErrorMessage('无法获取话题ID');
        return;
      }

      // 判断是回复话题还是回复回复
      const isReplyToPost = !!node.data.postId;
      const promptText = isReplyToPost ? '请输入回复内容' : '请输入回复内容';

      const replyContent = await vscode.window.showInputBox({
        prompt: promptText,
        placeHolder: '输入你的回复...',
        validateInput: (value) => {
          if (!value || value.trim().length === 0) {
            return '回复内容不能为空';
          }
          return null;
        }
      });

      if (replyContent) {
        try {
          await apiClient.createReply(node.data.topicId, replyContent);
          vscode.window.showInformationMessage('回复成功!');
          treeDataProvider.refresh();
        } catch (error: any) {
          vscode.window.showErrorMessage(`回复失败: ${error.message}`);
        }
      }
    }),

    vscode.commands.registerCommand('linuxdo.likeTopic', async (node: TreeItemNode) => {
      try {
        let postId: number;

        // 如果是回复节点,直接使用 postId
        if (node.data.postId) {
          postId = node.data.postId;
        }
        // 如果是话题节点,获取第一个帖子ID
        else if (node.data.topicId) {
          const posts = await apiClient.getTopicPosts(node.data.topicId);
          if (posts.length === 0) {
            vscode.window.showErrorMessage('无法找到帖子');
            return;
          }
          postId = posts[0].id;
        }
        else {
          vscode.window.showErrorMessage('无法获取帖子ID');
          return;
        }

        await apiClient.likePost(postId);
        vscode.window.showInformationMessage('点赞成功');
      } catch (error: any) {
        vscode.window.showErrorMessage(`点赞失败: ${error.message}`);
      }
    }),

    vscode.commands.registerCommand('linuxdo.refreshNode', (node: TreeItemNode) => {
      vscode.window.showInformationMessage('正在刷新...');
      treeDataProvider.refreshNode(node);
    }),

    vscode.commands.registerCommand('linuxdo.loadMore', async (node: TreeItemNode) => {
      if (node.data.topicId) {
        await treeDataProvider.loadMorePosts(node.data.topicId);
      }
    }),

    vscode.commands.registerCommand('linuxdo.openNotification', async (node: TreeItemNode) => {
      if (node.data.url) {
        // 先标记为已读
        if (node.data.notificationId) {
          await treeDataProvider.markNotificationAsRead(node.data.notificationId);
        }
        // 在浏览器中打开
        vscode.env.openExternal(vscode.Uri.parse(node.data.url));
      }
    }),

    vscode.commands.registerCommand('linuxdo.markNotificationAsRead', async (node: TreeItemNode) => {
      if (node.data.notificationId) {
        await treeDataProvider.markNotificationAsRead(node.data.notificationId);
        vscode.window.showInformationMessage('已标记为已读');
      }
    }),

    treeView
  );

  const hasCookie = await cookieStorage.hasCookie();
  if (!hasCookie) {
    const result = await vscode.window.showInformationMessage(
      '欢迎使用 Linux.do Explorer 先登录再摸鱼哦>_<',
      '登录',
      '稍后'
    );
    if (result === '登录') {
      await authManager.showLoginWebview();
    }
  } else {
    vscode.window.showInformationMessage('Linux.do Explorer 已就绪！');
  }
}

export function deactivate() {
  console.log('Linux.do Explorer 插件已停用');
}
