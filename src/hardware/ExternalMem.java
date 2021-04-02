package hardware;

import kernel.Page;
import os.Manager;

import java.io.*;

/**
 * 外存
 *
 * @author ZJC
 */
public class ExternalMem {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 柱面数
     */
    public static final int  CYLINDER_NUM = 10;
    /**
     * 磁道数
     */
    public static final int  TRACK_NUM = 32;
    /**
     * 扇区数
     */
    public static final int  SECTOR_NUM = 64;
    /**
     * 扇区大小，单位 B
     */
    public static final int  SECTOR_SIZE = 512;

    // 第0块为引导块，第1块为超级块
    // 第2-65块为磁盘inode区
    // 第66-20095块为存储区，存放用户文件
    // 第20096-20223块为JCB区，存储JCB数据
    // 第20224-20479块为交换区，用于虚拟内存交换

    // 2-65块 inode区
    public static final int INODE_AREA_START_BLOCK_NO = 2;
    public static final int INODE_AREA_BLOCK_NUM = 64;
    // 66-20095块 存储区
    public static final int STORE_AREA_START_BLOCK_NO = 66;
    public static final int STORE_AREA_BLOCK_NUM = 20030;
    // 20096-20223块 JCB区
    public static final int JCB_AREA_START_BLOCK_NO = 20096;
    public static final int JCB_AREA_BLOCK_NUM = 128;
    // 20224-20479块 交换区
    public static final int SWAP_AREA_START_BLOCK_NO = 20224;
    public static final int SWAP_AREA_BLOCK_NUM = 256;

    // 仿真外存的txt文件中每行的字节数
    public static final int INLINE_BYTE_NUM = 16;
    // 仿真外存的txt文件中总行数
    public static final int TOTAL_LINE_NUM = SECTOR_SIZE / INLINE_BYTE_NUM;
    /**
     * 交换区位示图
     */
    private byte[] swapAreaBitMap;

    public ExternalMem(Manager manager) {
        this.manager        = manager;
        this.swapAreaBitMap = new byte[SWAP_AREA_BLOCK_NUM / 8];
        this.init();

        this.manager.getDashboard().consoleSuccess("外存初始化完成");
    }

    /**
     * 初始化外存区
     *
     * 用以创建所有仿真外存 txt 文件
     */
    public void init() {
        // 在系统最终设计好后，外存系统区空间会自动释放
        // 目前暂时选择每次启动都进行初始化
//        String disk = "./disk";
//        if (new File(disk).isDirectory()) {
//            // 外存已初始化，则操作结束
//            this.manager.getDashboard().consoleInfo("外存已初始化！");
//            return;
//        }

        for (int c = 0; c < CYLINDER_NUM; ++c) {
            // 遍历柱面
            for (int t = 0; t < TRACK_NUM; ++t) {
                // 遍历磁道
                String track = "./disk/cylinder_" + c + "/track_" + t;
                File trackDirectory = new File(track);
                if(!trackDirectory.exists()) {
                    // 不存在，则创建目录
                    trackDirectory.mkdirs();
                }
                for (int s = 0; s < SECTOR_NUM; ++s) {
                    // 遍历生成每一个扇区，以 txt 文件仿真
                    File sectorFile = new File(track + "/sector_" + s + ".txt");
                    try {
                        if (!sectorFile.exists()) {
                            sectorFile.createNewFile();
                        }
                        BufferedWriter bw = new BufferedWriter(new FileWriter(sectorFile));
                        for (int i = 0; i < TOTAL_LINE_NUM; ++i) {
                            for (int j = 0; j < INLINE_BYTE_NUM; ++j) {
                                bw.write("FF ");
                            }
                            bw.newLine();
                        }
                        bw.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            this.manager.getDashboard().consoleLog("cylinder_"+ c + " OK！");
        }
    }

    /**
     * 读外存数据
     * @param address 外存地址
     * @return 读取数据
     */
    public synchronized short readData(int address) {
        // 拆分地址，提取所需信息
        int[] addressElement = this.splitAddress(address);
        int cylinder = addressElement[0];
        int track = addressElement[1];
        int sector = addressElement[2];
        int offset = addressElement[3];
        int line = addressElement[4];
        int inlineOffset = addressElement[5];
        // 定位sector文件
        File sectorFile  = new File("./disk" +
                "/cylinder_" + cylinder +
                "/track_" + track +
                "/sector_" + sector +
                ".txt");
        try {
            RandomAccessFile ra = new RandomAccessFile(sectorFile, "r");
            // 地址定位
            ra.seek(3 * offset + 2 * line);
            // 读低字节
            String lowByte = new String(new byte[]{ra.readByte(), ra.readByte()}, "ascii");
            // 跳过分隔符空格
            ra.skipBytes(1);
            if (inlineOffset == INLINE_BYTE_NUM - 1) {
                // 低位是一行末尾字节，则还需跳过换行符
                ra.skipBytes(2);
            }
            // 读高字节
            String highByte = new String(new byte[]{ra.readByte(), ra.readByte()}, "ascii");
            ra.close();

            // 16进制字符串转short
            return (short)Integer.parseInt(highByte + lowByte, 16);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 写外存数据
     * @param address 地址
     * @param data 写入数据
     */
    public synchronized void writeData(int address, short data) {
        // 拆分地址，提取所需信息
        int[] addressElement = this.splitAddress(address);
        int cylinder = addressElement[0];
        int track = addressElement[1];
        int sector = addressElement[2];
        int offset = addressElement[3];
        int line = addressElement[4];
        int inlineOffset = addressElement[5];
        // 定位sector文件
        File sectorFile  = new File("./disk" +
                "/cylinder_" + cylinder +
                "/track_" + track +
                "/sector_" + sector +
                ".txt");
        try {
            RandomAccessFile ra = new RandomAccessFile(sectorFile, "rw");
            // 定位地址
            ra.seek(3 * offset + 2 * line);
            // 双字节数据转化为4位16进制
            String dataString = Integer.toHexString((int)data);
            if (dataString.length() >= 4) {
                dataString = dataString.substring(dataString.length() - 4);
            } else {
                int appendZeroNum = 4 - dataString.length();
                for(int i = 0; i < appendZeroNum; ++i) {
                    dataString = "0" + dataString;
                }
            }
            this.manager.getDashboard().consoleLog("写入外存：" + dataString);
            // 写低字节
            String lowByte = dataString.substring(2).toUpperCase();
            ra.writeBytes(lowByte);
            // 跳过分隔符空格
            ra.skipBytes(1);
            if (inlineOffset == INLINE_BYTE_NUM - 1) {
                // 低位是一行末尾字节，则还需跳过换行符
                ra.skipBytes(2);
            }
            // 写高字节
            String highByte = dataString.substring(0,2).toUpperCase();
            ra.writeBytes(highByte);
            ra.close();
            /**
             * 还需要考虑低地址是该页最后一位的情况！！！！！！！！！！！！！！！！！
             */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 拆分地址，供定位数据使用
     * @param address 指定地址
     * @return int数组，依次存放柱面、磁道、扇区、块内偏移、所在行数、行内偏移
     */
    public int[] splitAddress(int address) {
        // 由地址计算柱面、磁道、扇区、块内偏移
        int cylinder = address / SECTOR_SIZE / SECTOR_NUM / TRACK_NUM;
        int track = address / SECTOR_SIZE / SECTOR_NUM % TRACK_NUM;
        int sector = address / SECTOR_SIZE % SECTOR_NUM;
        int offset = address % SECTOR_SIZE;
        // 由块内偏移计算所在行数、行内偏移
        int line = offset / INLINE_BYTE_NUM;
        int inlineOffset = offset % INLINE_BYTE_NUM;

        return new int[]{cylinder, track, sector, offset, line, inlineOffset};
    }

    /**
     * 检索空闲交换区块号
     * @return 空闲块号
     */
    public synchronized int findFreeIndexOfSwapArea() {
        for (int i = 0; i < this.swapAreaBitMap.length * 8; ++i) {
            byte data = (byte) (this.swapAreaBitMap[i / 8] >> (7 - i % 8));
            if ((data & 0x01) == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 申请交换区块
     * @param swapAreaIndex 交换区块号
     */
    public synchronized void applySwapAreaBlock(int swapAreaIndex) {
        byte data = (byte) (0x01 << (7 - swapAreaIndex % 8));
        this.swapAreaBitMap[swapAreaIndex / 8] |= data;
        this.manager.getDashboard().refreshBlock(swapAreaIndex, 1);
    }

    /**
     * 分配交换区块
     * @return  分配块号
     */
    public synchronized int allocateSwapAreaBlock() {
        int swapAreaIndex = this.findFreeIndexOfSwapArea();
        this.applySwapAreaBlock(swapAreaIndex);
        return swapAreaIndex;
    }

    /**
     * 释放交换区块号
     * @param swapAreaIndex 释放块号
     */
    public synchronized void releaseSwapAreaBlock(int swapAreaIndex) {
        byte data = (byte) ~(0x01 << (7 - swapAreaIndex % 8));
        this.swapAreaBitMap[swapAreaIndex / 8] &= data;
        this.manager.getDashboard().refreshBlock(swapAreaIndex, 0);
    }

    /**
     * 读取外存一页（块）
     * @param blockNo 外存块号
     * @param data 存放数据
     */
    public synchronized void readPage(int blockNo, byte[] data) {
        // 拆分地址，提取所需信息
        int[] addressElement = this.splitAddress(blockNo * SECTOR_SIZE);
        int cylinder = addressElement[0];
        int track = addressElement[1];
        int sector = addressElement[2];
        // 定位sector文件
        File sectorFile  = new File("./disk" +
                "/cylinder_" + cylinder +
                "/track_" + track +
                "/sector_" + sector +
                ".txt");
        try {
            RandomAccessFile ra = new RandomAccessFile(sectorFile, "r");
            for (int i = 0; i < SECTOR_SIZE; ++i) {
                // 读低字节
                String formaldata = new String(new byte[]{ra.readByte(), ra.readByte()}, "ascii");
                // 16进制字符串转byte
                data[i] = (byte) Integer.parseInt(formaldata, 16);
                // 跳过分隔符空格
                ra.skipBytes(1);
                if (i % INLINE_BYTE_NUM  == INLINE_BYTE_NUM - 1) {
                    // 处于一行末尾字节，则还需跳过换行符
                    ra.skipBytes(2);
                }
            }
            ra.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 写入外存一页（块）
     * @param blockNo 外存块号
     * @param data 存放数据
     */
    public synchronized void writePage(int blockNo, byte[] data) {

        int baseAddress = blockNo * SECTOR_SIZE;
        // 拆分地址，提取所需信息
        int[] addressElement = this.splitAddress(baseAddress);
        int cylinder = addressElement[0];
        int track = addressElement[1];
        int sector = addressElement[2];
        // 定位sector文件
        File sectorFile  = new File("./disk" +
                "/cylinder_" + cylinder +
                "/track_" + track +
                "/sector_" + sector +
                ".txt");
        try {
            RandomAccessFile ra = new RandomAccessFile(sectorFile, "rw");
            for (int i = 0; i < SECTOR_SIZE; i++) {
                // byte数据转化为2位16进制
                String dataString = Integer.toHexString((int)data[i]);
                if (dataString.length() >= 2) {
                    dataString = dataString.substring(dataString.length() - 2);
                } else {
                    int appendZeroNum = 2 - dataString.length();
                    for(int j = 0; j < appendZeroNum; ++j) {
                        dataString = "0" + dataString;
                    }
                }
                // 写字节
                String formaldata = dataString.toUpperCase();
                ra.writeBytes(formaldata);
                // 跳过分隔符空格
                ra.skipBytes(1);
                if (i % INLINE_BYTE_NUM  == INLINE_BYTE_NUM - 1) {
                    // 处于一行末尾字节，则还需跳过换行符
                    ra.skipBytes(2);
                }
            }
            ra.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public byte[] getSwapAreaBitMap() {
        return swapAreaBitMap;
    }

    public void setSwapAreaBitMap(byte[] swapAreaBitMap) {
        this.swapAreaBitMap = swapAreaBitMap;
    }
}
