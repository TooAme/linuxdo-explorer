package com.linuxdo.explorer.api;

import com.google.gson.*;
import com.linuxdo.explorer.model.*;
import com.linuxdo.explorer.settings.LinuxDoSettings;
import com.linuxdo.explorer.util.LinuxDoBundle;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Linux.do API 客户端
 */
public class DiscourseApiClient {

    private static final String BASE_URL = "https://linux.do";

    private final OkHttpClient httpClient;
    private final Gson gson;

    public DiscourseApiClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();

        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .setLenient()  // 允许宽松的 JSON 解析
                .create();
    }

    private String getCookie() {
        String cookie = LinuxDoSettings.getInstance().getCookie();
        return cookie != null ? cookie.trim() : "";
    }

    private String getUserAgent() {
        String ua = LinuxDoSettings.getInstance().getUserAgent();
        if (ua == null || ua.trim().isEmpty()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        }
        return ua.trim();
    }

    private Request.Builder createRequestBuilder(String url) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", getUserAgent())
                .header("Accept", "application/json")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        
        String cookie = getCookie();
        if (!cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }
        
        return builder;
    }

    private String executeRequest(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();
            
            if (code == 401 || code == 403) {
                throw new IOException(LinuxDoBundle.message("message.retry"));
            }
            
            if (!response.isSuccessful()) {
                throw new IOException(LinuxDoBundle.message("error.requestFailed", code, response.message()));
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException(LinuxDoBundle.message("error.emptyResponseBody"));
            }
            
            String responseText = body.string();
            
            // 检查是否是 HTML 页面（未登录时可能返回登录页面）
            if (responseText.trim().startsWith("<!DOCTYPE") || responseText.trim().startsWith("<html")) {
                throw new IOException(LinuxDoBundle.message("error.htmlInsteadOfJson"));
            }
            
            // 检查是否为空
            if (responseText.trim().isEmpty()) {
                throw new IOException(LinuxDoBundle.message("error.emptyResponse"));
            }
            
            return responseText;
        }
    }

    /**
     * 安全解析 JSON
     */
    private JsonObject parseJson(String json) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new IOException(LinuxDoBundle.message("error.invalidJson"));
            }
            return element.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            // 打印前100个字符帮助调试
            String preview = json.length() > 100 ? json.substring(0, 100) + "..." : json;
            throw new IOException(LinuxDoBundle.message("error.jsonParseFailed", e.getMessage(), preview));
        }
    }

    /**
     * 获取分类列表
     */
    public List<Category> getCategories() throws IOException {
        String cookie = getCookie();
        if (cookie.isEmpty()) {
            throw new IOException(LinuxDoBundle.message("message.configCookie"));
        }

        String url = BASE_URL + "/categories.json";
        Request request = createRequestBuilder(url).get().build();
        String json = executeRequest(request);

        JsonObject root = parseJson(json);
        
        if (!root.has("category_list")) {
            throw new IOException(LinuxDoBundle.message("error.missingCategoryList"));
        }
        
        JsonObject categoryList = root.getAsJsonObject("category_list");
        JsonArray categories = categoryList.getAsJsonArray("categories");

        List<Category> result = new ArrayList<>();
        if (categories != null) {
            for (JsonElement element : categories) {
                JsonObject catObj = element.getAsJsonObject();
                Category category = new Category();
                category.setId(getIntSafe(catObj, "id", 0));
                category.setName(getStringSafe(catObj, "name", LinuxDoBundle.message("fallback.unknownCategory")));
                category.setSlug(getStringSafe(catObj, "slug", ""));
                category.setColor(getStringSafe(catObj, "color", ""));
                category.setTopicCount(getIntSafe(catObj, "topic_count", 0));
                result.add(category);
            }
        }
        return result;
    }

    /**
     * 获取最新话题列表
     */
    public List<Topic> getLatestTopics(int page) throws IOException {
        String url = BASE_URL + "/latest.json?page=" + page;
        Request request = createRequestBuilder(url).get().build();
        String json = executeRequest(request);

        return parseTopics(json);
    }

    /**
     * 获取分类话题列表
     */
    public List<Topic> getCategoryTopics(int categoryId, int page) throws IOException {
        String url = BASE_URL + "/c/" + categoryId + ".json?page=" + page;
        Request request = createRequestBuilder(url).get().build();
        String json = executeRequest(request);

        return parseTopics(json);
    }

    /**
     * 搜索话题
     */
    public List<Topic> searchTopics(String query, int page) throws IOException {
        String encodedQuery;
        try {
            encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            encodedQuery = query;
        }
        String url = BASE_URL + "/search.json?q=" + encodedQuery + "&page=" + page;
        Request request = createRequestBuilder(url).get().build();
        String json = executeRequest(request);

        return parseSearchResults(json);
    }

    private List<Topic> parseSearchResults(String json) throws IOException {
        JsonObject root = parseJson(json);
        
        List<Topic> result = new ArrayList<>();
        
        // 搜索结果中的 topics 数组
        if (root.has("topics")) {
            JsonArray topics = root.getAsJsonArray("topics");
            if (topics != null) {
                for (JsonElement element : topics) {
                    JsonObject topicObj = element.getAsJsonObject();
                    Topic topic = new Topic();
                    topic.setId(getIntSafe(topicObj, "id", 0));
                    topic.setTitle(getStringSafe(topicObj, "title", LinuxDoBundle.message("fallback.untitled")));
                    topic.setSlug(getStringSafe(topicObj, "slug", ""));
                    topic.setPostsCount(getIntSafe(topicObj, "posts_count", 0));
                    topic.setReplyCount(getIntSafe(topicObj, "reply_count", 0));
                    topic.setViews(getIntSafe(topicObj, "views", 0));
                    topic.setLikeCount(getIntSafe(topicObj, "like_count", 0));
                    topic.setCategoryId(getIntSafe(topicObj, "category_id", 0));
                    topic.setPinned(getBooleanSafe(topicObj, "pinned", false));
                    result.add(topic);
                }
            }
        }
        
        return result;
    }

    private List<Topic> parseTopics(String json) throws IOException {
        JsonObject root = parseJson(json);
        
        if (!root.has("topic_list")) {
            return Collections.emptyList();
        }
        
        JsonObject topicList = root.getAsJsonObject("topic_list");
        JsonArray topics = topicList.getAsJsonArray("topics");

        List<Topic> result = new ArrayList<>();
        if (topics != null) {
            for (JsonElement element : topics) {
                JsonObject topicObj = element.getAsJsonObject();
                Topic topic = new Topic();
                topic.setId(getIntSafe(topicObj, "id", 0));
                topic.setTitle(getStringSafe(topicObj, "title", LinuxDoBundle.message("fallback.untitled")));
                topic.setSlug(getStringSafe(topicObj, "slug", ""));
                topic.setPostsCount(getIntSafe(topicObj, "posts_count", 0));
                topic.setReplyCount(getIntSafe(topicObj, "reply_count", 0));
                topic.setViews(getIntSafe(topicObj, "views", 0));
                topic.setLikeCount(getIntSafe(topicObj, "like_count", 0));
                topic.setCategoryId(getIntSafe(topicObj, "category_id", 0));
                topic.setPinned(getBooleanSafe(topicObj, "pinned", false));
                result.add(topic);
            }
        }
        return result;
    }

    /**
     * 获取话题详情
     */
    public TopicDetail getTopicDetail(int topicId) throws IOException {
        String url = BASE_URL + "/t/" + topicId + ".json";
        Request request = createRequestBuilder(url).get().build();
        String json = executeRequest(request);

        JsonObject root = parseJson(json);
        TopicDetail detail = new TopicDetail();
        detail.setId(getIntSafe(root, "id", topicId));
        detail.setTitle(getStringSafe(root, "title", ""));
        detail.setPostsCount(getIntSafe(root, "posts_count", 0));

        // 解析帖子
        if (root.has("post_stream")) {
            JsonObject postStream = root.getAsJsonObject("post_stream");
            
            // 解析 stream (所有帖子 ID)
            if (postStream.has("stream")) {
                JsonArray streamArr = postStream.getAsJsonArray("stream");
                List<Integer> stream = new ArrayList<>();
                for (JsonElement e : streamArr) {
                    stream.add(e.getAsInt());
                }
                detail.setStream(stream);
            }

            // 解析已加载的帖子
            if (postStream.has("posts")) {
                JsonArray postsArr = postStream.getAsJsonArray("posts");
                List<Post> posts = new ArrayList<>();
                for (JsonElement e : postsArr) {
                    posts.add(parsePost(e.getAsJsonObject()));
                }
                detail.setPosts(posts);
            }
        }

        return detail;
    }

    /**
     * 加载更多帖子
     */
    public List<Post> loadMorePosts(int topicId, List<Integer> postIds) throws IOException {
        StringBuilder sb = new StringBuilder(BASE_URL + "/t/" + topicId + "/posts.json?");
        for (int i = 0; i < postIds.size(); i++) {
            if (i > 0) sb.append("&");
            sb.append("post_ids[]=").append(postIds.get(i));
        }

        Request request = createRequestBuilder(sb.toString()).get().build();
        String json = executeRequest(request);

        JsonObject root = parseJson(json);
        List<Post> posts = new ArrayList<>();

        if (root.has("post_stream")) {
            JsonObject postStream = root.getAsJsonObject("post_stream");
            if (postStream.has("posts")) {
                JsonArray postsArr = postStream.getAsJsonArray("posts");
                for (JsonElement e : postsArr) {
                    posts.add(parsePost(e.getAsJsonObject()));
                }
            }
        }

        return posts;
    }

    private Post parsePost(JsonObject postObj) {
        Post post = new Post();
        post.setId(getIntSafe(postObj, "id", 0));
        post.setUsername(getStringSafe(postObj, "username", LinuxDoBundle.message("fallback.anonymous")));
        post.setCooked(getStringSafe(postObj, "cooked", ""));
        post.setPostNumber(getIntSafe(postObj, "post_number", 0));
        post.setCreatedAt(getStringSafe(postObj, "created_at", ""));
        post.setLikeCount(getIntSafe(postObj, "like_count", 0));
        post.setReplyCount(getIntSafe(postObj, "reply_count", 0));
        return post;
    }

    /**
     * 获取通知列表
     */
    public List<Notification> getNotifications() throws IOException {
        String cookie = getCookie();
        if (cookie.isEmpty()) {
            return Collections.emptyList();
        }

        String url = BASE_URL + "/notifications.json";
        Request request = createRequestBuilder(url).get().build();
        
        try {
            String json = executeRequest(request);
            JsonObject root = parseJson(json);
            
            if (!root.has("notifications")) {
                return Collections.emptyList();
            }
            
            JsonArray notificationsArr = root.getAsJsonArray("notifications");

            List<Notification> result = new ArrayList<>();
            if (notificationsArr != null) {
                for (JsonElement element : notificationsArr) {
                    JsonObject notifObj = element.getAsJsonObject();
                    Notification notification = new Notification();
                    notification.setId(getIntSafe(notifObj, "id", 0));
                    notification.setNotificationType(getIntSafe(notifObj, "notification_type", 0));
                    notification.setRead(getBooleanSafe(notifObj, "read", false));
                    notification.setCreatedAt(getStringSafe(notifObj, "created_at", ""));
                    notification.setTopicId(getIntSafe(notifObj, "topic_id", 0));
                    notification.setSlug(getStringSafe(notifObj, "slug", ""));
                    notification.setPostNumber(getIntSafe(notifObj, "post_number", 0));

                    if (notifObj.has("data") && notifObj.get("data").isJsonObject()) {
                        JsonObject dataObj = notifObj.getAsJsonObject("data");
                        Notification.NotificationData data = new Notification.NotificationData();
                        data.setTopicTitle(getStringSafe(dataObj, "topic_title", ""));
                        data.setDisplayUsername(getStringSafe(dataObj, "display_username", ""));
                        data.setOriginalUsername(getStringSafe(dataObj, "original_username", ""));
                        notification.setData(data);
                    }

                    result.add(notification);
                }
            }

            return result;
        } catch (IOException e) {
            // 通知获取失败不影响其他功能
            System.err.println("获取通知失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 验证 Cookie 是否有效
     */
    public boolean validateCookie(String cookie, String userAgent) {
        if (cookie == null || cookie.trim().isEmpty()) {
            return false;
        }
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return false;
        }

        try {
            String url = BASE_URL + "/session/current.json";
            Request request = new Request.Builder()
                    .url(url)
                    .header("Cookie", cookie.trim())
                    .header("User-Agent", userAgent.trim())
                    .header("Accept", "application/json")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }
                
                ResponseBody body = response.body();
                if (body == null) {
                    return false;
                }
                
                String responseText = body.string();
                // 确保返回的是 JSON 而非 HTML
                return !responseText.trim().startsWith("<!DOCTYPE") && 
                       !responseText.trim().startsWith("<html");
            }
        } catch (Exception e) {
            System.err.println("Cookie 验证失败: " + e.getMessage());
            return false;
        }
    }

    // ============ 辅助方法 ============

    private String getStringSafe(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    private int getIntSafe(JsonObject obj, String key, int defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean getBooleanSafe(JsonObject obj, String key, boolean defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsBoolean();
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
