package cn.alan.common.model;

/**
 * 常量定义
 */
public class Constant {
    // ************** FOR MONGODB ****************
    // mongoDB库名
    public static final String MONGODB_DATABASE = "recommender";

    // mongoDB中的表名
    public static final String MONGODB_USER_COLLECTION = "User";
    //
    public static final String MONGODB_MOVIE_COLLECTION = "Movie";
    public static final String MONGODB_RATING_COLLECTION = "Rating";
    public static final String MONGODB_TAG_COLLECTION = "Tag";

    //
    public static final String MONGODB_AVERAGE_MOVIES_SCORE_COLLECTION = "AverageMoviesScore";
    public static final String MONGODB_RATE_MORE_MOVIES_COLLECTION = "RateMoreMovies";
    public static final String MONGODB_RATE_MORE_MOVIES_RECENTLY_COLLECTION = "RateMoreMoviesRecently";
    public static final String MONGODB_GENRES_TOP_MOVIES_COLLECTION = "GenresTopMovies";

    //
    public static final String MONGODB_MOVIE_RECS_COLLECTION = "MovieRecs";
    public static final String MONGODB_STREAM_RECS_COLLECTION = "StreamRecs";
    public static final String MONGODB_USER_RECS_COLLECTION = "UserRecs";


    // ************** FOR ELEASTICSEARCH ****************
    // 使用的index
    public static final String ES_INDEX = "recommender";
    // 使用的 type
    public static final String ES_MOVIE_TYPE = "Movie";
    // ES集群名称
    public static final String ES_CLUSTER_NAME ="es";


    // 埋点日志前缀
    public static final String MOVIE_RATING_PREFIX = "MOVIE_RATING_PREFIX";

    // redis相关
    public static final int REDIS_MOVIE_RATING_QUEUE_SIZE = 20;
}
