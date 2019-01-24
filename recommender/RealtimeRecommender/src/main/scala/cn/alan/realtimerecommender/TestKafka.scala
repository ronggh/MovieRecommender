package cn.alan.realtimerecommender

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}

object TestKafka {
  def main(args: Array[String]): Unit = {
    // 配置信息
    val config = Map(
      "spark.cores" -> "local[*]",
      "kafka.topic" -> "recommender"
    )
    //创建一个SparkConf配置
    val sparkConf = new SparkConf().setAppName("TestKafka").setMaster(config("spark.cores"))

    //创建Spark的对象
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    val sc = spark.sparkContext
    val ssc = new StreamingContext(sc, Seconds(2))

    // Kafka的连接配置
    val kafkaPara = Map(
      "bootstrap.servers" -> "192.168.154.101:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "recommender",
      "auto.offset.reset" -> "latest"
    )

    // 创建到Kafka的连接
    val kafkaStream = KafkaUtils.createDirectStream[String, String](ssc, LocationStrategies.PreferConsistent, ConsumerStrategies.Subscribe[String, String](Array(config("kafka.topic")), kafkaPara))

    // 产生评分流，格式：UID|MID|SCORE|TIMESTAMP
    val ratingStream = kafkaStream.map { case msg =>
      val attr = msg.value().split("\\|")
      if (attr.length==4)
        {
          (attr(0).toInt, attr(1).toInt, attr(2).toDouble, attr(3).toInt)
        }
      else
        {
          msg.value()
        }

    }

    ratingStream.foreachRDD { rdd =>
      rdd.map {
        case (uid, mid, score, timestamp) =>
          println(uid + "," + mid + "," + score + "," + timestamp)
        case (value) =>
          println(value)
      }.count()
    }

    //启动Streaming程序
    ssc.start()
    ssc.awaitTermination()
  }
}
