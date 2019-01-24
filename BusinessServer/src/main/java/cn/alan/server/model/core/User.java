package cn.alan.server.model.core;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class User {
    // mongoDB中自动的_id，在序列化时，忽略该属性
    @JsonIgnore
    private int _id;
    //
    private int uid;
    private String userName;
    private String password;
    // 感兴趣的电影类别
    private List<String> genres = new ArrayList<>();
    // 用户是第一次登录
    private boolean isFirstLogin;

    public User() {
    }

    public User(String userName, String password, List<String> genres) {
        this.userName = userName;
        this.password = password;
        this.genres = genres;
    }

    //


    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        // 设置uid
        this.uid = userName.hashCode();
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public int get_id() {
        return _id;
    }

    public void set_id(int _id) {
        this._id = _id;
    }

    public boolean isFirstLogin() {
        return isFirstLogin;
    }

    public void setFirstLogin(boolean firstLogin) {
        isFirstLogin = firstLogin;
    }
}
