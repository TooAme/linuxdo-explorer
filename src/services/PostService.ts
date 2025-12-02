import { DiscourseApiClient } from '../api/DiscourseApiClient';
import type { Post } from '../api/ApiTypes';

export class PostService {
  constructor(private apiClient: DiscourseApiClient) {}

  async getTopicPosts(topicId: number): Promise<Post[]> {
    try {
      return await this.apiClient.getTopicPosts(topicId);
    } catch (error: any) {
      throw new Error(`获取话题回复失败: ${error.message}`);
    }
  }
}
