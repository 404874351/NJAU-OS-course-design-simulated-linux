package file;

import kernel.BufferHead;
import kernel.FileSystem;
import kernel.Page;

/**
 * 超级块，处于1#
 *
 * 存放文件系统结构和管理信息
 *
 * @author ZJC
 */
public class SuperBlock {
    /**
     * 文件系统
     */
    private FileSystem fileSystem;
    /**
     * inode区域所占块数
     */
    private int inodeAreaBlockNum;
    /**
     * 整个块设备的块数（外存块数）
     */
    private int totalBlockNum;
    /**
     * 存储区空闲块数
     */
    private int storeFreeBlockNum;
    /**
     * inode空闲个数，一个inode存储32B，一块可以存16个inode
     */
    private int inodeFreeNum;
    /**
     * 只读标志位
     */
    private int readOnly;
    /**
     * 修改标志位
     */
    private int modifyFlag;
    /**
     * 最后修改时间
     */
    private int lastUpdateTime;
    /**
     * 存储空闲块队列 空闲 0，占用 1
     */
    private int[] storeFreeQueue;
    /**
     * inode空闲队列 空闲 0，占用 1
     */
    private int[] inodeFreeQueue;

    public SuperBlock(FileSystem fileSystem, int inodeAreaBlockNum, int totalBlockNum, int storeFreeBlockNum, int inodeFreeNum) {
        this.fileSystem         = fileSystem;
        this.inodeAreaBlockNum  = inodeAreaBlockNum;
        this.totalBlockNum      = totalBlockNum;
        this.storeFreeBlockNum  = storeFreeBlockNum;
        this.inodeFreeNum       = inodeFreeNum;
        this.readOnly           = 1;
        this.modifyFlag         = 0;
        this.lastUpdateTime     = 0;
        this.storeFreeQueue     = new int[storeFreeBlockNum / 8 + 1];
        this.inodeFreeQueue     = new int[inodeFreeNum * 16 / 8];
        // 引导块和超级块不可被占用
        this.inodeFreeQueue[0]  = 0x0C0;
    }

    /**
     * 保存到外存
     */
    public void saveToDisk() {
        // 超级块存储在 #1
        int blockNo = 1;
        // 存储信息设置
        Page page = new Page();
        page.setExternalBlockNo(blockNo);
        page.getData()[0] = (byte) this.inodeAreaBlockNum;
        page.getData()[1] = (byte)(this.inodeAreaBlockNum >> 8);
        page.getData()[2] = (byte)(this.inodeAreaBlockNum >> 16);
        page.getData()[3] = (byte)(this.inodeAreaBlockNum >> 24);

        page.getData()[4] = (byte) this.totalBlockNum;
        page.getData()[5] = (byte)(this.totalBlockNum >> 8);
        page.getData()[6] = (byte)(this.totalBlockNum >> 16);
        page.getData()[7] = (byte)(this.totalBlockNum >> 24);

        page.getData()[8] =  (byte) this.storeFreeBlockNum;
        page.getData()[9] =  (byte)(this.storeFreeBlockNum >> 8);
        page.getData()[10] = (byte)(this.storeFreeBlockNum >> 16);
        page.getData()[11] = (byte)(this.storeFreeBlockNum >> 24);

        page.getData()[12] = (byte) this.inodeFreeNum;
        page.getData()[13] = (byte)(this.inodeFreeNum >> 8);
        page.getData()[14] = (byte)(this.inodeFreeNum >> 16);
        page.getData()[15] = (byte)(this.inodeFreeNum >> 24);

        page.getData()[16] = (byte) this.readOnly;
        page.getData()[17] = (byte)(this.readOnly >> 8);
        page.getData()[18] = (byte)(this.readOnly >> 16);
        page.getData()[19] = (byte)(this.readOnly >> 24);

        page.getData()[20] = (byte) this.modifyFlag;
        page.getData()[21] = (byte)(this.modifyFlag >> 8);
        page.getData()[22] = (byte)(this.modifyFlag >> 16);
        page.getData()[23] = (byte)(this.modifyFlag >> 24);

        page.getData()[24] = (byte) this.lastUpdateTime;
        page.getData()[25] = (byte)(this.lastUpdateTime >> 8);
        page.getData()[26] = (byte)(this.lastUpdateTime >> 16);
        page.getData()[27] = (byte)(this.lastUpdateTime >> 24);
        // 写入外存超级块
        this.fileSystem.getManager().getDeviceManage().useBuffer(page, BufferHead.WRITE);
    }

    /**
     * 检索空闲inode
     * @return 空闲inode编号
     */
    public synchronized int findFreeIndexOfInodeArea() {
        for (int i = 0; i < this.inodeFreeQueue.length * 8; ++i) {
            byte data = (byte) (this.inodeFreeQueue[i / 8] >> (7 - i % 8));
            if ((data & 0x01) == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 申请inode
     * @param inodeNo inode编号
     */
    public synchronized void applyInode(int inodeNo) {
        byte data = (byte) (0x01 << (7 - inodeNo % 8));
        this.inodeFreeQueue[inodeNo / 8] |= data;
        this.fileSystem.getManager().getDashboard().consoleLog("申请磁盘inode区 " + inodeNo);
    }
    /**
     * 分配（寻找并申请）一个磁盘inode
     * @return 分配的inode的逻辑号
     */
    public synchronized int allocateInode() {
        int inodeNo = this.findFreeIndexOfInodeArea();
        this.applyInode(inodeNo);
        return inodeNo;
    }

    /**
     * 释放inode
     * @param inodeNo inode编号
     */
    public synchronized void releaseInode(int inodeNo) {
        byte data = (byte) ~(0x01 << (7 - inodeNo % 8));
        this.inodeFreeQueue[inodeNo / 8] &= data;
        this.fileSystem.getManager().getDashboard().consoleLog("释放磁盘inode区 " + inodeNo);
    }

    /**
     * 检索空闲存储块
     * @return 空闲块编号
     */
    public synchronized int findFreeIndexOfStoreArea() {
        for (int i = 0; i < this.storeFreeQueue.length * 8; ++i) {
            byte data = (byte) (this.storeFreeQueue[i / 8] >> (7 - i % 8));
            if ((data & 0x01) == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 申请空闲块
     * @param blockNo 块编号
     */
    public synchronized void applyStoreBlock(int blockNo) {
        byte data = (byte) (0x01 << (7 - blockNo % 8));
        this.storeFreeQueue[blockNo / 8] |= data;
        this.fileSystem.getManager().getDashboard().consoleLog("申请存储区 " + blockNo);

    }

    /**
     * 分配（寻找并申请）一个物理存储块
     * @return 分配的物理块的逻辑块号
     */
    public synchronized int allocateStoreBlock() {
        int blockNo = this.findFreeIndexOfStoreArea();
        this.applyStoreBlock(blockNo);
        return blockNo;
    }

    /**
     * 释放存储块
     * @param blockNo 块编号
     */
    public synchronized void releaseStoreBlock(int blockNo) {
        byte data = (byte) ~(0x01 << (7 - blockNo % 8));
        this.storeFreeQueue[blockNo / 8] &= data;
        this.fileSystem.getManager().getDashboard().consoleLog("释放存储区 " + blockNo);
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public int getInodeAreaBlockNum() {
        return inodeAreaBlockNum;
    }

    public void setInodeAreaBlockNum(int inodeAreaBlockNum) {
        this.inodeAreaBlockNum = inodeAreaBlockNum;
    }

    public int getTotalBlockNum() {
        return totalBlockNum;
    }

    public void setTotalBlockNum(int totalBlockNum) {
        this.totalBlockNum = totalBlockNum;
    }

    public int getStoreFreeBlockNum() {
        return storeFreeBlockNum;
    }

    public void setStoreFreeBlockNum(int storeFreeBlockNum) {
        this.storeFreeBlockNum = storeFreeBlockNum;
    }

    public int getInodeFreeNum() {
        return inodeFreeNum;
    }

    public void setInodeFreeNum(int inodeFreeNum) {
        this.inodeFreeNum = inodeFreeNum;
    }

    public int getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(int readOnly) {
        this.readOnly = readOnly;
    }

    public int getModifyFlag() {
        return modifyFlag;
    }

    public void setModifyFlag(int modifyFlag) {
        this.modifyFlag = modifyFlag;
    }

    public int getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(int lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public int[] getStoreFreeQueue() {
        return storeFreeQueue;
    }

    public void setStoreFreeQueue(int[] storeFreeQueue) {
        this.storeFreeQueue = storeFreeQueue;
    }

    public int[] getInodeFreeQueue() {
        return inodeFreeQueue;
    }

    public void setInodeFreeQueue(int[] inodeFreeQueue) {
        this.inodeFreeQueue = inodeFreeQueue;
    }
}
