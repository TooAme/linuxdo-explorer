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

    // 使用完整内容作为tooltip，如果没有则使用description或label
    this.tooltip = data.description || data.label;
    this.description = data.description;
    this.contextValue = data.type;

    this.iconPath = this.getIcon();

    // 如果是"加载更多"节点,设置点击命令
    if (data.type === NodeType.LOAD_MORE) {
      this.command = {
        command: 'linuxdo.loadMore',
        title: '加载更多',
        arguments: [this]
      };
    }

    // 如果是通知节点,设置点击命令
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
