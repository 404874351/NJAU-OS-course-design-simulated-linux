package hardware;

import kernel.Page;
import os.Manager;

import javax.xml.crypto.Data;

/**
 * 内存
 *
 * @author ZJC
 */
public class InternalMem {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 当前PCB池中PCB的个数，即处于调度状态的进程数
     */
    private int totalPCBNum;
    /**
     * 物理页框（物理块）数
     */
    public static final int PAGE_NUM = 64;
    /**
     * 页（页框、物理块）大小，单位 B
     */
    public static final int PAGE_SIZE = 512;

    //  0-15块 系统区
    public static final int SYSTEM_AREA_START_PAGE_NO = 0;
    public static final int SYSTEM_AREA_PAGE_NUM = 16;
    //  0- 1块 页表区
    public static final int PAGE_TABLE_START_PAGE_NO = 0;
    public static final int PAGE_TABLE_PAGE_NUM = 2;
    // 页表项大小 4B
    // 7 逻辑页号  6 物理页框号  15 物理块号  1 状态位 1 修改位  2 占位符
    // 每个进程最多可以分配 16 个页表项
    public static final int PAGE_TABLE_ITEM_SIZE = 4;
    //  2-15块 PCB池
    public static final int PCB_POOL_START_PAGE_NO = 2;
    public static final int PCB_POOL_PAGE_NUM = 14;
    // 16-47块 用户区
    public static final int USER_AREA_START_PAGE_NO = 16;
    public static final int USER_AREA_PAGE_NUM = 32;
    // 48-63块 缓冲区
    public static final int BUFFER_AREA_START_PAGE_NO = 48;
    public static final int BUFFER_AREA_PAGE_NUM = 16;

    /**
     * 用以存储内存数据的byte数组
     */
    private byte[] memery;
    /**
     * 页表位示图
     */
    private byte[] pageTableBitMap;
    /**
     * PCB池位示图
     */
    private byte[] poolBitMap;
    /**
     * 用户区域位示图
     */
    private byte[] userAreaBitMap;

    public InternalMem(Manager manager) {
        this.manager = manager;
        this.totalPCBNum = 0;
        this.memery = new byte[PAGE_NUM * PAGE_SIZE];
        this.pageTableBitMap = new byte[2];
        this.poolBitMap = new byte[2];
        this.userAreaBitMap = new byte[4];

        // 初始化内存，每个字节都为FF
        for (int i = 0; i < this.memery.length; ++i) {
            this.memery[i] = -1;
        }

        this.manager.getDashboard().consoleSuccess("内存初始化完成");
    }

    /**
     * 写内存数据
     * @param addressLine 地址线
     * @param dataLine 数据线
     */
    public synchronized void writeData(AddressLine addressLine, DataLine dataLine) {
        // 写低位
        this.memery[addressLine.getAddress() + 0] = (byte)(dataLine.getData());
        // 写高位
        this.memery[addressLine.getAddress() + 1] = (byte)(dataLine.getData() >> 8);
    }

    /**
     * 读内存数据
     * @param addressLine 地址线
     * @return 读取数据
     */
    public synchronized short readData(AddressLine addressLine) {
        // 读低位
        int lowData = ((short)this.memery[addressLine.getAddress() + 0]) & 0x00FF;
        // 读高位
        int highData = (((short)this.memery[addressLine.getAddress() + 1]) << 8) & 0xFF00;
        return (short)(lowData | highData);
    }

    /**
     * 读取页表项信息
     * @param addressLine 地址线，传入页表项的内存地址（实际的存储地址）
     * @return 仅存储页表项信息的页封装类
     */
    public synchronized Page readPageItem(AddressLine addressLine) {
        short lowShort = this.readData(addressLine);
        addressLine.setAddress((short)(addressLine.getAddress() + 2));
        short highShort = this.readData(addressLine);
        int pageItemContent = (((int) highShort << 16) & 0xFFFF0000) | ((int)lowShort & 0x0000FFFF);
        Page page = new Page();
        page.setLogicPageNo(pageItemContent >> 25 & 0x0000007F);
        page.setInternalFrameNo((pageItemContent >> 19 & 0x0000003F) == 63 ? -1 : pageItemContent >> 19 & 0x0000003F);
        page.setExternalBlockNo(pageItemContent >> 4 & 0x00007FFF);
        page.setCallFlag(pageItemContent >> 3 & 0x00000001);
        page.setModifyFlag(pageItemContent >> 2 & 0x00000001);

        return page;
    }

    /**
     * 向内存页表写入一个页表项
     * @param addressLine 地址线，传入页表项的内存地址（实际的存储地址）
     * @param page 对应页表项，使用其中的相关信息
     */
    public synchronized void writePageItem(AddressLine addressLine, Page page) {
        int data = 0;
        // 7 逻辑页号  6 物理页框号  15 物理块号  1 状态位 1 修改位  2 占位符
        data |= page.getLogicPageNo() << 25 & 0xFE000000;
        data |= page.getInternalFrameNo() << 19 & 0x01F80000;
        data |= page.getExternalBlockNo() << 4 & 0x0007FFF0;
        data |= page.getCallFlag() << 3 & 0x00000008;
        data |= page.getModifyFlag() << 2 & 0x00000004;

        this.manager.getDataLine().setData((short)data);
        this.writeData(addressLine, this.manager.getDataLine());

        addressLine.setAddress((short)(addressLine.getAddress() + 2));
        this.manager.getDataLine().setData((short)(data >> 16));
        this.writeData(addressLine, this.manager.getDataLine());
    }

    /**
     * 查找页表区空闲索引，作为新进程的页表基址
     * @return 页表中的区域偏移索引，每个区域包含16个页表项，共占64B
     */
    public synchronized int findFreeIndexOfPageTable() {
        for (int i = 0; i < this.pageTableBitMap.length * 8; ++i) {
            byte data = (byte) (this.pageTableBitMap[i / 8] >> (7 - i % 8));
            if ((data & 0x01) == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 分配页表
     * @return 分配起始地址
     */
    public synchronized int allocatePageTable() {
        int pageTableIndex = this.findFreeIndexOfPageTable();
        this.applyPageTable(pageTableIndex);
        return pageTableIndex;
    }

    /**
     * 申请页表
     * @param pageTableIndex 申请起始地址
     */
    public synchronized void applyPageTable(int pageTableIndex) {
        byte data = (byte) (0x01 << (7 - pageTableIndex % 8));
        this.pageTableBitMap[pageTableIndex / 8] |= data;
    }

    /**
     * 释放页表
     * @param pageTableIndex 释放起始地址
     */
    public synchronized void releasePageTable(int pageTableIndex) {
        byte data = (byte) ~(0x01 << (7 - pageTableIndex % 8));
        this.pageTableBitMap[pageTableIndex / 8] &= data;
    }

    /**
     * 读页框
     * @param page 页信息
     */
    public synchronized void readPage(Page page) {
        int baseAddress = page.getInternalFrameNo() * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE ; i++) {
            page.getData()[i] = this.memery[baseAddress + i];
        }
    }

    /**
     * 写页框
     * @param page 页信息
     */
    public synchronized void writePage(Page page) {
        int baseAddress = page.getInternalFrameNo() * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; ++i) {
            this.memery[baseAddress + i] = page.getData()[i];
        }
        // 如果写入的是PCB页，则系统PCB数 +1
        if (page.getLogicPageNo() == 0) {
            this.increasePCB();
        }
    }

    /**
     * 检索空闲PCB
     * @return 空闲PCB索引
     */
    public synchronized int findFreeIndexOfPool() {
        for (int i = 0; i < this.poolBitMap.length * 8 - 2; ++i) {
            byte data = (byte) (this.poolBitMap[i / 8] >> (7 - i % 8));
            if ((data & 0x01) == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 分配PCB
     * @return 分配的PCB索引
     */
    public synchronized int allocatePool() {
        int poolIndex = this.findFreeIndexOfPool();
        this.applyPool(poolIndex);
        return poolIndex;
    }

    /**
     * 申请PCB
     * @param poolIndex 申请PCB索引
     */
    public synchronized void applyPool(int poolIndex) {
        byte data = (byte) (0x01 << (7 - poolIndex % 8));
        this.poolBitMap[poolIndex / 8] |= data;
        this.manager.getDashboard().refreshFrame(PCB_POOL_START_PAGE_NO + poolIndex, 1);
    }

    /**
     * 释放PCB
     * @param poolIndex 释放PCB索引
     */
    public synchronized void releasePool(int poolIndex) {
        byte data = (byte) ~(0x01 << (7 - poolIndex % 8));
        this.poolBitMap[poolIndex / 8] &= data;
        this.manager.getDashboard().refreshFrame(PCB_POOL_START_PAGE_NO + poolIndex, 0);
    }

    /**
     * 检索空闲用户区页框
     * @return 空闲用户区索引
     */
    public synchronized int findFreeIndexOfUserArea() {
        for (int i = 0; i < this.userAreaBitMap.length * 8; ++i) {
            byte data = (byte) (this.userAreaBitMap[i / 8] >> (7 - i % 8));
            if ((data & 0x01) == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 分配用户区
     * @return 分配的用户区索引
     */
    public synchronized int allocateUserArea() {
        int userAreaIndex = this.findFreeIndexOfUserArea();
        this.applyUserArea(userAreaIndex);
        return userAreaIndex;
    }

    /**
     * 申请用户区
     * @param userAreaIndex 用户区索引
     */
    public synchronized void applyUserArea(int userAreaIndex) {
        byte data = (byte) (0x01 << (7 - userAreaIndex % 8));
        this.userAreaBitMap[userAreaIndex / 8] |= data;
        this.manager.getDashboard().refreshFrame(USER_AREA_START_PAGE_NO + userAreaIndex, 1);
    }

    /**
     * 释放用户区
     * @param userAreaIndex 用户区索引
     */
    public synchronized void releaseUserArea(int userAreaIndex) {
        byte data = (byte) ~(0x01 << (7 - userAreaIndex % 8));
        this.userAreaBitMap[userAreaIndex / 8] &= data;
        this.manager.getDashboard().refreshFrame(USER_AREA_START_PAGE_NO + userAreaIndex, 0);
    }

    /**
     * 获取内存用户区空闲页框数，供中级调度使用
     * @return sum 空闲页框数
     */
    public synchronized int getFreeFrameNumOfUserArea() {
        int sum = 0;
        for (int i = 0; i < this.userAreaBitMap.length * 8; ++i) {
            byte data = (byte) (this.userAreaBitMap[i / 8] >> (7 - i % 8));
            if ((data & 0x01) == 0) {
                ++sum;
            }
        }
        return sum;
    }

    /**
     * 当前系统并发PCB -1
     */
    public synchronized void decreasePCB() {
        --this.totalPCBNum;
    }
    /**
     * 当前系统并发PCB +1
     */
    public synchronized void increasePCB() {
        ++this.totalPCBNum;
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public int getTotalPCBNum() {
        return totalPCBNum;
    }

    public void setTotalPCBNum(int totalPCBNum) {
        this.totalPCBNum = totalPCBNum;
    }

    public byte[] getMemery() {
        return memery;
    }

    public void setMemery(byte[] memery) {
        this.memery = memery;
    }

    public byte[] getPageTableBitMap() {
        return pageTableBitMap;
    }

    public void setPageTableBitMap(byte[] pageTableBitMap) {
        this.pageTableBitMap = pageTableBitMap;
    }

    public byte[] getPoolBitMap() {
        return poolBitMap;
    }

    public void setPoolBitMap(byte[] poolBitMap) {
        this.poolBitMap = poolBitMap;
    }

    public byte[] getUserAreaBitMap() {
        return userAreaBitMap;
    }

    public void setUserAreaBitMap(byte[] userAreaBitMap) {
        this.userAreaBitMap = userAreaBitMap;
    }
}
