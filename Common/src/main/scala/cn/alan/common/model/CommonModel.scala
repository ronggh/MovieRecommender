package cn.alan.common.model

// 三个数据文件的结构，定义为3个case class类。
/**
  * Movie数据集，数据集字段通过  ^  符号分割
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

case class Movie(val mid: Int, val name: String, val descri: String, val timelong: String, val issue: String,
                 val shoot: String, val language: String, val genres: String, val actors: String, val directors: String)

/**
  * Rating数据集，用户对于电影的评分数据集，用，分割
  *
  * 1,           用户的ID
  * 31,          电影的ID
  * 2.5,         用户对于电影的评分
  * 1260759144   用户对于电影评分的时间
  */
case class MovieRating(val uid: Int, val mid: Int, val score: Double, val timestamp: Int)

/**
  * Tag数据集，用户对于电影的标签数据集，用，分割
  *
  * 15,          用户的ID
  * 1955,        电影的ID
  * dentist,     标签的具体内容
  * 1193435061   用户对于电影打标签的时间
  */
case class Tag(val uid: Int, val mid: Int, val tag: String, val timestamp: Int)

//推荐
case class Recommendation(rid: Int, r: Double)
/**
  * 电影相似推荐
  * @param mid 电影ID
  * @param recs 相似的电影集合
  */
case class MovieRecommendation(mid: Int, recs: Seq[Recommendation])
case class GenresRecommendation(genres:String, recs:Seq[Recommendation])

// 用户的推荐矩阵
case class UserRecs(uid: Int, recs: Seq[Recommendation])

//电影的相似度
case class MovieRecs(uid: Int, recs: Seq[Recommendation])



// MongoDB和ElasticSearch的连接配置信息
/**
  * MongoDB的连接配置  *
  * @param uri MongoDB的连接
  * @param db  MongoDB要操作数据库
  */
case class MongoConfig(val uri: String, val db: String)

/**
  * ElasticSearch的连接配置  *
  * @param httpHosts      Http的主机列表，以，分割
  * @param transportHosts Transport主机列表， 以，分割
  * @param index          需要操作的索引
  * @param clustername    ES集群的名称：见ElasticSearch服务的配置文件：config/elasticsearch.yml
  */
case class ESConfig(val httpHosts: String, val transportHosts: String, val index: String, val clustername: String)



