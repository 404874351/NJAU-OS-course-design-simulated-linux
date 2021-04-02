package file;

/**
 * 文件权限，分为用户、用户组、其他用户三类
 *
 * @author ZJC
 */
public interface Mode {
    /**
     * 其他用户 执行
     */
    int OTHER_EXEC  = 1;
    /**
     * 其他用户 写
     */
    int OTHER_WRITE = 2;
    /**
     * 其他用户 读
     */
    int OTHER_READ  = 4;
    /**
     * 同组用户 执行
     */
    int GROUP_EXEC  = 8;
    /**
     * 同组用户 写
     */
    int GROUP_WRITE = 16;
    /**
     * 同组用户 读
     */
    int GROUP_READ  = 32;
    /**
     * 属主用户 执行
     */
    int USER_EXEC   = 64;
    /**
     * 属主用户 写
     */
    int USER_WRITE  = 128;
    /**
     * 属主用户 读
     */
    int USER_READ   = 256;
}
