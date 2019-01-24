package cn.alan.server.service;

import cn.alan.common.model.Constant;
import cn.alan.server.model.core.User;
import cn.alan.server.model.request.UpdateUserGenresRequest;
import cn.alan.server.model.request.UserLoginRequest;
import cn.alan.server.model.request.UserRegisterRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;

import com.mongodb.util.JSON;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 处理具体的用户有前的业务逻辑
 */
@Service
public class UserService {
    // 对象转换
    @Autowired
    private ObjectMapper objectMapper;
    // 通过注解直接获取mongoDB的连接客户端
    @Autowired
    private MongoClient mongoClient;
    // 本类中会多次使用，可以封装中一个方法
    private MongoCollection<Document> userCollection;

    private MongoCollection<Document> getUserCollection() {
        // 空则创建
        if (null == userCollection) {
            this.userCollection = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_USER_COLLECTION);
        }
        //
        return this.userCollection;
    }

    // User对象转换为MongoDB的Document
    private Document user2Document(User user) {
        try {
            Document document = Document.parse(objectMapper.writeValueAsString(user));
            return document;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Document转换为User
    private  User document2User(Document document) {
        try {
            User user = objectMapper.readValue(JSON.serialize(document), User.class);
            return user;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 用户注册服务
    public boolean userRegister(UserRegisterRequest request) {
        // 判断是否有同名的用户名已注册
        if (getUserCollection().find(new Document("userName", request.getUserName())).first() != null)
            return false;

        // 创建一个用户
        User user = new User();
        user.setUserName(request.getUserName());
        user.setPassword(request.getPassword());
        //
        user.setFirstLogin(true);

        // 向MongoDB中插入用户信息:库和表都定义为了常量
        Document document = user2Document(user);
        if (null == document) {
            return false;
        } else {
            getUserCollection().insertOne(document);
            return true;
        }
    }

    // 用户登录服务
    public boolean userLogin(UserLoginRequest request) {
        //
        Document document = getUserCollection().find(new Document("userName", request.getUserName())).first();
        if (null == document) {
            return false;
        }

        User user = document2User(document);
        if (null == user) {
            return false;
        }

        return user.getPassword().compareTo(request.getPassword()) == 0;
    }

    /**
     * 用于用户第一次登录时选择的电影类别
     *
     * @param request
     * @return
     */
    public void updateUserGenres(UpdateUserGenresRequest request) {
        getUserCollection().updateOne(new Document("username", request.getUsername()), new Document().append("$set", new Document("$genres", request.getGenres())));
        getUserCollection().updateOne(new Document("username", request.getUsername()), new Document().append("$set", new Document("$first", false)));

    }
}
