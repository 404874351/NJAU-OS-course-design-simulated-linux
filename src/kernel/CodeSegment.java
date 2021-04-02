package kernel;

import hardware.InternalMem;

/**
 * 代码段，存放执行的指令
 *
 * 仅被PCB调用{@link PCB}
 *
 * @author ZJC
 */
public class CodeSegment {
    /**
     * 指令数
     */
    private int instructionNum;
    /**
     * 指令集
     */
    private Instruction[] instruction;
    /**
     * 起始逻辑页号
     */
    private int logicPageStartNo;
    /**
     * 所占页面数
     */
    private int pageNum;

    public CodeSegment(int instructionNum, Instruction[] instruction, int logicPageStartNo, int pageNum) {
        this.instructionNum = instructionNum;
        this.instruction = instruction;
        this.logicPageStartNo = logicPageStartNo;
        this.pageNum = pageNum;
    }

    public Page initCodePage() {
        Page page = new Page();
        // 存储指令集
        byte[] codes = new byte[InternalMem.PAGE_SIZE];
        for (int i = 0; i < this.instructionNum; i++) {
            // 每条指令存储8B，一页最多可存64条
            codes[i * 8 + 0] = (byte) this.instruction[i].getId();
            codes[i * 8 + 1] = (byte)(this.instruction[i].getId() >> 8);
            codes[i * 8 + 2] = (byte) this.instruction[i].getState();
            codes[i * 8 + 3] = (byte)(this.instruction[i].getState() >> 8);
            codes[i * 8 + 4] = (byte) this.instruction[i].getArgument();
            codes[i * 8 + 5] = (byte)(this.instruction[i].getArgument() >> 8);
        }
        page.setLogicPageNo(this.logicPageStartNo);
        page.setInternalFrameNo(-1);
        page.setData(codes);

        return page;
    }

    public int getInstructionNum() {
        return instructionNum;
    }

    public void setInstructionNum(int instructionNum) {
        this.instructionNum = instructionNum;
    }

    public Instruction[] getInstruction() {
        return instruction;
    }

    public void setInstruction(Instruction[] instruction) {
        this.instruction = instruction;
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
