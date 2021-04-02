package file;

import hardware.ExternalMem;
import kernel.BufferHead;
import kernel.FileSystem;
import kernel.Page;

import java.util.Iterator;
import java.util.Vector;

/**
 * 磁盘inode结构
 *
 * @author ZJC
 */
public class DiskInode {
    /**
     * 文件系统
     */
    private FileSystem fileSystem;
    /**
     * 用户 ID
     */
    private int userId;
    /**
     * 用户组 ID
     */
    private int groupId;
    /**
     * 硬连接数
     */
    private int hardLinkNum;
    /**
     * 文件大小
     */
    private int fileSize;
    /**
     * 文件访问权限
     */
    private int mode;
    /**
     * 文件类型
     */
    private int type;
    /**
     * 创建时间
     */
    private int createdTime;
    /**
     * 最后更新时间
     */
    private int lastUpdateTime;
    /**
     * 存储区域的块编号
     */
    private Vector<Integer> storeBlockNoList;
    /**
     * 目录项
     */
    private Vector<DirectoryItem> directoryItemList;

    public DiskInode(FileSystem fileSystem, int userId, int groupId, int mode, int type, int createdTime) {
        this.fileSystem         = fileSystem;
        this.userId             = userId;
        this.groupId            = groupId;
        this.hardLinkNum        = 1;
        this.fileSize           = 0;
        this.mode               = mode;
        this.type               = type;
        this.createdTime        = createdTime;
        this.lastUpdateTime     = createdTime;
        this.storeBlockNoList   = new Vector<>();
        this.directoryItemList  = new Vector<>();
    }

    /**
     * 添加当前目录项
     * @param inodeNo inode编号
     */
    public void addCurrentDir(int inodeNo) {
        this.directoryItemList.add(new DirectoryItem(inodeNo, "."));
    }

    /**
     * 获取当前目录项
     * @return 当前目录项
     */
    public DirectoryItem getCurrentDir() {
        Iterator<DirectoryItem> iterator = this.directoryItemList.iterator();
        while (iterator.hasNext()) {
            DirectoryItem directoryItem = iterator.next();
            if (directoryItem.getFileName().equals(".")) {
                return directoryItem;
            }
        }
        return null;
    }

    /**
     * 添加父目录项
     * @param inodeNo inode编号
     */
    public void addParentDir(int inodeNo) {
        this.directoryItemList.add(new DirectoryItem(inodeNo, ".."));
    }

    /**
     * 获取父目录项
     * @return 父目录项
     */
    public DirectoryItem getParentDir() {
        Iterator<DirectoryItem> iterator = this.directoryItemList.iterator();
        while (iterator.hasNext()) {
            DirectoryItem directoryItem = iterator.next();
            if (directoryItem.getFileName().equals("..")) {
                return directoryItem;
            }
        }
        return null;
    }

    /**
     * 添加普通目录项
     * @param inodeNo inode编号
     * @param fileName 文件名
     */
    public void addDir(int inodeNo, String fileName) {
        this.directoryItemList.add(new DirectoryItem(inodeNo, fileName));
        this.lastUpdateTime = this.fileSystem.getManager().getClock().getCurrentTime();
    }

    /**
     * 删除目录项
     * @param fileName 文件名
     */
    public void removeDir(String fileName) {
        Iterator<DirectoryItem> iterator = this.directoryItemList.iterator();
        while (iterator.hasNext()) {
            DirectoryItem directoryItem = iterator.next();
            if (directoryItem.getFileName().equals(fileName)) {
                iterator.remove();
                this.lastUpdateTime = this.fileSystem.getManager().getClock().getCurrentTime();
                return;
            }
        }
    }

    /**
     * 删除目录项
     * @param inodeNo inode编号
     */
    public void removeDir(int inodeNo) {
        Iterator<DirectoryItem> iterator = this.directoryItemList.iterator();
        while (iterator.hasNext()) {
            DirectoryItem directoryItem = iterator.next();
            if (directoryItem.getInodeNo() == inodeNo) {
                iterator.remove();
                this.lastUpdateTime = this.fileSystem.getManager().getClock().getCurrentTime();
                return;
            }
        }
    }

    /**
     * 检索目录项
     * @param fileName 文件名
     * @return 目录项索引
     */
    public int findDirectoryItem(String fileName) {
        for (int i = 0; i < this.directoryItemList.size(); ++i) {
            if (this.directoryItemList.get(i).getFileName().equals(fileName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 检索目录项
     * @param inodeNo inode编号
     * @return 目录项索引
     */
    public int findDirectoryItem(int inodeNo) {
        for (int i = 0; i < this.directoryItemList.size(); ++i) {
            if (this.directoryItemList.get(i).getInodeNo() == inodeNo) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 保存到外存
     * @param inodeNo inode编号
     */
    public void saveToDisk(int inodeNo) {
        // 存储信息设置
        Page page = new Page();
        page.setExternalBlockNo(ExternalMem.INODE_AREA_START_BLOCK_NO + inodeNo);
        page.getData()[0] = (byte) this.userId;
        page.getData()[1] = (byte)(this.userId >> 8);
        page.getData()[2] = (byte)(this.userId >> 16);
        page.getData()[3] = (byte)(this.userId >> 24);

        page.getData()[4] = (byte) this.groupId;
        page.getData()[5] = (byte)(this.groupId >> 8);
        page.getData()[6] = (byte)(this.groupId >> 16);
        page.getData()[7] = (byte)(this.groupId >> 24);

        page.getData()[8] =  (byte) this.hardLinkNum;
        page.getData()[9] =  (byte)(this.hardLinkNum >> 8);
        page.getData()[10] = (byte)(this.hardLinkNum >> 16);
        page.getData()[11] = (byte)(this.hardLinkNum >> 24);

        page.getData()[12] = (byte) this.fileSize;
        page.getData()[13] = (byte)(this.fileSize >> 8);
        page.getData()[14] = (byte)(this.fileSize >> 16);
        page.getData()[15] = (byte)(this.fileSize >> 24);

        page.getData()[16] = (byte) this.mode;
        page.getData()[17] = (byte)(this.mode >> 8);
        page.getData()[18] = (byte)(this.mode >> 16);
        page.getData()[19] = (byte)(this.mode >> 24);

        page.getData()[20] = (byte) this.type;
        page.getData()[21] = (byte)(this.type >> 8);
        page.getData()[22] = (byte)(this.type >> 16);
        page.getData()[23] = (byte)(this.type >> 24);

        page.getData()[24] = (byte) this.createdTime;
        page.getData()[25] = (byte)(this.createdTime >> 8);
        page.getData()[26] = (byte)(this.createdTime >> 16);
        page.getData()[27] = (byte)(this.createdTime >> 24);

        page.getData()[28] = (byte) this.lastUpdateTime;
        page.getData()[29] = (byte)(this.lastUpdateTime >> 8);
        page.getData()[30] = (byte)(this.lastUpdateTime >> 16);
        page.getData()[31] = (byte)(this.lastUpdateTime >> 24);
        // 写入磁盘inode区
        this.fileSystem.getManager().getDeviceManage().useBuffer(page, BufferHead.WRITE);
    }

    /**
     * 添加一个存储块
     * @param blockNo 物理块号
     */
    public void addStoreBlock(int blockNo) {
        this.storeBlockNoList.add(new Integer(blockNo));
        this.lastUpdateTime = this.fileSystem.getManager().getClock().getCurrentTime();
    }

    /**
     * 删除磁盘inode
     */
    public void remove() {
        // 释放相关数据
        Iterator<Integer> iterator = this.storeBlockNoList.iterator();
        while (iterator.hasNext()) {
            // 释放占用的存储空间
            this.fileSystem.getSuperBlock().releaseStoreBlock(iterator.next().intValue());
        }
        // 释放一个占用inode
        this.fileSystem.getSuperBlock().releaseInode(this.getCurrentDir().getInodeNo());
        // 修改更新时间
        this.lastUpdateTime = this.fileSystem.getManager().getClock().getCurrentTime();

    }

    /**
     * 硬链接数 +1
     */
    public void increaseHardLinkNum() {
        ++this.hardLinkNum;
        this.lastUpdateTime = this.fileSystem.getManager().getClock().getCurrentTime();
    }

    /**
     * 硬链接数 -1
     */
    public void decreaseHardLinkNum() {
        --this.hardLinkNum;
        this.lastUpdateTime = this.fileSystem.getManager().getClock().getCurrentTime();
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public int getHardLinkNum() {
        return hardLinkNum;
    }

    public void setHardLinkNum(int hardLinkNum) {
        this.hardLinkNum = hardLinkNum;
    }

    public int getFileSize() {
        return fileSize;
    }

    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(int createdTime) {
        this.createdTime = createdTime;
    }

    public int getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(int lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Vector<Integer> getStoreBlockNoList() {
        return storeBlockNoList;
    }

    public void setStoreBlockNoList(Vector<Integer> storeBlockNoList) {
        this.storeBlockNoList = storeBlockNoList;
    }

    public Vector<DirectoryItem> getDirectoryItemList() {
        return directoryItemList;
    }

    public void setDirectoryItemList(Vector<DirectoryItem> directoryItemList) {
        this.directoryItemList = directoryItemList;
    }
}
