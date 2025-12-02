export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string;
  topic_count: number;
  color: string;
  text_color: string;
}

export interface CategoriesResponse {
  category_list: {
    categories: Category[];
  };
}

export interface Topic {
  id: number;
  title: string;
  slug: string;
  posts_count: number;
  reply_count: number;
  views: number;
  created_at: string;
  last_posted_at: string;
  pinned: boolean;
  closed: boolean;
  category_id: number;
}

export interface TopicListResponse {
  topic_list: {
    topics: Topic[];
  };
}

export interface Post {
  id: number;
  username: string;
  created_at: string;
  cooked: string;
  post_number: number;
  reply_count: number;
  quote_count: number;
}

export interface TopicDetail {
  id: number;
  title: string;
  posts_count: number;
  post_stream: {
    posts: Post[];
    stream: number[];  // 所有 post ID 的数组
  };
}

export interface PostsResponse {
  post_stream: {
    posts: Post[];
  };
}

export interface Notification {
  id: number;
  notification_type: number;
  read: boolean;
  created_at: string;
  post_number: number;
  topic_id: number;
  slug: string;
  data: {
    topic_title: string;
    original_post_id: number;
    original_post_type: number;
    original_username: string;
    display_username: string;
  };
}

export interface NotificationsResponse {
  notifications: Notification[];
}
