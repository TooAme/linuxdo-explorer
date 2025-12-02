export const DISCOURSE_API = {
  BASE_URL: 'https://linux.do',
  ENDPOINTS: {
    CATEGORIES: '/categories.json',
    CATEGORY_TOPICS: (categoryId: number) => `/c/${categoryId}.json`,
    LATEST_TOPICS: '/latest.json',
    TOPIC: (topicId: number) => `/t/${topicId}.json`,
    POSTS: (topicId: number) => `/t/${topicId}/posts.json`,
    USER_INFO: '/u/current.json'
  }
};
