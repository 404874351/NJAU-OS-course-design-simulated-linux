package interrupt;

import kernel.PCB;
import os.Manager;

/**
 * 文件操作中断
 *
 * @author ZJC
 */
public class FileOperationInterrupt extends Thread{
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

    public static final int CREATE_FILE = 0;
    public static final int CREATE_TIME = 3;

    public static final int CLOSE_FILE = 3;
    public static final int CLOSE_TIME = 3;

    public FileOperationInterrupt(Manager manager, PCB pcb, int type) {
        super("FileOperation");
        this.manager    = manager;
        this.pcb        = pcb;
        this.type       = type;
    }

    @Override
    public void run() {
        int startTime = this.manager.getClock().getCurrentTime();
        int runTime = 0;
        synchronized (this.manager.getFileSystem()) {
            this.manager.getFileSystem().setUserOperatePCB(this.pcb);
            if (this.type == CREATE_FILE) {
                // 创建文件
                this.manager.getDashboard().consoleLog("系统调用 -> 创建文件");
                runTime = CREATE_TIME;
                String filePath = this.pcb.getCodeSegment().getInstruction()[this.pcb.getIR() - 1].getExtra();
                this.manager.getFileSystem().createFile(filePath, -1, true);
            } else if (this.type == CLOSE_FILE) {
                // 关闭文件
                this.manager.getDashboard().consoleLog("系统调用 -> 关闭文件");
                runTime = CLOSE_TIME;
                String filePath = this.pcb.getCodeSegment().getInstruction()[this.pcb.getIR() - 1].getExtra();
                this.manager.getFileSystem().cmd("close " + filePath);
            }
        }
        // 等待文件操作结束
        while (this.manager.getClock().getCurrentTime() - startTime < runTime) { }
        this.manager.getDashboard().consoleLog("文件操作完成");
        // 如果这是最后一条指令，则直接撤销进程
        if (pcb.getPC() > pcb.getInstructionNum()) {
            this.manager.getSchedule().getBlockQueue().remove(this.pcb);
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
}
