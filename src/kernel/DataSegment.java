package kernel;

import hardware.InternalMem;

import java.util.Arrays;

/**
 * 数据段，存放进程的相关数据
 *
 * 仅被PCB调用{@link PCB}
 *
 * @author ZJC
 */
public class DataSegment {
    /**
     * 存放的数据
     */
    private byte[] data;
    /**
     * 起始逻辑页号
     */
    private int logicPageStartNo;
    /**
     * 所占页面数
     */
    private int pageNum;

    public DataSegment(byte[] data, int logicPageStartNo, int pageNum) {
        this.data = data;
        this.logicPageStartNo = logicPageStartNo;
        this.pageNum = pageNum;
    }

    public Page[] initDataPages() {
        // 数据段随机占用2-10页，需要依次初始化
        Page[] pages = new Page[this.pageNum];
        for (int i = 0; i < this.pageNum; ++i) {
            pages[i] = new Page();
            pages[i].setLogicPageNo(this.logicPageStartNo + i);
            pages[i].setInternalFrameNo(-1);
            pages[i].setData(Arrays.copyOfRange(this.data, InternalMem.PAGE_SIZE * i, InternalMem.PAGE_SIZE * (i + 1)));
        }
        return pages;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getLogicPageStartNo() {
        return logicPageStartNo;
    }

    public void setLogicPageStartNo(int logicPageStartNo) {
        this.logicPageStartNo = logicPageStartNo;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }
}
