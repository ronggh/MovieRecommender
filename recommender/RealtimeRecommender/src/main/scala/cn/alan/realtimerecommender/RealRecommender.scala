package cn.alan.realtimerecommender

import cn.alan.common.model.{MongoConfig, MovieRecs}
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import redis.clients.jedis.Jedis

// 用于将Jedis的对象类型转换为scala可操作的类型
import scala.collection.JavaConversions._
// 引入java中定义的常量
import cn.alan.common.model.Constant._

// Jedis和mongoDB连接实例
object ConnHelper extends Serializable {
  lazy val jedis = new Jedis("192.168.154.101")
  lazy val mongoClient = MongoClient(MongoClientURI("mongodb://192.168.154.101:27017/"+MONGODB_DATABASE))
}


object RealRecommender {
  //
  val MAX_USER_RATINGS_NUM = 20
  val MAX_SIM_MOVIES_NUM = 20


  //
  def main(args: Array[String]): Unit = {
    // 配置信息
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> {"mongodb://192.168.154.101:27017/"+MONGODB_DATABASE},
      "mongo.db" -> MONGODB_DATABASE,
      "kafka.topic" -> "recommender"
    )
    //创建一个SparkConf配置
    val sparkConf = new SparkConf().setAppName("RealRecommender").setMaster(config("spark.cores"))

    //创建Spark的对象
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    val sc = spark.sparkContext
    val ssc = new StreamingContext(sc, Seconds(2))

    implicit val mongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))
    import spark.implicits._

    //******************  电影相似度矩阵，使用scala中的广播变量实现下发到所有worker机
    // 从MongoDB中读取电影相似度矩阵，转换成为： Map[Int, Map[Int,Double]]格式
    val simMoviesMatrix = spark
      .read
      .option("uri", config("mongo.uri"))
      .option("collection", MONGODB_MOVIE_RECS_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRecs]
      .rdd
      .map { recs =>
        (recs.uid, recs.recs.map(x => (x.rid, x.r)).toMap)
      }.collectAsMap()

    // 广播变量
    val simMoviesMatrixBroadCast = sc.broadcast(simMoviesMatrix)

    // 调用一下，触发广播变量内容的执行
    val abc = sc.makeRDD(1 to 2)
    abc.map(x => simMoviesMatrixBroadCast.value.get(1)).count()

    //******************

    // Kafka的连接配置
    val kafkaPara = Map(
      "bootstrap.servers" -> "192.168.154.101:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "recommender",
      "auto.offset.reset" -> "latest"
    )

    // 创建到Kafka的连接
    val kafkaStream = KafkaUtils.createDirectStream[String, String](ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](Array(config("kafka.topic")),
        kafkaPara))

    // 产生评分流，格式：UID|MID|SCORE|TIMESTAMP
    val ratingStream = kafkaStream.map { case msg =>
      var attr = msg.value().split("\\|")
      (attr(0).toInt, attr(1).toInt, attr(2).toDouble, attr(3).toInt)
    }

    ratingStream.foreachRDD { rdd =>
      rdd.map { case (uid, mid, score, timestamp) =>
        println(">>>>>>>>>>>>>>>>")

        //获取当前最近的M次电影评分
        val userRecentlyRatings = getUserRecentlyRating(MAX_USER_RATINGS_NUM, uid, ConnHelper.jedis)

        //获取电影P最相似的K个电影
        val simMovies = getTopSimMovies(MAX_SIM_MOVIES_NUM, mid, uid, simMoviesMatrixBroadCast.value)

        //计算待选电影的推荐优先级
        val streamRecs = computeMovieScores(simMoviesMatrixBroadCast.value,
          userRecentlyRatings,
          simMovies)

        //将数据保存到MongoDB
        saveRecsToMongoDB(uid, streamRecs)

      }.count()
    }

    //启动Streaming程序
    ssc.start()
    ssc.awaitTermination()

  }

  /**
    * 将数据保存到MongoDB    uid -> 1,  recs -> 22:4.5|45:3.8
    *
    * @param streamRecs 流式的推荐结果
    * @param mongConfig MongoDB的配置
    */
  def saveRecsToMongoDB(uid: Int, streamRecs: Array[(Int, Double)])(implicit mongConfig: MongoConfig): Unit = {
    //到StreamRecs的连接
    val streaRecsCollection = ConnHelper.mongoClient(mongConfig.db)(MONGODB_STREAM_RECS_COLLECTION)

    streaRecsCollection.findAndRemove(MongoDBObject("uid" -> uid))
    streaRecsCollection.insert(MongoDBObject("uid" -> uid, "recs" -> streamRecs.map(x => x._1 + ":" + x._2).mkString("|")))

  }

  /**
    * 计算待选电影的推荐分数
    *
    * @param simMovies           电影相似度矩阵
    * @param userRecentlyRatings 用户最近的k次评分
    * @param topSimMovies        当前电影最相似的K个电影
    * @return
    */
  def computeMovieScores(simMovies: scala.collection.Map[Int, scala.collection.immutable.Map[Int, Double]], userRecentlyRatings: Array[(Int, Double)], topSimMovies: Array[Int]): Array[(Int, Double)] = {

    //用于保存每一个待选电影和最近评分的每一个电影的权重得分
    val score = scala.collection.mutable.ArrayBuffer[(Int, Double)]()

    //用于保存每一个电影的增强因子数
    val increMap = scala.collection.mutable.HashMap[Int, Int]()

    //用于保存每一个电影的减弱因子数
    val decreMap = scala.collection.mutable.HashMap[Int, Int]()

    for (topSimMovie <- topSimMovies; userRecentlyRating <- userRecentlyRatings) {
      val simScore = getMoviesSimScore(simMovies, userRecentlyRating._1, topSimMovie)
      // 相似度大于0.6
      if (simScore > 0.6) {
        score += ((topSimMovie, simScore * userRecentlyRating._2))
        // 评分大于3，增强因子加1;否则，减弱因子加1
        if (userRecentlyRating._2 > 3) {
          increMap(topSimMovie) = increMap.getOrDefault(topSimMovie, 0) + 1
        } else {
          decreMap(topSimMovie) = decreMap.getOrDefault(topSimMovie, 0) + 1
        }
      }
    }

    score.groupBy(_._1).map { case (mid, sims) =>
      (mid, sims.map(_._2).sum / sims.length + log(increMap(mid)) - log(decreMap(mid)))
    }.toArray

  }

  //取2的对数
  def log(m: Int): Double = {
    math.log(m) / math.log(2)
  }

  /**
    * 获取当个电影之间的相似度
    *
    * @param simMovies       电影相似度矩阵
    * @param userRatingMovie 用户已经评分的电影
    * @param topSimMovie     候选电影
    * @return
    */
  def getMoviesSimScore(simMovies: scala.collection.Map[Int, scala.collection.immutable.Map[Int, Double]], userRatingMovie: Int, topSimMovie: Int): Double = {
    simMovies.get(topSimMovie) match {
      case Some(sim) => sim.get(userRatingMovie) match {
        case Some(score) => score
        case None => 0.0
      }
      case None => 0.0
    }
  }

  /**
    * 获取当前电影K个相似的电影
    *
    * @param num         相似电影的数量
    * @param mid         当前电影的ID
    * @param uid         当前的评分用户
    * @param simMovies   电影相似度矩阵的广播变量值
    * @param mongoConfig MongoDB的配置
    * @return
    */
  def getTopSimMovies(num: Int, mid: Int, uid: Int, simMovies: scala.collection.Map[Int, scala.collection.immutable.Map[Int, Double]])(implicit mongoConfig: MongoConfig): Array[Int] = {
    //从广播变量的电影相似度矩阵中获取当前电影所有的相似电影
    val allSimMovies = simMovies.get(mid).get.toArray
    //获取用户已经观看过得电影
    val ratingExist = ConnHelper.mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).find(MongoDBObject("uid" -> uid)).toArray.map { item =>
      item.get("mid").toString.toInt
    }
    //过滤掉已经评分过得电影，并排序输出
    allSimMovies.filter(x => !ratingExist.contains(x._1)).sortWith(_._2 > _._2).take(num).map(x => x._1)
  }

  /**
    * 获取当前最近的M次电影评分
    *
    * @param num 评分的个数
    * @param uid 用户
    * @return Seq[（mid,score）]
    */
  def getUserRecentlyRating(num: Int, uid: Int, jedis: Jedis): Array[(Int, Double)] = {
    //从用户的队列中取出num个评论  => 数据存在Redis服务器中
    // 格式：
    // uid:1 [(mid:score)]
    // 将List<String> 转换为 Array[(Int,Double)]格式
    jedis.lrange("uid:" + uid.toString, 0, num).map { item =>
      val attr = item.split("\\|")
      (attr(0).trim.toInt, attr(1).trim.toDouble)
    }.toArray
  }

}
