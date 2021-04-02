package file;

/**
 * 目录项
 *
 * @author ZJC
 */
public class DirectoryItem {
    /**
     * inode编号
     */
    private int inodeNo;
    /**
     * 文件名
     */
    private String fileName;

    public DirectoryItem(int inodeNo, String fileName) {
        this.inodeNo = inodeNo;
        this.fileName = fileName;
    }

    public int getInodeNo() {
        return inodeNo;
    }

    public void setInodeNo(int inodeNo) {
        this.inodeNo = inodeNo;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
