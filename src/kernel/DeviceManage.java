package kernel;

import hardware.InternalMem;
import os.Manager;

import java.util.Arrays;

import static java.lang.Thread.sleep;

/**
 * 设备管理
 *
 * 负责输入输出缓冲区的申请、释放等操作
 *
 * @author ZJC
 */
public class DeviceManage {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 缓冲区信息
     */
    private BufferHead[] buffers;
    /**
     * 缓冲区空闲位示图 0空闲 1占用
     */
    private byte[] bufferBitMap;

    public DeviceManage(Manager manager) {
        this.manager = manager;
        this.buffers = new BufferHead[InternalMem.BUFFER_AREA_PAGE_NUM];
        this.bufferBitMap = new byte[InternalMem.BUFFER_AREA_PAGE_NUM / 8];
        for (int i = 0; i < this.buffers.length; ++i) {
            this.buffers[i] = new BufferHead(FileSystem.DEVICE_NO, i, -1, -1, BufferHead.FREE);
        }
    }

    /**
     * 检索空闲缓冲区
     * @return 空闲缓冲区编号
     */
    public synchronized int findFreeIndexOfBufferArea() {
        // 返回缓冲区的偏移索引
        // 实际内存地址为 (BUFFER_AREA_START_PAGE_NO + index) * PAGE_SIZE
        for (int i = 0; i < this.bufferBitMap.length * 8; ++i) {
            byte data = (byte) (this.bufferBitMap[i / 8] >> (7 - i % 8));
            if ((data & 0x01) == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 申请缓冲区
     * @param bufferNo 缓冲区编号
     */
    public synchronized void applyBuffer(int bufferNo) {
        byte data = (byte) (0x01 << (7 - bufferNo % 8));
        this.bufferBitMap[bufferNo / 8] |= data;

        this.manager.getDashboard().consoleInfo("申请缓冲区 " + bufferNo);
        this.manager.getDashboard().refreshFrame(bufferNo + InternalMem.BUFFER_AREA_START_PAGE_NO, 1);
    }

    /**
     * 分配缓冲区
     * @return 分配到的缓冲区编号
     */
    public int allocateBuffer() {
        int freeBufferIndex = -1;
        while (true) {
            synchronized (this) {
                // 申请资源
                freeBufferIndex = this.findFreeIndexOfBufferArea();
                if (freeBufferIndex != -1) {
                    this.applyBuffer(freeBufferIndex);
                    break;
                }
            }
            manager.getDashboard().consoleError("缓冲区资源不足");
            try {
                sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return freeBufferIndex;
    }

    /**
     * 释放缓冲区
     * @param bufferNo 缓冲区编号
     */
    public synchronized void releaseBuffer(int bufferNo) {
        byte data = (byte) ~(0x01 << (7 - bufferNo % 8));
        this.bufferBitMap[bufferNo / 8] &= data;

        this.manager.getDashboard().consoleInfo("释放缓冲区 " + bufferNo);
        this.manager.getDashboard().refreshFrame(bufferNo + InternalMem.BUFFER_AREA_START_PAGE_NO, 0);
    }

    /**
     * 缓冲区读整块，外存 -> 缓冲区
     * @param bufferNo 缓冲区序号
     */
    public void bufferRead(int bufferNo) {
        int blockNo = this.buffers[bufferNo].getBlockNo();
        byte[] data = new byte[InternalMem.PAGE_SIZE];
        this.manager.getCpu().switchToKernelState();
        this.manager.getExMem().readPage(blockNo, data);
        this.manager.getCpu().switchToUserState();
        this.setBufferContent(bufferNo, data);
    }

    /**
     * 缓冲区写整块，缓冲区 -> 外存
     * @param bufferNo 缓冲区序号
     */
    public void bufferWrite(int bufferNo) {
        int blockNo = this.buffers[bufferNo].getBlockNo();
        byte[] data = this.getBufferContent(bufferNo);
        this.manager.getCpu().switchToKernelState();
        this.manager.getExMem().writePage(blockNo, data);
        this.manager.getCpu().switchToUserState();
    }

    /**
     * 获取缓冲区内容
     * @param bufferNo 缓冲区序号
     * @return 缓冲区数据
     */
    public byte[] getBufferContent(int bufferNo) {
        int startIndex = (InternalMem.BUFFER_AREA_START_PAGE_NO + bufferNo) * InternalMem.PAGE_SIZE;
        int endIndex = startIndex + InternalMem.PAGE_SIZE;
        byte[] data = Arrays.copyOfRange(this.manager.getInMem().getMemery(), startIndex, endIndex);
        return data;
    }

    /**
     * 设置缓冲区内容
     * @param bufferNo 缓冲区序号
     * @param data 缓冲区数据
     */
    public void setBufferContent(int bufferNo, byte[] data) {
        for (int i = 0; i < data.length; ++i) {
            this.manager.getInMem().getMemery()[(InternalMem.BUFFER_AREA_START_PAGE_NO + bufferNo) * InternalMem.PAGE_SIZE + i] = data[i];
        }
    }

    /**
     * 调用缓冲区
     * @param page 页信息
     * @param mode 打开方式
     */
    public void useBuffer(Page page, int mode) {
        if (mode == BufferHead.READ) {
            page.setData(null);
        }
        DeviceManage deviceManage = this;
        new Thread() {
            @Override
            public void run() {
                int freeBufferIndex = deviceManage.allocateBuffer();
                BufferHead bufferHead;

                // 设置相关对应关系
                bufferHead = deviceManage.getBuffers()[freeBufferIndex];
                bufferHead.setFlag(mode);
                bufferHead.setFrameNo(page.getInternalFrameNo());
                bufferHead.setBlockNo(page.getExternalBlockNo());
                // 具体的读写操作
                if (mode == BufferHead.READ) {
                    manager.getDashboard().consoleLog("外存块 " + bufferHead.getBlockNo() +
                            " --读取--> 缓冲区 " + bufferHead.getBufferNo());
                    deviceManage.bufferRead(bufferHead.getBufferNo());
                    page.setData(deviceManage.getBufferContent(bufferHead.getBufferNo()));
                } else if (mode == BufferHead.WRITE) {
                    manager.getDashboard().consoleLog("缓冲区 " + bufferHead.getBufferNo() +
                            " --写入--> 外存块 " + bufferHead.getBlockNo());
                    deviceManage.setBufferContent(bufferHead.getBufferNo(), page.getData());
                    deviceManage.bufferWrite(bufferHead.getBufferNo());
                }
                // 释放资源
                deviceManage.releaseBuffer(bufferHead.getBufferNo());
            }
        }.start();

    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public BufferHead[] getBuffers() {
        return buffers;
    }

    public void setBuffers(BufferHead[] buffers) {
        this.buffers = buffers;
    }

    public byte[] getBufferBitMap() {
        return bufferBitMap;
    }

    public void setBufferBitMap(byte[] bufferBitMap) {
        this.bufferBitMap = bufferBitMap;
    }
}
