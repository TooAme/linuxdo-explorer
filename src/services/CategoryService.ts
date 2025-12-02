import { DiscourseApiClient } from '../api/DiscourseApiClient';
import type { Category } from '../api/ApiTypes';

export class CategoryService {
  constructor(private apiClient: DiscourseApiClient) { }

  async getCategories(): Promise<Category[]> {
    try {
      const categories = await this.apiClient.getCategories();
      // 过滤掉一些不需要显示的系统分类
      return categories.filter(cat => !cat.slug.includes('uncategorized'));
    } catch (error: any) {
      throw new Error(`${error.message}`);
    }
  }
}
