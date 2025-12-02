import { DiscourseApiClient } from '../api/DiscourseApiClient';
import type { Topic } from '../api/ApiTypes';

export class TopicService {
  constructor(private apiClient: DiscourseApiClient) { }

  async getLatestTopics(page: number = 0, limit: number = 20): Promise<Topic[]> {
    try {
      const topics = await this.apiClient.getLatestTopics(page);
      // 限制返回的话题数量
      return topics.slice(0, limit);
    } catch (error: any) {
      throw new Error(`获取最新话题失败: ${error.message}`);
    }
  }

  async getCategoryTopics(categoryId: number, page: number = 0, limit: number = 20): Promise<Topic[]> {
    try {
      const topics = await this.apiClient.getCategoryTopics(categoryId, page);
      // 限制返回的话题数量
      console.log(`[TopicService] 获取到 ${topics.length} 个话题，限制为 ${limit} 个`);
      return topics.slice(0, limit);
    } catch (error: any) {
      throw new Error(`获取分类话题失败: ${error.message}`);
    }
  }
}
