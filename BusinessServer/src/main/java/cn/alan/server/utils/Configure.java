package cn.alan.server.utils;

import com.mongodb.MongoClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import redis.clients.jedis.Jedis;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;


/**
 * 通过Config类来实例化bean
 * MongoDB、redis、ES客户端配置信息
 */
@Configuration
public class Configure {
    // mongoDB
    private String mongoHost;
    private int mongoPort;
    // ElasticSearch
    private String esClusterName;
    private String esHost;
    private int esPort;
    // Redis
    private String redisHost;

    public Configure() {
        try {
            // 从资源文件中加载配置信息，main/resources/application.properties
            Properties properties = new Properties();
            Resource resource = new ClassPathResource("application.properties");
            properties.load(new FileInputStream(resource.getFile()));

            // 读取属性信息
            //
            this.mongoHost = properties.getProperty("mongo.host");
            this.mongoPort = Integer.parseInt(properties.getProperty("mongo.port"));
            //
            this.esClusterName = properties.getProperty("es.cluster.name");
            this.esHost = properties.getProperty("es.host");
            this.esPort = Integer.parseInt(properties.getProperty("es.port"));
            //
            this.redisHost = properties.getProperty("redis.host");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * 获取mongoDB连接客户端
     * @return
     */
    @Bean(name = "mongoClient")
    public MongoClient getMongoClient() {
        MongoClient mongoClient = new MongoClient(mongoHost, mongoPort);
        return mongoClient;
    }

    /**
     * 获取ES连接客户端端
     * @return
     * @throws UnknownHostException
     */
    @Bean(name = "transportClient")
    public TransportClient getTransportClient() throws UnknownHostException {
        Settings settings = Settings.builder().put("cluster.name", esClusterName).build();
        TransportClient esClient = new PreBuiltTransportClient(settings);
        esClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(esHost), esPort));
        return esClient;
    }

    /**
     * 获取Redis连接客户端jedis的实例
     *
     * @return
     */
    @Bean(name = "jedis")
    public Jedis getRedisClient() {
        Jedis jedis = new Jedis(redisHost);
        return jedis;
    }
}
