import * as vscode from 'vscode';

export enum NodeType {
  ROOT = 'root',
  ALL_TOPICS = 'all-topics',
  CATEGORY = 'category',
  TOPIC = 'topic',
  POST = 'post',
  LOAD_MORE = 'load-more',
  NOTIFICATION = 'notification'
}

export interface NodeData {
  type: NodeType;
  id?: number | string;
  label: string;
  description?: string;
  tooltip?: string | vscode.MarkdownString;
  categoryId?: number;
  topicId?: number;
  postId?: number;
  url?: string;
  notificationId?: number;
}

export class TreeItemNode extends vscode.TreeItem {
  constructor(
    public readonly data: NodeData,
    public readonly collapsibleState: vscode.TreeItemCollapsibleState
  ) {
    super(data.label, collapsibleState);

    // 优先使用节点数据中显式提供的 tooltip（可以是 MarkdownString）
    if (data.tooltip !== undefined) {
      this.tooltip = data.tooltip;
    } else if (data.type === NodeType.TOPIC) {
      // 话题节点使用完整标题作为 tooltip
      this.tooltip = data.label;
    } else {
      // 其他节点：有 description 就显示 description，否则显示 label
      this.tooltip = data.description || data.label;
    }

    this.description = data.description;
    this.contextValue = data.type;

    this.iconPath = this.getIcon();

    // 话题节点：点击标题在编辑器中预览完整话题
    if (data.type === NodeType.TOPIC && data.topicId) {
      this.command = {
        command: 'linuxdo.openTopicPreview',
        title: '预览话题内容',
        arguments: [this]
      };
    }

    // “加载更多”节点点击命令
    if (data.type === NodeType.LOAD_MORE) {
      this.command = {
        command: 'linuxdo.loadMore',
        title: '加载更多',
        arguments: [this]
      };
    }

    // 通知节点点击命令
    if (data.type === NodeType.NOTIFICATION && data.topicId) {
      this.command = {
        command: 'linuxdo.openNotification',
        title: '打开通知',
        arguments: [this]
      };
    }
  }

  private getIcon(): vscode.ThemeIcon {
    switch (this.data.type) {
      case NodeType.ROOT:
      case NodeType.ALL_TOPICS:
        return new vscode.ThemeIcon('home');
      case NodeType.CATEGORY:
        return new vscode.ThemeIcon('folder');
      case NodeType.TOPIC:
        return new vscode.ThemeIcon('file-text');
      case NodeType.POST:
        return new vscode.ThemeIcon('comment');
      case NodeType.LOAD_MORE:
        return new vscode.ThemeIcon('chevron-down');
      case NodeType.NOTIFICATION:
        return new vscode.ThemeIcon('bell');
      default:
        return new vscode.ThemeIcon('file');
    }
  }
}

