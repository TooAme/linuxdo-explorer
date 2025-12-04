import * as vscode from 'vscode';
import { CookieStorage } from './CookieStorage';
import { DiscourseApiClient } from '../api/DiscourseApiClient';

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

export class AuthenticationManager {
  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly cookieStorage: CookieStorage,
    private readonly apiClient: DiscourseApiClient
  ) { }

  async showLoginWebview(): Promise<void> {
    const panel = vscode.window.createWebviewPanel(
      'Explorer Settings',
      'Linux.do Explorer 设置',
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
        switch (message.command) {
          case 'saveSettings':
            await this.handleSaveSettings(message, panel);
            break;
          case 'openBrowser':
            void vscode.env.openExternal(vscode.Uri.parse('https://linux.do'));
            break;
          case 'ready':
            await this.sendInitialData(panel);
            break;
        }
      },
      undefined,
      this.context.subscriptions
    );
  }

  private getDefaultSettings(): LinuxDoSettings {
    return {
      showImages: true,
      showEmoji: false,
      autoRefreshMinutes: 0,
      ignoreLineBreaks: false,
      compactMode: false,
      topicFontSize: 'medium',
      grayscaleImages: false,
      showPostInfo: true
    };
  }

  private async loadSettings(): Promise<LinuxDoSettings> {
    const stored = this.context.globalState.get<
      Partial<LinuxDoSettings> & { ignoreSpaces?: boolean }
    >(SETTINGS_KEY);

    return {
      ...this.getDefaultSettings(),
      ...(stored || {}),
      // 兼容旧版本的 ignoreSpaces，映射到 compactMode
      compactMode: stored?.compactMode ?? stored?.ignoreSpaces ?? false
    };
  }

  private async saveSettings(settings: LinuxDoSettings): Promise<void> {
    await this.context.globalState.update(SETTINGS_KEY, settings);
  }

  private async sendInitialData(panel: vscode.WebviewPanel): Promise<void> {
    const settings = await this.loadSettings();
    const cookie = await this.cookieStorage.getCookie();
    const userAgent = await this.cookieStorage.getUserAgent();

    panel.webview.postMessage({
      command: 'init',
      cookie,
      userAgent,
      showImages: settings.showImages,
      showEmoji: settings.showEmoji,
      autoRefreshMinutes: settings.autoRefreshMinutes,
      ignoreLineBreaks: settings.ignoreLineBreaks,
      compactMode: settings.compactMode,
      topicFontSize: settings.topicFontSize,
      grayscaleImages: settings.grayscaleImages,
      showPostInfo: settings.showPostInfo
    });
  }

  private async handleSaveSettings(message: any, panel: vscode.WebviewPanel): Promise<void> {
    const minutesRaw = message.autoRefreshMinutes;
    const autoMinutes =
      typeof minutesRaw === 'number'
        ? minutesRaw
        : parseInt(minutesRaw ?? '0', 10) || 0;

    const topicFontSize: 'small' | 'medium' | 'large' =
      message.topicFontSize === 'small' ||
        message.topicFontSize === 'large' ||
        message.topicFontSize === 'medium'
        ? message.topicFontSize
        : 'medium';

    const cookie = (message.cookie as string | undefined)?.trim() ?? '';
    const userAgent = (message.userAgent as string | undefined)?.trim() ?? '';

    const settings: LinuxDoSettings = {
      showImages: !!message.showImages,
      showEmoji: !!message.showEmoji,
      autoRefreshMinutes: autoMinutes,
      ignoreLineBreaks: !!message.ignoreLineBreaks,
      compactMode: !!message.compactMode,
      topicFontSize,
      grayscaleImages: !!message.grayscaleImages,
      showPostInfo: !!message.showPostInfo
    };

    await this.saveSettings(settings);

    const storedCookie = (await this.cookieStorage.getCookie()) || '';
    const storedUserAgent = (await this.cookieStorage.getUserAgent()) || '';

    // å¯¹æ¯”ç™»å½•ä¿¡æ¯æ—¶ï¼Œç»Ÿä¸€ä½¿ç”¨ trim åŽçš„ç»“æžœï¼Œé¿å…å› ä¸¤ç«¯ç©ºç™½å¯¼è‡´æ¯æ¬¡éƒ½è§†ä¸ºæœ‰æ”¹åŠ¨ã€‚
    const normalizedCookie = cookie.trim();
    const normalizedStoredCookie = storedCookie.trim();
    const normalizedUserAgent = userAgent.trim();
    const normalizedStoredUserAgent = storedUserAgent.trim();

    const cookieChanged = normalizedCookie !== normalizedStoredCookie;
    const userAgentChanged = normalizedUserAgent !== normalizedStoredUserAgent;
    const authChanged = cookieChanged || userAgentChanged;

    // 仅配置变化，登录信息未改动：不触发验证
    if (!authChanged) {
      vscode.window.showInformationMessage('配置已保存');
      panel.webview.postMessage({ command: 'saved' });
      void vscode.commands.executeCommand('linuxdo.updateAutoRefresh');
      return;
    }

    // 登录信息有变更但字段不完整
    if (!cookie || !userAgent) {
      vscode.window.showErrorMessage('已修改 Cookie 或 User-Agent，但二者必须同时填写才能更新登录信息');
      panel.webview.postMessage({ command: 'validationFailed' });
      return;
    }

    // 同时更新 Cookie / User-Agent
    await this.handleCookieSubmit(cookie, userAgent, panel);
  }

  private async handleCookieSubmit(
    cookie: string,
    userAgent: string,
    panel: vscode.WebviewPanel
  ): Promise<void> {
    try {
      panel.webview.postMessage({ command: 'validating' });

      const isValid = await this.apiClient.validateCookie(cookie, userAgent);

      if (isValid) {
        await this.cookieStorage.saveCookie(cookie);
        await this.cookieStorage.saveUserAgent(userAgent);
        await this.apiClient.refreshCookie();
        vscode.window.showInformationMessage('登录成功！配置已保存');
        panel.dispose();
        void vscode.commands.executeCommand('linuxdo.refresh');
        void vscode.commands.executeCommand('linuxdo.updateAutoRefresh');
      } else {
        panel.webview.postMessage({ command: 'validationFailed' });
        vscode.window.showErrorMessage('Cookie 或 User-Agent 无效，请重新获取');
      }
    } catch (error: any) {
      panel.webview.postMessage({ command: 'validationFailed' });
      vscode.window.showErrorMessage(`验证失败: ${error.message}`);
    }
  }

  private getLoginHtml(webview: vscode.Webview): string {
    const nonce = getNonce();

    return `<!DOCTYPE html>
    <html lang="zh-CN">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
        <title>设置</title>
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
            max-width: 700px;
            margin: 0 auto;
          }
          h1 {
            font-size: 24px;
            font-weight: 500;
            margin-bottom: 24px;
            letter-spacing: 0.5px;
          }
          .section {
            margin-bottom: 24px;
          }
          .section h2 {
            font-size: 16px;
            font-weight: 500;
            margin-bottom: 12px;
            opacity: 0.9;
          }
          .input-group {
            margin-bottom: 18px;
          }
          .input-group label {
            display: block;
            margin-bottom: 8px;
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
          .inline-option {
            margin: 6px 0;
          }
          .inline-option label {
            display: flex;
            align-items: center;
            gap: 6px;
            cursor: pointer;
          }
          select {
            padding: 6px 10px;
            background: transparent;
            color: var(--vscode-input-foreground);
            border: 1px solid var(--vscode-input-border);
            outline: none;
          }
          select:focus {
            border-color: var(--focus-border-color);
          }
          .radio-group {
            margin: 8px 0 16px 0;
          }
          .radio-group-title {
            font-weight: 500;
            margin-bottom: 8px;
            opacity: 0.9;
          }
          .radio-options {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
          }
          .radio-options label {
            display: flex;
            align-items: center;
            gap: 4px;
            cursor: pointer;
          }
          .button-group {
            display: flex;
            gap: 15px;
            margin-top: 24px;
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
            margin-top: 16px;
            padding: 10px;
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
            margin-top: 24px;
            padding-top: 16px;
            border-top: 1px solid var(--vscode-input-border);
            font-size: 12px;
            opacity: 0.7;
          }
        </style>
      </head>
      <body>
        <div class="container">
          <h1>设置</h1>

          <div class="section">
            <h2>显示选项</h2>
            <div class="inline-option">
              <label>
                <input type="checkbox" id="showImages">
                <span>显示图片预览</span>
              </label>
            </div>
            <div class="inline-option">
              <label>
                <input type="checkbox" id="showEmoji">
                <span>显示表情符号</span>
              </label>
            </div>
            <div class="inline-option">
              <label>
                <input type="checkbox" id="ignoreLineBreaks">
                <span>忽略回车符</span>
              </label>
            </div>
            <div class="inline-option">
              <label>
                <input type="checkbox" id="compactMode">
                <span>紧凑模式（减少详情页空白）</span>
              </label>
            </div>
            <div class="inline-option">
              <label>
                <input type="checkbox" id="grayscaleImages">
                <span>黑白模式（图片灰度显示，悬停恢复彩色）</span>
              </label>
            </div>
            <div class="inline-option">
              <label>
                <input type="checkbox" id="showPostInfo" checked>
                <span>帖子信息显示（显示楼数/发帖人/时间）</span>
              </label>
            </div>
            <div class="radio-group">
              <div class="radio-group-title">自动刷新</div>
              <div class="radio-options">
                <label><input type="radio" name="autoRefresh" value="0" checked><span>关闭</span></label>
                <label><input type="radio" name="autoRefresh" value="5"><span>每 5 分钟</span></label>
                <label><input type="radio" name="autoRefresh" value="10"><span>每 10 分钟</span></label>
                <label><input type="radio" name="autoRefresh" value="20"><span>每 20 分钟</span></label>
                <label><input type="radio" name="autoRefresh" value="60"><span>每 60 分钟</span></label>
              </div>
            </div>
            <div class="radio-group">
              <div class="radio-group-title">话题窗口字号</div>
              <div class="radio-options">
                <label><input type="radio" name="topicFontSize" value="small"><span>小</span></label>
                <label><input type="radio" name="topicFontSize" value="medium" checked><span>中</span></label>
                <label><input type="radio" name="topicFontSize" value="large"><span>大</span></label>
              </div>
            </div>
          </div>

          <div class="section">
            <h2>登录信息</h2>
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

            <div class="input-group">
              <label for="userAgentInput">User-Agent 内容</label>
              <textarea
                id="userAgentInput"
                placeholder="在此粘贴 User-Agent 内容..."
                style="min-height: 60px;"
              ></textarea>
            </div>
          </div>

          <div class="button-group">
            <button id="submitBtn">
              保存
            </button>
          </div>

          <div id="status" class="status"></div>

          <div class="tip">
            提示：Cookie 和 User-Agent 包含你的登录凭证和浏览器指纹信息，请妥善保管，不要分享给他人。
          </div>
        </div>

        <script nonce="${nonce}">
          const vscode = acquireVsCodeApi();
          
          function openBrowser() {
            vscode.postMessage({ command: 'openBrowser' });
          }

          function saveSettings() {
            const cookieInput = document.getElementById('cookieInput');
            const userAgentInput = document.getElementById('userAgentInput');
            const submitBtn = document.getElementById('submitBtn');
            const showImagesInput = document.getElementById('showImages');
            const showEmojiInput = document.getElementById('showEmoji');
            const ignoreLineBreaksInput = document.getElementById('ignoreLineBreaks');
            const compactModeInput = document.getElementById('compactMode');
            const grayscaleImagesInput = document.getElementById('grayscaleImages');
            const showPostInfoInput = document.getElementById('showPostInfo');
            const autoRefreshRadio = document.querySelector('input[name="autoRefresh"]:checked');
            const topicFontSizeRadio = document.querySelector('input[name="topicFontSize"]:checked');

            const cookie = (cookieInput?.value || '').trim();
            const userAgent = (userAgentInput?.value || '').trim();
            const showImages = !!showImagesInput?.checked;
            const showEmoji = !!showEmojiInput?.checked;
            const ignoreLineBreaks = !!ignoreLineBreaksInput?.checked;
            const compactMode = !!compactModeInput?.checked;
            const grayscaleImages = !!grayscaleImagesInput?.checked;
            const showPostInfo = !!showPostInfoInput?.checked;
            const autoRefreshMinutes = parseInt(autoRefreshRadio?.value || '0', 10) || 0;
            const topicFontSize = topicFontSizeRadio?.value || 'medium';

            if (submitBtn) {
              submitBtn.disabled = true;
            }
            showStatus('正在保存配置...', 'validating');

            vscode.postMessage({
              command: 'saveSettings',
              cookie,
              userAgent,
              showImages,
              showEmoji,
              ignoreLineBreaks,
              compactMode,
              grayscaleImages,
              showPostInfo,
              autoRefreshMinutes,
              topicFontSize
            });
          }

          function showStatus(message, type) {
            const status = document.getElementById('status');
            if (!status) return;
            status.textContent = message;
            status.className = 'status show ' + type;
          }

          window.addEventListener('message', event => {
            const message = event.data;
            const submitBtn = document.getElementById('submitBtn');

            switch (message.command) {
              case 'init': {
                const cookieInput = document.getElementById('cookieInput');
                const userAgentInput = document.getElementById('userAgentInput');
                const showImagesInput = document.getElementById('showImages');
                const showEmojiInput = document.getElementById('showEmoji');
                const ignoreLineBreaksInput = document.getElementById('ignoreLineBreaks');
                const compactModeInput = document.getElementById('compactMode');
                const grayscaleImagesInput = document.getElementById('grayscaleImages');
                const showPostInfoInput = document.getElementById('showPostInfo');

                if (cookieInput) cookieInput.value = message.cookie || '';
                if (userAgentInput) userAgentInput.value = message.userAgent || '';
                if (showImagesInput) showImagesInput.checked = !!message.showImages;
                if (showEmojiInput) showEmojiInput.checked = !!message.showEmoji;
                if (ignoreLineBreaksInput) ignoreLineBreaksInput.checked = !!message.ignoreLineBreaks;
                if (compactModeInput) compactModeInput.checked = !!message.compactMode;
                if (grayscaleImagesInput) grayscaleImagesInput.checked = !!message.grayscaleImages;
                if (showPostInfoInput) showPostInfoInput.checked = message.showPostInfo !== false;
                
                // 设置自动刷新单选按钮
                if (typeof message.autoRefreshMinutes === 'number') {
                  const autoRefreshRadio = document.querySelector('input[name="autoRefresh"][value="' + message.autoRefreshMinutes + '"]');
                  if (autoRefreshRadio) autoRefreshRadio.checked = true;
                }
                // 设置字号单选按钮
                if (typeof message.topicFontSize === 'string') {
                  const fontSizeRadio = document.querySelector('input[name="topicFontSize"][value="' + message.topicFontSize + '"]');
                  if (fontSizeRadio) fontSizeRadio.checked = true;
                }
                break;
              }
              case 'validating':
                showStatus('正在验证 Cookie 和 User-Agent...', 'validating');
                break;
              case 'saved':
                if (submitBtn) submitBtn.disabled = false;
                showStatus('配置已保存', 'validating');
                break;
              case 'validationFailed':
                if (submitBtn) submitBtn.disabled = false;
                showStatus('Cookie 或 User-Agent 验证失败，请检查是否正确复制。', 'error');
                break;
            }
          });

          document.addEventListener('DOMContentLoaded', () => {
            const openBtn = document.getElementById('openBrowserBtn');
            const submitBtn = document.getElementById('submitBtn');
            const cookieInput = document.getElementById('cookieInput');

            if (openBtn) {
              openBtn.addEventListener('click', openBrowser);
            }
            if (submitBtn) {
              submitBtn.addEventListener('click', saveSettings);
            }
            if (cookieInput) {
              cookieInput.addEventListener('keydown', (e) => {
                if (e.ctrlKey && e.key === 'Enter') {
                  saveSettings();
                }
              });
            }

            vscode.postMessage({ command: 'ready' });
          });
        </script>
      </body>
    </html>`;
  }
}

// 生成随机 nonce 用于 CSP
function getNonce() {
  let text = '';
  const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  for (let i = 0; i < 32; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
}
