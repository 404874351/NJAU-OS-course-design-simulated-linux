package kernel;

import hardware.InternalMem;

import java.util.Stack;

/**
 * 堆栈段，存放栈结构
 *
 * 仅被PCB调用{@link PCB}
 *
 * @author ZJC
 */
public class StackSegment {
    /**
     * 栈结构
     */
    private Stack<Byte> stack;
    /**
     * 起始逻辑页号
     */
    private int logicPageStartNo;
    /**
     * 所占页面数
     */
    private int pageNum;

    public StackSegment(int logicPageStartNo, int pageNum) {
        this.stack = new Stack<>();
        this.logicPageStartNo = logicPageStartNo;
        this.pageNum = pageNum;
    }

    /**
     * 初始化堆栈段
     * @return 页信息
     */
    public Page initSatckPage() {
        Page page = new Page();
        page.setLogicPageNo(this.logicPageStartNo);
        page.setInternalFrameNo(-1);
        page.setData(new byte[InternalMem.PAGE_SIZE]);

        return page;
    }

    public Stack<Byte> getStack() {
        return stack;
    }

    public void setStack(Stack<Byte> stack) {
        this.stack = stack;
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
