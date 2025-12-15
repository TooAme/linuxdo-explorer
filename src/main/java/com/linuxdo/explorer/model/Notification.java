package com.linuxdo.explorer.model;

import com.linuxdo.explorer.util.LinuxDoBundle;

/**
 * 通知数据模型
 */
public class Notification {
    private int id;
    private int notificationType;
    private boolean read;
    private String createdAt;
    private int topicId;
    private String slug;
    private int postNumber;
    private NotificationData data;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(int notificationType) {
        this.notificationType = notificationType;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getTopicId() {
        return topicId;
    }

    public void setTopicId(int topicId) {
        this.topicId = topicId;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public int getPostNumber() {
        return postNumber;
    }

    public void setPostNumber(int postNumber) {
        this.postNumber = postNumber;
    }

    public NotificationData getData() {
        return data;
    }

    public void setData(NotificationData data) {
        this.data = data;
    }

    public String getUrl() {
        return "https://linux.do/t/" + slug + "/" + topicId + "/" + postNumber;
    }

    public String getDisplayText() {
        String username = data != null
                ? (data.getDisplayUsername() != null ? data.getDisplayUsername() : data.getOriginalUsername())
                : null;
        if (username == null || username.isBlank()) {
            username = LinuxDoBundle.message("notification.unknownUser");
        }

        String topicTitle = data != null ? data.getTopicTitle() : null;
        if (topicTitle == null || topicTitle.isBlank()) {
            topicTitle = LinuxDoBundle.message("notification.unknownTopic");
        }

        switch (notificationType) {
            case 1:
                return LinuxDoBundle.message("notification.mentioned", username, topicTitle);
            case 2:
                return LinuxDoBundle.message("notification.repliedToYourPostIn", username, topicTitle);
            case 5:
                return LinuxDoBundle.message("notification.repliedToTopic", username, topicTitle);
            case 6:
                return LinuxDoBundle.message("notification.likedYourPost", username);
            case 9:
                return LinuxDoBundle.message("notification.repliedToYou", username);
            default:
                return LinuxDoBundle.message("notification.generic", username, topicTitle);
        }
    }

    public static class NotificationData {
        private String topicTitle;
        private String originalPostId;
        private String originalPostType;
        private String originalUsername;
        private String displayUsername;

        public String getTopicTitle() {
            return topicTitle;
        }

        public void setTopicTitle(String topicTitle) {
            this.topicTitle = topicTitle;
        }

        public String getOriginalPostId() {
            return originalPostId;
        }

        public void setOriginalPostId(String originalPostId) {
            this.originalPostId = originalPostId;
        }

        public String getOriginalPostType() {
            return originalPostType;
        }

        public void setOriginalPostType(String originalPostType) {
            this.originalPostType = originalPostType;
        }

        public String getOriginalUsername() {
            return originalUsername;
        }

        public void setOriginalUsername(String originalUsername) {
            this.originalUsername = originalUsername;
        }

        public String getDisplayUsername() {
            return displayUsername;
        }

        public void setDisplayUsername(String displayUsername) {
            this.displayUsername = displayUsername;
        }
    }
}
