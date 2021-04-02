package kernel;

/**
 * 指令，用以记录每条指令的相关信息
 *
 * @author ZJC
 */
public class Instruction {
    /**
     * 指令编号，当作指令的逻辑地址
     */
    private int id;
    /**
     * 指令状态，用于区分类型
     */
    private int state;
    /**
     * 指令参数
     */
    private int argument;
    /**
     * 指令附带数据
     */
    private String extra;

    public Instruction() {
        this.id = 0;
        this.state = 0;
        this.argument = 0;
        this.extra = "";
    }

    public Instruction(int id, int state, int argument, String extra) {
        this.id = id;
        this.state = state;
        this.argument = argument;
        this.extra = extra;
    }

    @Override
    public String toString() {
        return this.id + "," + this.state + "," + this.argument + "," + this.extra;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getArgument() {
        return argument;
    }

    public void setArgument(int argument) {
        this.argument = argument;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
