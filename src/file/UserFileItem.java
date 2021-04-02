package file;

/**
 * 用户打开文件表 表项
 *
 * @author ZJC
 */
public class UserFileItem {
    /**
     * 文件描述符
     */
    private int fd;
    /**
     * file指针
     */
    private SystemFileItem fp;

    public UserFileItem(int fd, SystemFileItem fp) {
        this.fd = fd;
        this.fp = fp;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }

    public SystemFileItem getFp() {
        return fp;
    }

    public void setFp(SystemFileItem fp) {
        this.fp = fp;
    }
}
