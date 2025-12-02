import * as vscode from 'vscode';
import { CookieStorage } from './CookieStorage';
import { DiscourseApiClient } from '../api/DiscourseApiClient';

export class AuthenticationManager {
  constructor(
    private context: vscode.ExtensionContext,
    private cookieStorage: CookieStorage,
    private apiClient: DiscourseApiClient
  ) { }

  async showLoginWebview(): Promise<void> {
    console.log('[AuthenticationManager] 显示登录Webview');

    const panel = vscode.window.createWebviewPanel(
      'linuxdoLogin',
      'Linux.do 登录',
      vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: []
      }
    );

    panel.webview.html = this.getLoginHtml(panel.webview);

    panel.webview.onDidReceiveMessage(
      async message => {
        console.log('[AuthenticationManager] 收到webview消息:', message.command);
        switch (message.command) {
          case 'submitCookie':
            await this.handleCookieSubmit(message.cookie, panel);
            break;
          case 'openBrowser':
            vscode.env.openExternal(vscode.Uri.parse('https://linux.do'));
            break;
          case 'ready':
            console.log('[AuthenticationManager] Webview已就绪');
            break;
        }
      },
      undefined,
      this.context.subscriptions
    );
  }

  private async handleCookieSubmit(cookie: string, panel: vscode.WebviewPanel): Promise<void> {
    try {
      console.log('[AuthenticationManager] 开始验证Cookie...');
      panel.webview.postMessage({ command: 'validating' });

      const isValid = await this.apiClient.validateCookie(cookie);

      if (isValid) {
        console.log('[AuthenticationManager] Cookie验证成功');
        await this.cookieStorage.saveCookie(cookie);
        await this.apiClient.refreshCookie();
        vscode.window.showInformationMessage('登录成功!');
        panel.dispose();
        vscode.commands.executeCommand('linuxdo.refresh');
      } else {
        console.error('[AuthenticationManager] Cookie验证失败');
        panel.webview.postMessage({ command: 'validationFailed' });
        vscode.window.showErrorMessage('Cookie无效，请重新获取');
      }
    } catch (error: any) {
      console.error('[AuthenticationManager] Cookie验证异常:', error);
      panel.webview.postMessage({ command: 'validationFailed' });
      vscode.window.showErrorMessage(`验证失败: ${error.message}`);
    }
  }

  private getLoginHtml(webview: vscode.Webview): string {
    // 生成一个nonce来允许内联脚本执行
    const nonce = getNonce();

    return `<!DOCTYPE html>
    <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
        <title>Linux.do 登录</title>
        <style>
          :root {
            --container-paddding: 20px;
            --input-padding: 12px;
            --button-padding: 10px 20px;
            --focus-border-color: var(--vscode-focusBorder);
          }
          * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
          }
          body {
            padding: 40px 20px;
            font-family: var(--vscode-font-family);
            color: var(--vscode-foreground);
            background-color: var(--vscode-editor-background);
            line-height: 1.6;
          }
          .container {
            max-width: 600px;
            margin: 0 auto;
          }
          h1 {
            font-size: 24px;
            font-weight: 500;
            margin-bottom: 30px;
            letter-spacing: 0.5px;
          }
          .instructions {
            margin-bottom: 30px;
          }
          .instructions h3 {
            font-size: 16px;
            font-weight: 500;
            margin-bottom: 15px;
            opacity: 0.9;
          }
          .instructions ol {
            padding-left: 20px;
            opacity: 0.8;
          }
          .instructions li {
            margin: 8px 0;
          }
          .instructions code {
            font-family: var(--vscode-editor-font-family);
            background: var(--vscode-textCodeBlock-background);
            padding: 2px 6px;
            border-radius: 2px;
            font-size: 0.9em;
          }
          .input-group {
            margin-bottom: 25px;
          }
          .input-group label {
            display: block;
            margin-bottom: 10px;
            font-weight: 500;
            opacity: 0.9;
          }
          textarea {
            width: 100%;
            min-height: 120px;
            padding: var(--input-padding);
            background: transparent;
            color: var(--vscode-input-foreground);
            border: 1px solid var(--vscode-input-border);
            font-family: var(--vscode-editor-font-family);
            font-size: 13px;
            resize: vertical;
            outline: none;
            transition: border-color 0.2s;
          }
          textarea:focus {
            border-color: var(--focus-border-color);
          }
          .button-group {
            display: flex;
            gap: 15px;
            margin-top: 30px;
          }
          button {
            padding: var(--button-padding);
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: 1px solid #fff;
            cursor: pointer;
            font-size: 14px;
            font-weight: 500;
            transition: opacity 0.2s;
          }
          button:hover {
            opacity: 0.9;
          }
          button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
          }
          .secondary-button {
            background: transparent;
            color: var(--vscode-foreground);
            border: 1px solid var(--vscode-input-border);
          }
          .secondary-button:hover {
            background: var(--vscode-toolbar-hoverBackground);
          }
          .status {
            margin-top: 20px;
            padding: 12px;
            font-size: 13px;
            display: none;
            border: 1px solid transparent;
          }
          .status.show {
            display: block;
          }
          .status.validating {
            color: var(--vscode-descriptionForeground);
            border-color: var(--vscode-input-border);
          }
          .status.error {
            color: var(--vscode-errorForeground);
            border-color: var(--vscode-errorForeground);
          }
          .tip {
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid var(--vscode-input-border);
            font-size: 12px;
            opacity: 0.7;
          }
        </style>
      </head>
      <body>
        <div class="container">
          <h1>Linux.do 登录</h1>

          <div class="instructions">
            <h3>如何获取 Cookie</h3>
            <ol>
              <li>点击下方按钮在浏览器中打开 linux.do 并登录账号</li>
              <li>登录成功后，按 F12 打开浏览器开发者工具</li>
              <li>切换到 网络 (Network) 标签页</li>
              <li>刷新页面 F5 或 Ctrl+R</li>
              <li>点击左侧任意一个请求</li>
              <li>在右侧找到 请求标头 (Request Headers)</li>
              <li>找到 Cookie: 字段，复制其完整内容</li>
              <li>将复制的 Cookie 粘贴到下方输入框中</li>
            </ol>
          </div>

          <div class="button-group">
            <button id="openBrowserBtn" class="secondary-button">
              在浏览器中打开 Linux.do
            </button>
          </div>

          <div class="input-group">
            <label for="cookieInput">Cookie 内容</label>
            <textarea
              id="cookieInput"
              placeholder="在此粘贴 Cookie 内容..."
            ></textarea>
          </div>

          <div class="button-group">
            <button id="submitBtn">
              提交登录
            </button>
          </div>

          <div id="status" class="status"></div>

          <div class="tip">
            提示：Cookie 包含您的登录凭证，请妥善保管，不要分享给他人。
          </div>
        </div>

        <script nonce="${nonce}">
          const vscode = acquireVsCodeApi();
          
          function openBrowser() {
            vscode.postMessage({ command: 'openBrowser' });
          }

          function submitCookie() {
            const cookieInput = document.getElementById('cookieInput');
            const submitBtn = document.getElementById('submitBtn');
            const cookie = cookieInput.value.trim();

            if (!cookie) {
              showStatus('请先输入 Cookie 内容', 'error');
              return;
            }

            submitBtn.disabled = true;
            showStatus('正在验证 Cookie...', 'validating');

            vscode.postMessage({
              command: 'submitCookie',
              cookie: cookie
            });
          }

          function showStatus(message, type) {
            const status = document.getElementById('status');
            status.textContent = message;
            status.className = 'status show ' + type;
          }

          window.addEventListener('message', event => {
            const message = event.data;
            const submitBtn = document.getElementById('submitBtn');

            switch (message.command) {
              case 'validating':
                showStatus('正在验证 Cookie...', 'validating');
                break;
              case 'validationFailed':
                if (submitBtn) submitBtn.disabled = false;
                showStatus('Cookie 验证失败，请检查是否正确复制', 'error');
                break;
            }
          });

          document.addEventListener('DOMContentLoaded', () => {
            document.getElementById('openBrowserBtn').addEventListener('click', openBrowser);
            document.getElementById('submitBtn').addEventListener('click', submitCookie);
            
            document.getElementById('cookieInput').addEventListener('keydown', (e) => {
              if (e.ctrlKey && e.key === 'Enter') {
                submitCookie();
              }
            });

            vscode.postMessage({ command: 'ready' });
          });
        </script>
      </body>
    </html>`;
  }
}

// 生成随机nonce用于CSP
function getNonce() {
  let text = '';
  const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  for (let i = 0; i < 32; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
}
