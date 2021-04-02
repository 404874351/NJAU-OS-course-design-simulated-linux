package interrupt;

import hardware.InternalMem;
import kernel.PCB;
import os.Manager;

/**
 * 输入输出中断
 * 本次设计方针为读文件、写文件
 * @author ZJC
 */
public class IOInterrupt extends Thread{
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 请求缺页中断的进程 PCB
     */
    private PCB pcb;
    /**
     * 中断类型
     */
    private int type;
    /**
     * IO操作对应的页框号
     */
    private int frameNo;
    /**
     * IO操作对应的文件描述符
     */
    private int fd;

    public static final int INPUT = 1;
    public static final int INPUT_TIME = 4;

    public static final int OUTPUT = 2;
    public static final int OUTPUT_TIME = 4;

    public IOInterrupt(Manager manager, PCB pcb, int type, int frameNo, int fd) {
        super("IO");
        this.manager    = manager;
        this.pcb        = pcb;
        this.type       = type;
        this.frameNo    = frameNo;
        this.fd         = fd;
    }

    @Override
    public void run() {
        int startTime = this.manager.getClock().getCurrentTime();
        int runTime = 0;
        synchronized (this.manager.getFileSystem()) {
            this.manager.getFileSystem().setUserOperatePCB(this.pcb);
            if (this.type == INPUT) {
                // 输入操作，读文件
                this.manager.getDashboard().consoleLog("系统调用 -> 输入操作");
                runTime = INPUT_TIME;
                this.manager.getFileSystem().read(this.fd, this.frameNo, InternalMem.PAGE_SIZE);
            } else if (this.type == OUTPUT) {
                // 输出操作，写文件
                this.manager.getDashboard().consoleLog("系统调用 -> 输出操作");
                runTime = OUTPUT_TIME;
                this.manager.getFileSystem().write(this.fd, this.frameNo, InternalMem.PAGE_SIZE);
            }
        }
        // 等待输入输出结束
        while (this.manager.getClock().getCurrentTime() - startTime < runTime) { }
        this.manager.getDashboard().consoleLog("IO操作完成");
        // 如果这是最后一条指令，则直接撤销进程
        if (pcb.getPC() > pcb.getInstructionNum()) {
            synchronized (this.manager.getSchedule()) {
                this.manager.getSchedule().getBlockQueue().remove(this.pcb);
            }
            this.pcb.cancel();
            return;
        }
        // 进程唤醒
        this.pcb.wakeUp(this.manager.getSchedule().getBlockQueue());
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public PCB getPcb() {
        return pcb;
    }

    public void setPcb(PCB pcb) {
        this.pcb = pcb;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getFrameNo() {
        return frameNo;
    }

    public void setFrameNo(int frameNo) {
        this.frameNo = frameNo;
    }

    public int getFd() {
        return fd;
    }

    public void setFd(int fd) {
        this.fd = fd;
    }
}
