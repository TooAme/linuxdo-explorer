package com.linuxdo.explorer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 话题详情数据模型
 */
public class TopicDetail {
    private int id;
    private String title;
    private int postsCount;
    private List<Integer> stream = new ArrayList<>();
    private List<Post> posts = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPostsCount() {
        return postsCount;
    }

    public void setPostsCount(int postsCount) {
        this.postsCount = postsCount;
    }

    public List<Integer> getStream() {
        return stream;
    }

    public void setStream(List<Integer> stream) {
        this.stream = stream;
    }

    public List<Post> getPosts() {
        return posts;
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts;
    }
}
