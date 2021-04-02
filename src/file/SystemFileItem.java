package file;

/**
 * 系统打开文件表 表项
 *
 * @author ZJC
 */
public class SystemFileItem {
    /**
     * 标志位，标志读、写、管道
     */
    private int flag;
    /**
     * 参照数，记录该项是否被启用
     */
    private int count;
    /**
     * 文件偏移，记录操作指针的位置，0 <= offset <= fileSize 恒成立
     */
    private int offset;
    /**
     * 活动inode
     */
    private ActivityInode inode;

    public SystemFileItem(int flag, int count, int offset, ActivityInode inode) {
        this.flag = flag;
        this.count = count;
        this.offset = offset;
        this.inode = inode;
    }

    public void increaseCount() {
        ++this.count;
    }

    public void decreaseCount() {
        --this.count;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public ActivityInode getInode() {
        return inode;
    }

    public void setInode(ActivityInode inode) {
        this.inode = inode;
    }
}
