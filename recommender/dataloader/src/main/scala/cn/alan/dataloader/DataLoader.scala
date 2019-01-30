/**
  * 功能：将三个数据文件，加载到MongoDB和ElasticSearch中。
  * 步骤：
  * 1、通过Spark读取数据文件，完成数据集的分割
  * 2、使用Spark和MongoDB的驱动，将数据集写入到MongoDB中
  * 3、使用Spark和ElasticSearch的驱动，将数据集写入到ElasticSearch中。
  */

package cn.alan.dataloader

import java.net.InetAddress

import cn.alan.common.model._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.client.PreBuiltTransportClient

// 引入java中定义的常量
import cn.alan.common.model.Constant._


/*
  DataLoader
  用Spark将数据加载到mongodb和ElasticSearch
 */
object DataLoader {
  // 定义常量，三个数据文件的存放位置：工程的resources目录下。
  val MOVIE_DATA_PATH = "F:\\MyProject\\MovieRecommender\\recommender\\dataloader\\src\\main\\resources\\movies.csv"
  val RATING_DATA_PATH = "F:\\MyProject\\MovieRecommender\\recommender\\dataloader\\src\\main\\resources\\ratings.csv"
  val TAG_DATA_PATH = "F:\\MyProject\\MovieRecommender\\recommender\\dataloader\\src\\main\\resources\\tags.csv"

  // 程序入口
  def main(args: Array[String]): Unit = {
    /**
      * 配置信息：
      * 1、Spark相关配置信息
      * 2、mongoDB服务器连接（mongodb://192.168.154.101:27017/recommender）、数据库（名为：recommender）
      * 3、ES服务器及索引（名为recommender）
      */

    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> {
        "mongodb://192.168.154.101:27017/" + MONGODB_DATABASE
      },
      "mongo.db" -> MONGODB_DATABASE,
      "es.httpHosts" -> "192.168.154.101:9200",
      "es.transportHosts" -> "192.168.154.101:9300",
      "es.index" -> ES_INDEX,
      "es.cluster.name" -> ES_CLUSTER_NAME
    )

    // 需要创建一个SparkConf配置
    val sparkConf = new SparkConf().setAppName("DataLoader").setMaster(config.get("spark.cores").get)
    // 创建一个SparkSession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    import spark.implicits._

    // 将Movie、Rating、Tag数据集加载进来
    val movieRDD = spark.sparkContext.textFile(MOVIE_DATA_PATH)
    //将MovieRDD装换为DataFrame
    val movieDF = movieRDD.map(item => {
      val attr = item.split("\\^")
      Movie(attr(0).toInt, attr(1).trim, attr(2).trim, attr(3).trim, attr(4).trim, attr(5).trim, attr(6).trim, attr(7).trim, attr(8).trim, attr(9).trim)
    }).toDF()

    val ratingRDD = spark.sparkContext.textFile(RATING_DATA_PATH)
    //将ratingRDD转换为DataFrame
    val ratingDF = ratingRDD.map(item => {
      val attr = item.split(",")
      MovieRating(attr(0).toInt, attr(1).toInt, attr(2).toDouble, attr(3).toInt)
    }).toDF()

    val tagRDD = spark.sparkContext.textFile(TAG_DATA_PATH)
    //将tagRDD装换为DataFrame
    val tagDF = tagRDD.map(item => {
      val attr = item.split(",")
      Tag(attr(0).toInt, attr(1).toInt, attr(2).trim, attr(3).toInt)
    }).toDF()

    // 读取MongoDB连接信息
    implicit val mongoConfig = MongoConfig(config.get("mongo.uri").get, config.get("mongo.db").get)

    // 需要将数据保存到MongoDB中
    storeDataInMongoDB(movieDF, ratingDF, tagDF)

    // 存入ES中，需要将Movie数据集与Tag数据集进行左连接处理后，再存入。
    /**
      * Movie数据集，数据集字段通过分割
      *
      * 151^                          电影的ID
      * Rob Roy (1995)^               电影的名称
      * In the highlands ....^        电影的描述
      * 139 minutes^                  电影的时长
      * August 26, 1997^              电影的发行日期
      * 1995^                         电影的拍摄日期
      * English ^                     电影的语言
      * Action|Drama|Romance|War ^    电影的类型
      * Liam Neeson|Jessica Lange...  电影的演员
      * Michael Caton-Jones           电影的导演
      *
      * tag1|tag2|tag3|....           电影的Tag
      **/

    // 首先需要将Tag数据集进行处理，  处理后的形式为  MID ， tag1|tag2|tag3     tag1   tag2  tag3
    import org.apache.spark.sql.functions._

    /**
      * MID , Tags
      * 1     tag1|tag2|tag3|tag4....
      */
    val newTag = tagDF.groupBy($"mid").agg(concat_ws("|", collect_set($"tag")).as("tags")).select("mid", "tags")

    // 需要将处理后的Tag数据，和Moive数据融合，产生新的Movie数据，使用左连接，没有tags的电影也需要保留
    val movieWithTagsDF = movieDF.join(newTag, Seq("mid", "mid"), "left")

    // 声明了一个ES配置的隐式参数
    implicit val esConfig = ESConfig(config.get("es.httpHosts").get,
      config.get("es.transportHosts").get,
      config.get("es.index").get,
      config.get("es.cluster.name").get)

    // 需要将新的Movie数据保存到ES中
    storeDataInES(movieWithTagsDF)

    // 关闭Spark
    spark.stop()
  }

  // 将数据保存到MongoDB中的方法
  def storeDataInMongoDB(movieDF: DataFrame, ratingDF: DataFrame, tagDF: DataFrame)(implicit mongoConfig: MongoConfig): Unit = {

    //新建一个到MongoDB的连接
    val mongoClient = MongoClient(MongoClientURI(mongoConfig.uri))

    //如果MongoDB中有对应的数据库，那么应该删除
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).dropCollection()

    //将当前数据写入到MongoDB
    movieDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_MOVIE_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    ratingDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    tagDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_TAG_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //对数据表建索引
    // 电影：只需要mid
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    // 评分：联合索引（uid,mid)
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("uid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    // Tag:联合索引（uid,mid)
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).createIndex(MongoDBObject("uid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).createIndex(MongoDBObject("mid" -> 1))

    //关闭MongoDB的连接
    mongoClient.close()
  }

  // 将数据保存到ES中的方法
  def storeDataInES(movieDF: DataFrame)(implicit eSConfig: ESConfig): Unit = {

    //新建一个配置
    val settings: Settings = Settings.builder().put("cluster.name", eSConfig.clustername).build()

    //新建一个ES的客户端
    val esClient = new PreBuiltTransportClient(settings)

    //需要将TransportHosts添加到esClient中
    //  "es.httpHosts" -> "192.168.154.101:9200",
    //      "es.transportHosts" -> "192.168.154.101:9300"
    // 使用正则表达式将IP和端口号分拆开
    val REGEX_HOST_PORT = "(.+):(\\d+)".r
    eSConfig.transportHosts.split(",").foreach {
      case REGEX_HOST_PORT(host: String, port: String) => {
        esClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port.toInt))
      }
    }

    //需要清除掉ES中遗留的数据
    if (esClient.admin().indices().exists(new IndicesExistsRequest(eSConfig.index)).actionGet().isExists) {
      esClient.admin().indices().delete(new DeleteIndexRequest(eSConfig.index))
    }
    esClient.admin().indices().create(new CreateIndexRequest(eSConfig.index))

    //将数据写入到ES中
    movieDF
      .write
      .option("es.nodes", eSConfig.httpHosts)
      .option("es.http.timeout", "100m")
      .option("es.mapping.id", "mid")
      .mode("overwrite")
      .format("org.elasticsearch.spark.sql")
      .save(eSConfig.index + "/" + ES_INDEX)

    // 关闭客户端连接
    esClient.close()
  }
}
