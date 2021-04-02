package file;

/**
 * 文件类型，分为目录、普通文件
 */
public interface FileType {
    /**
     * 文件类型
     */
    int FILE            = 1;
    /**
     * 文件标识符
     */
    String FILE_MARK    = "-";

    /**
     * 目录类型
     */
    int DIR             = 2;
    /**
     * 目录标识符
     */
    String DIR_MARK     = "d";
}
