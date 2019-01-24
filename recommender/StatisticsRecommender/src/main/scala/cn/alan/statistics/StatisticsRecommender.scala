package cn.alan.statistics

import java.text.SimpleDateFormat
import java.util.Date

import cn.alan.common.model._
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

// 引入java中定义的常量
import cn.alan.common.model.Constant._



object StatisticsRecommender {


  def main(args: Array[String]): Unit = {
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> {"mongodb://192.168.154.101:27017/"+MONGODB_DATABASE} ,
      "mongo.db" -> MONGODB_DATABASE
    )

    //创建SparkConf配置
    val sparkConf = new SparkConf().setAppName("StatisticsRecommender").setMaster(config("spark.cores"))

    //创建SparkSession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    val mongoConfig = MongoConfig(config("mongo.uri"),config("mongo.db"))

    //加入隐式转换
    import spark.implicits._

    // 从MongoDB中读取数据，加载
    val ratingDF = spark
      .read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRating]
      .toDF()

    val movieDF = spark
      .read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_MOVIE_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Movie]
      .toDF()

    //创建一张名叫ratings的表
    ratingDF.createOrReplaceTempView("ratings")

    //1. 统计所有历史数据中每个电影的评分数量
    //数据结构 -》  mid,count
    val rateMoreMoviesDF = spark.sql("select mid, count(mid) as count from ratings group by mid")

    // 统计结果保存到mongoDB中
    rateMoreMoviesDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATE_MORE_MOVIES_COLLECTION )
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    // 2. 统计以月为单位的每个电影的评分次数
    // 数据结构 -》 mid,count,time

    //创建一个日期格式化工具
    val simpleDateFormat = new SimpleDateFormat("yyyyMM")

    //注册一个UDF函数，用于将timestamp装换成年月格式   例；1260759144000  => 201605
    spark.udf.register("changeDate",(x:Int) => simpleDateFormat.format(new Date(x * 1000L)).toInt)

    // 将原来的Rating数据集中的时间转换成年月的格式
    val ratingOfYeahMouth = spark.sql("select mid, score, changeDate(timestamp) as yeahmouth from ratings")

    // 将新的数据集注册成为一张表
    ratingOfYeahMouth.createOrReplaceTempView("ratingOfMouth")

    // 统计结果
    val rateMoreRecentlyMovies = spark.sql("select mid, count(mid) as count ,yeahmouth from ratingOfMouth group by yeahmouth,mid")

    // 写入MongoDB
    rateMoreRecentlyMovies
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATE_MORE_MOVIES_RECENTLY_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    // 3. 统计每个电影的平均评分
    val averageMoviesDF = spark.sql("select mid, avg(score) as avg from ratings group by mid")

    averageMoviesDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_AVERAGE_MOVIES_SCORE_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //4. 统计每种电影类型中评分最高的10个电影
    //需要用left join，应为只需要有评分的电影数据集
    val movieWithScore = movieDF.join(averageMoviesDF,Seq("mid","mid"))

    //所有的电影类别
    val genres = List("Action","Adventure","Animation","Comedy","Ccrime","Documentary","Drama","Family","Fantasy","Foreign","History","Horror","Music","Mystery"
      ,"Romance","Science","Tv","Thriller","War","Western")

    //将电影类别转换成RDD
    val genresRDD = spark.sparkContext.makeRDD(genres)

    //计算电影类别top10
    val genrenTopMovies = genresRDD.cartesian(movieWithScore.rdd)  //将电影类别和电影数据进行笛卡尔积操作
      .filter{
      // 过滤掉电影的类别不匹配的电影
      case (genres,row) => row.getAs[String]("genres").toLowerCase.contains(genres.toLowerCase)
    }
      .map{
        // 将整个数据集的数据量减小，生成RDD[String,Iter[mid,avg]]
        case (genres,row) => {
          (genres,(row.getAs[Int]("mid"), row.getAs[Double]("avg")))
        }
      }.groupByKey()   //将genres数据集中的相同的聚集
      .map{
      // 通过评分的大小进行数据的排序，然后将数据映射为对象
      case (genres, items) => GenresRecommendation(genres,items.toList.sortWith(_._2 > _._2).take(10).map(item => Recommendation(item._1,item._2)))
    }.toDF()

    // 输出数据到MongoDB
    genrenTopMovies
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_GENRES_TOP_MOVIES_COLLECTION )
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //关闭Spark
    spark.stop()
  }

}
