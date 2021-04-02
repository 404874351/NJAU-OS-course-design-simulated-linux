package kernel;

import hardware.InternalMem;

/**
 * 页块封装类
 *
 * 封装了页表项的各项信息，以及针对整个页和块的相关操作
 * 该封装类仅为方便操作，并非仿真实际OS结构
 *
 * @author ZJC
 */
public class Page {
    /**
     * 逻辑页号
     */
    private int logicPageNo;
    /**
     * 内存页框号
     */
    private int internalFrameNo;
    /**
     * 外存块号
     */
    private int externalBlockNo;
    /**
     * 调入标志位，0未调入，1已调入
     */
    private int callFlag;
    /**
     * 修改标志位，0未修改，1已修改
     */
    private int modifyFlag;
    /**
     * 存储该页的数据，与内存页框数据保持一致
     */
    private byte[] data;
    // 后续需要改成缓冲区指针，使用缓冲区操作！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！

    public Page() {
        this.logicPageNo = 0;
        this.internalFrameNo = 0;
        this.externalBlockNo = 0;
        this.callFlag = 0;
        this.modifyFlag = 0;
        this.data = new byte[InternalMem.PAGE_SIZE];
    }

    public Page(int logicPageNo, int internalFrameNo, int externalBlockNo, int callFlag, int modifyFlag, byte[] data) {
        this.logicPageNo = logicPageNo;
        this.internalFrameNo = internalFrameNo;
        this.externalBlockNo = externalBlockNo;
        this.callFlag = callFlag;
        this.modifyFlag = modifyFlag;
        this.data = data;
    }

    public int getLogicPageNo() {
        return logicPageNo;
    }

    public void setLogicPageNo(int logicPageNo) {
        this.logicPageNo = logicPageNo;
    }

    public int getInternalFrameNo() {
        return internalFrameNo;
    }

    public void setInternalFrameNo(int internalFrameNo) {
        this.internalFrameNo = internalFrameNo;
    }

    public int getExternalBlockNo() {
        return externalBlockNo;
    }

    public void setExternalBlockNo(int externalBlockNo) {
        this.externalBlockNo = externalBlockNo;
    }

    public int getCallFlag() {
        return callFlag;
    }

    public void setCallFlag(int callFlag) {
        this.callFlag = callFlag;
    }

    public int getModifyFlag() {
        return modifyFlag;
    }

    public void setModifyFlag(int modifyFlag) {
        this.modifyFlag = modifyFlag;
    }

    public synchronized byte[] getData() {
        return data;
    }

    public synchronized void setData(byte[] data) {
        this.data = data;
    }
}
