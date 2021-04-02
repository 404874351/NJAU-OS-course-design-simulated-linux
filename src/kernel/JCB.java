package kernel;

/**
 * 作业控制块
 *
 * 包含作业请求的各种信息，存储在外存JCB区
 *
 * @author ZJC
 */
public class JCB {
    /**
     * 作业id
     */
    private short id;
    /**
     * 作业优先级，数字越小，优先级越高
     */
    private short priority;
    /**
     * 作业进入时间
     */
    private short inTime;
    /**
     * 作业（进程）包含的指令数
     */
    private short instructionNum;
    /**
     * 所需页面数
     */
    private short needPageNum;
    /**
     * 作业指令集
     */
    private Instruction[] instructions;
    /**
     * 外存存储地址
     */
    private int externalMemeryAddress;


    public JCB(short id, short priority, short inTime, short instructionNum, short needPageNum) {
        this.id = id;
        this.priority = priority;
        this.inTime = inTime;
        this.instructionNum = instructionNum;
        this.needPageNum = needPageNum;
    }

    public short getId() {
        return id;
    }

    public void setId(short id) {
        this.id = id;
    }

    public short getPriority() {
        return priority;
    }

    public void setPriority(short priority) {
        this.priority = priority;
    }

    public short getInTime() {
        return inTime;
    }

    public void setInTime(short inTime) {
        this.inTime = inTime;
    }

    public short getInstructionNum() {
        return instructionNum;
    }

    public void setInstructionNum(short instructionNum) {
        this.instructionNum = instructionNum;
    }

    public Instruction[] getInstructions() {
        return instructions;
    }

    public void setInstructions(Instruction[] instructions) {
        this.instructions = instructions;
    }

    public int getExternalMemeryAddress() {
        return externalMemeryAddress;
    }

    public void setExternalMemeryAddress(int externalMemeryAddress) {
        this.externalMemeryAddress = externalMemeryAddress;
    }

    public short getNeedPageNum() {
        return needPageNum;
    }

    public void setNeedPageNum(short needPageNum) {
        this.needPageNum = needPageNum;
    }
}
