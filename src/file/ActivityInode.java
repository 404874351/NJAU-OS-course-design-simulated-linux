package file;

import kernel.FileSystem;

/**
 * 活动inode
 *
 * @author ZJC
 */
public class ActivityInode {
    /**
     * 文件系统
     */
    private FileSystem fileSystem;
    /**
     * 设备编号
     */
    private int deviceNo;
    /**
     * inode编号
     */
    private int inodeNo;
    /**
     * 引用数，记录当前打开该文件的次数
     */
    private int referenceCount;

    public ActivityInode(FileSystem fileSystem, int deviceNo, int inodeNo, int referenceCount) {
        this.fileSystem = fileSystem;
        this.deviceNo = deviceNo;
        this.inodeNo = inodeNo;
        this.referenceCount = referenceCount;
    }

    public void increaseReferenceCount() {
        ++this.referenceCount;
    }

    public void decreaseReferenceCount() {
        --this.referenceCount;
    }

    public void writeBack() {
        this.fileSystem.getDiskInodeMap().get("" + this.inodeNo).saveToDisk(this.inodeNo);
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public int getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(int deviceNo) {
        this.deviceNo = deviceNo;
    }

    public int getInodeNo() {
        return inodeNo;
    }

    public void setInodeNo(int inodeNo) {
        this.inodeNo = inodeNo;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public void setReferenceCount(int referenceCount) {
        this.referenceCount = referenceCount;
    }
}
