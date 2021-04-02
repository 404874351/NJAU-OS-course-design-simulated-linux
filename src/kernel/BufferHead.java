package kernel;

/**
 * 缓冲区头部
 *
 * 用以建立缓冲区页与内存页框、外存物理块的关系
 *
 * @author ZJC
 */
public class BufferHead {
    /**
     * 设备编号
     */
    private int deviceNo;
    /**
     * 缓冲区编号
     */
    private int bufferNo;
    /**
     * 内存框号
     */
    private int frameNo;
    /**
     * 外存块号
     */
    private int blockNo;
    /**
     * 标志位
     */
    private int flag;

    public static final int FREE = 0;
    public static final int READ = 1;
    public static final int WRITE = 2;

    public BufferHead(int deviceNo, int bufferNo, int frameNo, int blockNo, int flag) {
        this.deviceNo = deviceNo;
        this.bufferNo = bufferNo;
        this.frameNo = frameNo;
        this.blockNo = blockNo;
        this.flag = flag;
    }

    public int getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(int deviceNo) {
        this.deviceNo = deviceNo;
    }

    public int getBufferNo() {
        return bufferNo;
    }

    public void setBufferNo(int bufferNo) {
        this.bufferNo = bufferNo;
    }

    public int getFrameNo() {
        return frameNo;
    }

    public void setFrameNo(int frameNo) {
        this.frameNo = frameNo;
    }

    public int getBlockNo() {
        return blockNo;
    }

    public void setBlockNo(int blockNo) {
        this.blockNo = blockNo;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }
}
