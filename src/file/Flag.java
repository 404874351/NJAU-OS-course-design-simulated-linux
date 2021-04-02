package file;

/**
 * 文件打开方式，分为读打开、写打开、管道打开三种
 */
public interface Flag {
    /**
     * 文件读打开
     */
    int FILE_READ = 1;
    /**
     * 文件写打开
     */
    int FILE_WRITE = 2;
    /**
     * 文件管道
     */
    int FILE_PIPE = 4;
}
