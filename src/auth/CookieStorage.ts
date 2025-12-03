import * as vscode from 'vscode';

export class CookieStorage {
  private readonly STORAGE_KEY = 'linuxdo-cookie';
  private readonly USER_AGENT_KEY = 'linuxdo-user-agent';

  constructor(private secrets: vscode.SecretStorage) {}

  async saveCookie(cookie: string): Promise<void> {
    await this.secrets.store(this.STORAGE_KEY, cookie);
  }

  async getCookie(): Promise<string> {
    return await this.secrets.get(this.STORAGE_KEY) || '';
  }

  async saveUserAgent(userAgent: string): Promise<void> {
    await this.secrets.store(this.USER_AGENT_KEY, userAgent);
  }

  async getUserAgent(): Promise<string> {
    return await this.secrets.get(this.USER_AGENT_KEY) || '';
  }

  async deleteCookie(): Promise<void> {
    await this.secrets.delete(this.STORAGE_KEY);
    await this.secrets.delete(this.USER_AGENT_KEY);
  }

  async hasCookie(): Promise<boolean> {
    const cookie = await this.getCookie();
    return cookie.length > 0;
  }
}
