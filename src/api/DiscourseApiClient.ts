import * as https from 'https';
import * as zlib from 'zlib';
import { DISCOURSE_API } from '../constants/Config';
import type {
  Category,
  CategoriesResponse,
  Topic,
  TopicListResponse,
  TopicDetail,
  Post,
  Notification,
  NotificationsResponse
} from './ApiTypes';

export class DiscourseApiClient {
  private cookie: string = '';
  private userAgent: string = '';
  private cookieInitialized: boolean = false;
  private csrfToken: string = '';

  constructor(
    private getCookieFunc: () => Promise<string>,
    private getUserAgentFunc: () => Promise<string>
  ) {
    // 不在构造函数中调用异步方法
  }

  /**
   * 从 Cookie 中提取 CSRF Token
   * 也可能在 session cookie 中
   */
  private extractCsrfToken(cookieString: string): string {
    // 尝试方法1: 直接从 csrf_token 字段提取
    let match = cookieString.match(/csrf_token=([^;]+)/);
    if (match) {
      return decodeURIComponent(match[1]);
    }

    // 尝试方法2: 从 _t cookie 中提取（Discourse 使用的主要 session cookie）
    match = cookieString.match(/_t=([^;]+)/);
    if (match) {
      // _t cookie 本身可能就是 CSRF token
      return decodeURIComponent(match[1]);
    }

    return '';
  }

  /**
   * 通过访问首页获取 CSRF Token
   */
  private async fetchCsrfTokenFromHomepage(): Promise<string> {
    try {
      console.log('[DiscourseApiClient] 尝试从首页获取 CSRF Token...');
      const url = DISCOURSE_API.BASE_URL;

      const response = await this.httpsRequest(url, {
        method: 'GET',
        headers: {
          'Cookie': this.cookie,
          'User-Agent': this.userAgent || 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0',
          'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
          'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
          'Accept-Encoding': 'gzip, deflate, br'
        }
      });

      console.log('[DiscourseApiClient] 首页响应状态:', response.statusCode);
      console.log('[DiscourseApiClient] 首页内容长度:', response.body.length);

      // 尝试多种 meta 标签格式
      let match = response.body.match(/<meta name=["']csrf-token["'] content=["']([^"']+)["']/);
      if (!match) {
        match = response.body.match(/<meta content=["']([^"']+)["'] name=["']csrf-token["']/);
      }
      if (!match) {
        match = response.body.match(/csrf[_-]?token["']?\s*[:=]\s*["']([^"']+)["']/i);
      }

      if (match) {
        const token = match[1];
        console.log('[DiscourseApiClient] 从首页获取到 CSRF Token:', token.substring(0, 30) + '...');
        return token;
      }

      console.log('[DiscourseApiClient] 首页中未找到 CSRF Token');
      console.log('[DiscourseApiClient] 首页前1000字符:', response.body.substring(0, 1000));
      return '';
    } catch (error: any) {
      console.error('[DiscourseApiClient] 获取 CSRF Token 失败:', error.message);
      return '';
    }
  }

  private async ensureCookieLoaded(): Promise<void> {
    if (!this.cookieInitialized) {
      this.cookie = await this.getCookieFunc();
      this.userAgent = await this.getUserAgentFunc();
      this.cookieInitialized = true;

      console.log('[DiscourseApiClient] Cookie已加载，长度:', this.cookie.length);
      console.log('[DiscourseApiClient] Cookie内容（前500字符）:', this.cookie.substring(0, 500));
      console.log('[DiscourseApiClient] User-Agent已加载:', this.userAgent.substring(0, 100));

      // 先尝试从 Cookie 中提取
      this.csrfToken = this.extractCsrfToken(this.cookie);

      // 如果从 Cookie 提取失败，尝试从首页获取
      if (!this.csrfToken) {
        console.log('[DiscourseApiClient] Cookie中未找到 CSRF Token，尝试从首页获取...');
        this.csrfToken = await this.fetchCsrfTokenFromHomepage();
      }

      console.log('[DiscourseApiClient] CSRF Token:', this.csrfToken ? `已提取: ${this.csrfToken.substring(0, 20)}...` : '未找到');
    }
  }

  async refreshCookie(): Promise<void> {
    this.cookie = await this.getCookieFunc();
    this.userAgent = await this.getUserAgentFunc();
    this.cookieInitialized = true;

    // 重新提取 CSRF Token
    this.csrfToken = this.extractCsrfToken(this.cookie);
    if (!this.csrfToken) {
      this.csrfToken = await this.fetchCsrfTokenFromHomepage();
    }

    console.log('[DiscourseApiClient] Cookie和User-Agent已刷新');
    console.log('[DiscourseApiClient] CSRF Token:', this.csrfToken ? '已提取' : '未找到');
  }

  /**
   * 使用https模块发送请求
   */
  private httpsRequest(url: string, options: https.RequestOptions, postData?: string, followRedirects: number = 3): Promise<{
    statusCode: number;
    statusMessage: string;
    headers: any;
    body: string;
  }> {
    return new Promise((resolve, reject) => {
      const req = https.request(url, options, (res) => {
        // 处理重定向
        if ((res.statusCode === 301 || res.statusCode === 302 || res.statusCode === 307 || res.statusCode === 308) && followRedirects > 0) {
          const redirectUrl = res.headers.location;
          if (redirectUrl) {
            console.log(`[DiscourseApiClient] 重定向到: ${redirectUrl}`);

            // 如果是相对路径，转换为绝对路径
            const finalUrl = redirectUrl.startsWith('http')
              ? redirectUrl
              : `${DISCOURSE_API.BASE_URL}${redirectUrl}`;

            // 递归处理重定向
            this.httpsRequest(finalUrl, options, postData, followRedirects - 1)
              .then(resolve)
              .catch(reject);
            return;
          }
        }

        const chunks: Buffer[] = [];

        res.on('data', (chunk) => {
          chunks.push(chunk);
        });

        res.on('end', () => {
          let buffer = Buffer.concat(chunks);

          // 处理压缩编码
          const encoding = res.headers['content-encoding'];

          try {
            let body: string;

            if (encoding === 'gzip') {
              body = zlib.gunzipSync(buffer).toString('utf-8');
            } else if (encoding === 'deflate') {
              body = zlib.inflateSync(buffer).toString('utf-8');
            } else if (encoding === 'br') {
              body = zlib.brotliDecompressSync(buffer).toString('utf-8');
            } else if (encoding === 'zstd') {
              // zstd需要特殊处理，暂时返回原始数据
              console.warn('[DiscourseApiClient] 不支持zstd压缩，返回原始数据');
              body = buffer.toString('utf-8');
            } else {
              body = buffer.toString('utf-8');
            }

            resolve({
              statusCode: res.statusCode || 0,
              statusMessage: res.statusMessage || '',
              headers: res.headers,
              body: body
            });
          } catch (decompressError: any) {
            console.error('[DiscourseApiClient] 解压失败:', decompressError.message);
            // 如果解压失败，尝试返回原始数据
            resolve({
              statusCode: res.statusCode || 0,
              statusMessage: res.statusMessage || '',
              headers: res.headers,
              body: buffer.toString('utf-8')
            });
          }
        });
      });

      req.on('error', (error) => {
        reject(error);
      });

      // 如果有POST数据，写入请求体
      if (postData) {
        req.write(postData);
      }

      req.end();
    });
  }

  private async request<T>(endpoint: string, options?: RequestInit): Promise<T> {
    // 确保Cookie已加载
    await this.ensureCookieLoaded();

    if (!this.cookie) {
      console.error('[DiscourseApiClient] Cookie为空，请先登录');
      throw new Error('请先登录');
    }

    const url = `${DISCOURSE_API.BASE_URL}${endpoint}`;
    const method = (options?.method as string) || 'GET';
    const body = options?.body as string | undefined;

    console.log(`[DiscourseApiClient] 请求: ${method} ${url}`);
    console.log(`[DiscourseApiClient] Cookie长度: ${this.cookie.length}`);
    if (body) {
      console.log(`[DiscourseApiClient] 请求体:`, body);
    }

    try {
      const headers: Record<string, string> = {
        'Cookie': this.cookie,
        'User-Agent': this.userAgent || 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0',
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6',
        'Accept-Encoding': 'gzip, deflate, br',
        'Sec-CH-UA': '"Chromium";v="142", "Microsoft Edge";v="142", "Not_A Brand";v="99"',
        'Sec-CH-UA-Mobile': '?0',
        'Sec-CH-UA-Platform': '"Windows"',
        ...(options?.headers as Record<string, string> || {})
      };

      // 如果是 POST/PUT/DELETE 请求，使用 AJAX 风格的请求头
      if (method === 'POST' || method === 'PUT' || method === 'DELETE') {
        headers['Content-Type'] = 'application/json; charset=UTF-8';
        headers['X-Requested-With'] = 'XMLHttpRequest';
        headers['Origin'] = DISCOURSE_API.BASE_URL.replace(/\/$/, '');
        headers['Referer'] = DISCOURSE_API.BASE_URL;
        headers['Sec-Fetch-Dest'] = 'empty';
        headers['Sec-Fetch-Mode'] = 'cors';
        headers['Sec-Fetch-Site'] = 'same-origin';

        // 添加 CSRF Token
        if (this.csrfToken) {
          headers['X-CSRF-Token'] = this.csrfToken;
          console.log('[DiscourseApiClient] 添加 CSRF Token 到请求头');
        } else {
          console.warn('[DiscourseApiClient] 警告: CSRF Token 未找到，POST 请求可能失败');
        }

        if (body) {
          headers['Content-Length'] = Buffer.byteLength(body).toString();
        }
      } else {
        // GET 请求使用导航风格的请求头
        headers['Cache-Control'] = 'max-age=0';
        headers['Sec-Fetch-Dest'] = 'document';
        headers['Sec-Fetch-Mode'] = 'navigate';
        headers['Sec-Fetch-Site'] = 'same-origin';
        headers['Sec-Fetch-User'] = '?1';
        headers['Upgrade-Insecure-Requests'] = '1';
      }

      const response = await this.httpsRequest(url, {
        method: method,
        headers: headers
      }, body);

      console.log(`[DiscourseApiClient] 响应状态: ${response.statusCode} ${response.statusMessage}`);

      if (response.statusCode < 200 || response.statusCode >= 300) {
        if (response.statusCode === 401 || response.statusCode === 403) {
          console.error(`[DiscourseApiClient] 认证失败: ${response.statusCode}`);
          throw new Error('登录已过期，请重新登录');
        }
        console.error(`[DiscourseApiClient] API请求失败: ${response.statusCode} ${response.statusMessage}`);
        console.error(`[DiscourseApiClient] 响应内容:`, response.body);
        throw new Error(`API请求失败: ${response.statusCode} ${response.statusMessage}`);
      }

      console.log(`[DiscourseApiClient] 请求成功: ${url}`);
      return JSON.parse(response.body) as T;
    } catch (error: any) {
      console.error(`[DiscourseApiClient] 请求异常: ${error.message}`);
      throw error;
    }
  }

  async getCategories(): Promise<Category[]> {
    const data = await this.request<CategoriesResponse>(DISCOURSE_API.ENDPOINTS.CATEGORIES);
    return data.category_list.categories;
  }

  async getCategoryTopics(categoryId: number, page: number = 0): Promise<Topic[]> {
    const endpoint = `${DISCOURSE_API.ENDPOINTS.CATEGORY_TOPICS(categoryId)}?page=${page}`;
    const data = await this.request<TopicListResponse>(endpoint);
    return data.topic_list.topics;
  }

  async getLatestTopics(page: number = 0): Promise<Topic[]> {
    const endpoint = `${DISCOURSE_API.ENDPOINTS.LATEST_TOPICS}?page=${page}`;
    const data = await this.request<TopicListResponse>(endpoint);
    return data.topic_list.topics;
  }

  async getTopic(topicId: number): Promise<TopicDetail> {
    return await this.request<TopicDetail>(DISCOURSE_API.ENDPOINTS.TOPIC(topicId));
  }

  async getTopicPosts(topicId: number): Promise<Post[]> {
    const data = await this.getTopic(topicId);
    return data.post_stream.posts;
  }

  /**
   * 加载话题的更多回复
   * @param topicId 话题ID
   * @param postIds 要加载的 post ID 数组
   * @returns 回复列表
   */
  async loadMoreTopicPosts(topicId: number, postIds: number[]): Promise<Post[]> {
    // 尝试使用 /posts 端点
    const postIdsParam = postIds.map(id => `post_ids[]=${id}`).join('&');
    const endpoint = `/t/${topicId}/posts.json?${postIdsParam}`;

    console.log(`[DiscourseApiClient] 加载话题 ${topicId} 的 ${postIds.length} 条回复`);
    console.log(`[DiscourseApiClient] 请求的 post IDs:`, postIds.slice(0, 5), '...');
    console.log(`[DiscourseApiClient] 请求 URL: ${endpoint.substring(0, 100)}...`);

    const data = await this.request<{
      post_stream?: {
        posts: Post[];
        stream?: number[];
      };
    }>(endpoint);

    console.log(`[DiscourseApiClient] API 响应结构:`, Object.keys(data));

    // 尝试从不同的位置获取 posts
    const posts = data.post_stream?.posts || (data as any).posts || [];

    console.log(`[DiscourseApiClient] API 返回的 posts 数量: ${posts.length}`);
    if (posts.length > 0) {
      console.log(`[DiscourseApiClient] API 返回的 post IDs:`, posts.map((p: any) => p.id).slice(0, 5), '...');
    }

    return posts;
  }

  /**
   * 创建回复
   * @param topicId 话题ID
   * @param content 回复内容
   * @returns 创建的回复对象
   */
  async createReply(topicId: number, content: string): Promise<Post> {
    const endpoint = '/posts.json';
    const body = JSON.stringify({
      raw: content,
      topic_id: topicId
    });

    return await this.request<Post>(endpoint, {
      method: 'POST',
      body: body
    });
  }

  /**
   * 点赞帖子
   * @param postId 帖子ID
   * @returns 点赞结果
   */
  async likePost(postId: number): Promise<{ success: boolean }> {
    const endpoint = '/post_actions.json';
    const body = JSON.stringify({
      post_id: postId,  // 使用 post_id 而不是 id
      post_action_type_id: 2, // 2 代表点赞
      flag_topic: false
    });

    return await this.request<{ success: boolean }>(endpoint, {
      method: 'POST',
      body: body
    });
  }

  /**
   * 获取通知列表
   * @returns 通知列表
   */
  async getNotifications(): Promise<Notification[]> {
    const endpoint = '/notifications.json';
    console.log('[DiscourseApiClient] 获取通知列表');

    const data = await this.request<NotificationsResponse>(endpoint);
    console.log(`[DiscourseApiClient] 获取到 ${data.notifications.length} 条通知`);

    return data.notifications;
  }

  /**
   * 标记通知为已读
   * @param notificationId 通知ID
   * @returns 是否成功
   */
  async markNotificationAsRead(notificationId: number): Promise<boolean> {
    const endpoint = `/notifications/mark-read.json`;
    const body = JSON.stringify({
      id: notificationId
    });

    console.log(`[DiscourseApiClient] 标记通知 ${notificationId} 为已读`);

    try {
      await this.request<{ success: boolean }>(endpoint, {
        method: 'PUT',
        body: body
      });
      return true;
    } catch (error) {
      console.error(`[DiscourseApiClient] 标记通知失败:`, error);
      return false;
    }
  }

  async validateCookie(cookieStr: string, userAgentStr: string): Promise<boolean> {
    try {
      console.log('[DiscourseApiClient] 开始验证Cookie和User-Agent...');
      console.log('[DiscourseApiClient] Cookie原始长度:', cookieStr.length);
      console.log('[DiscourseApiClient] User-Agent长度:', userAgentStr.length);

      // 清理Cookie：移除换行符、多余空格等
      const cleanedCookie = cookieStr
        .replace(/\r?\n|\r/g, ' ')  // 替换换行符为空格
        .replace(/\s+/g, ' ')        // 合并多个空格为一个
        .trim();                     // 移除首尾空格

      // 清理User-Agent
      const cleanedUserAgent = userAgentStr
        .replace(/\r?\n|\r/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();

      console.log('[DiscourseApiClient] Cookie清理后长度:', cleanedCookie.length);
      console.log('[DiscourseApiClient] Cookie前100字符:', cleanedCookie.substring(0, 100));
      console.log('[DiscourseApiClient] User-Agent:', cleanedUserAgent);

      // 先尝试用户信息端点
      let url = `${DISCOURSE_API.BASE_URL}${DISCOURSE_API.ENDPOINTS.USER_INFO}`;
      console.log('[DiscourseApiClient] 验证URL:', url);

      let response = await this.httpsRequest(url, {
        method: 'GET',
        headers: {
          'Cookie': cleanedCookie,
          'Accept': 'application/json',
          'User-Agent': cleanedUserAgent,
          'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6',
          'Accept-Encoding': 'gzip, deflate, br',
          'Cache-Control': 'max-age=0',
          'Sec-CH-UA': '"Chromium";v="142", "Microsoft Edge";v="142", "Not_A Brand";v="99"',
          'Sec-CH-UA-Arch': '"x86"',
          'Sec-CH-UA-Bitness': '"64"',
          'Sec-CH-UA-Full-Version': '"142.0.3595.94"',
          'Sec-CH-UA-Full-Version-List': '"Chromium";v="142.0.7444.176", "Microsoft Edge";v="142.0.3595.94", "Not_A Brand";v="99.0.0.0"',
          'Sec-CH-UA-Mobile': '?0',
          'Sec-CH-UA-Model': '""',
          'Sec-CH-UA-Platform': '"Windows"',
          'Sec-CH-UA-Platform-Version': '"19.0.0"',
          'Sec-Fetch-Dest': 'document',
          'Sec-Fetch-Mode': 'navigate',
          'Sec-Fetch-Site': 'same-origin',
          'Sec-Fetch-User': '?1',
          'Upgrade-Insecure-Requests': '1'
        }
      });

      console.log('[DiscourseApiClient] 验证响应状态:', response.statusCode, response.statusMessage);
      console.log('[DiscourseApiClient] 响应编码:', response.headers['content-encoding']);

      // 如果用户信息端点失败，尝试使用分类端点验证
      if (response.statusCode !== 200) {
        console.log('[DiscourseApiClient] 用户信息端点失败，尝试分类端点...');
        url = `${DISCOURSE_API.BASE_URL}${DISCOURSE_API.ENDPOINTS.CATEGORIES}`;
        console.log('[DiscourseApiClient] 备用验证URL:', url);

        response = await this.httpsRequest(url, {
          method: 'GET',
          headers: {
            'Cookie': cleanedCookie,
            'Accept': 'application/json',
            'User-Agent': cleanedUserAgent,
            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6',
            'Accept-Encoding': 'gzip, deflate, br',
            'Cache-Control': 'max-age=0',
            'Sec-CH-UA': '"Chromium";v="142", "Microsoft Edge";v="142", "Not_A Brand";v="99"',
            'Sec-CH-UA-Arch': '"x86"',
            'Sec-CH-UA-Bitness': '"64"',
            'Sec-CH-UA-Full-Version': '"142.0.3595.94"',
            'Sec-CH-UA-Full-Version-List': '"Chromium";v="142.0.7444.176", "Microsoft Edge";v="142.0.3595.94", "Not_A Brand";v="99.0.0.0"',
            'Sec-CH-UA-Mobile': '?0',
            'Sec-CH-UA-Model': '""',
            'Sec-CH-UA-Platform': '"Windows"',
            'Sec-CH-UA-Platform-Version': '"19.0.0"',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Site': 'same-origin',
            'Sec-Fetch-User': '?1',
            'Upgrade-Insecure-Requests': '1'
          }
        });

        console.log('[DiscourseApiClient] 备用验证响应状态:', response.statusCode, response.statusMessage);
      }

      console.log('[DiscourseApiClient] 响应头:', JSON.stringify(response.headers, null, 2));

      if (response.statusCode >= 200 && response.statusCode < 300) {
        try {
          const data = JSON.parse(response.body);
          console.log('[DiscourseApiClient] 验证成功，响应数据:', JSON.stringify(data, null, 2));
          return true;
        } catch (parseError: any) {
          console.error('[DiscourseApiClient] 解析响应JSON失败:', parseError.message);
          console.error('[DiscourseApiClient] 响应内容:', response.body.substring(0, 500));
          return false;
        }
      } else {
        console.error('[DiscourseApiClient] 验证失败，状态码:', response.statusCode);
        console.error('[DiscourseApiClient] 响应内容:', response.body.substring(0, 500));
        return false;
      }
    } catch (error: any) {
      console.error('[DiscourseApiClient] Cookie验证异常:', error.message);
      console.error('[DiscourseApiClient] 错误堆栈:', error.stack);
      return false;
    }
  }
}
