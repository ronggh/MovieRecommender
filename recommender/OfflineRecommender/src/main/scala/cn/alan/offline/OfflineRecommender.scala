package cn.alan.offline

/**
  * 离线推荐计算 —— 从mongoDB，电影及评分表中读取数据计算：
  * 1、用户电影推荐矩阵
  * 2、电影相似度矩阵
  */

import cn.alan.common.model._
import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, MatrixFactorizationModel, Rating}
import org.apache.spark.sql.SparkSession
import org.jblas.DoubleMatrix

// 引入java中定义的常量
import cn.alan.common.model.Constant._


object OfflineRecommender {
    // 推荐数量
  val USER_MAX_RECOMMENDATION = 10

  def main(args: Array[String]): Unit = {
    // 配置信息
    val config = Map(
      "spark.cores" -> "local[*]",
      //
      "mongo.uri" -> {"mongodb://192.168.154.101:27017/" + MONGODB_DATABASE},
      //"mongo.uri" -> "mongodb://localhost:27017/recommender",
      "mongo.db" -> MONGODB_DATABASE
    )

    //创建一个SparkConf配置，内存设置过小，会导致OutofMemory错误
    val sparkConf = new SparkConf().setAppName("OfflineRecommender")
      .setMaster(config("spark.cores")).set("spark.executor.memory", "6G").set("spark.driver.memory", "2G")

    //基于SparkConf创建一个SparkSession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    //创建一个MongoDBConfig
    val mongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))

    import spark.implicits._

    //读取mongoDB中评分表的业务数据,转换成三元组（uid,mid,score)
    val ratingRDD = spark
      .read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRating]
      .rdd
      .map(rating => (rating.uid, rating.mid, rating.score))

    //用户的数据集 RDD[Int],从ratingRdd中得出
    val userRDD = ratingRDD.map(_._1).distinct()

    //电影数据集 RDD[Int]
    val movieRDD = spark
      .read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_MOVIE_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Movie]
      .rdd
      .map(_.mid) // 只需要电影ID即可。

    //创建训练数据集
    val trainData = ratingRDD.map(x => Rating(x._1, x._2, x._3))
    val (rank, iterations, lambda) = (50, 5, 0.01)
    //训练ALS模型
    val model = ALS.train(trainData, rank, iterations, lambda)


    //需要构造一个usersProducts  RDD[(Int,Int)]，cartesian：笛卡尔积
    val userMovies = userRDD.cartesian(movieRDD)
    val preRatings = model.predict(userMovies)

    //计算用户推荐矩阵
    val userRecs = preRatings
      .filter(_.rating > 0)
      .map(rating => (rating.user, (rating.product, rating.rating)))
      .groupByKey()
      .map {
        case (uid, recs) => UserRecs(uid, recs.toList.sortWith(_._2 > _._2).take(USER_MAX_RECOMMENDATION).map(x => Recommendation(x._1, x._2)))
      }
      .toDF()

    userRecs.write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_USER_RECS_COLLECTION )
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    // 计算电影相似度矩阵
    //获取电影的特征矩阵
    val movieFeatures = model.productFeatures.map { case (mid, freatures) =>
      (mid, new DoubleMatrix(freatures))
    }

    val movieRecs = movieFeatures.cartesian(movieFeatures)
      .filter { case (a, b) => a._1 != b._1 }
      .map { case (a, b) =>
        val simScore = this.consinSim(a._2, b._2)
        (a._1, (b._1, simScore))
      }
      .filter(_._2._2 > 0.6)
      .groupByKey()
      .map { case (mid, items) =>
        MovieRecs(mid, items.toList.map(x => Recommendation(x._1, x._2)))
      }
      .toDF()

    movieRecs
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_MOVIE_RECS_COLLECTION )
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //关闭Spark
    spark.close()
  }

  //

  //计算两个电影之间的余弦相似度
  def consinSim(movie1: DoubleMatrix, movie2: DoubleMatrix): Double = {
    movie1.dot(movie2) / (movie1.norm2() * movie2.norm2())
  }
}
