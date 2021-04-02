package hardware;

import file.DiskInode;
import file.UserFileItem;
import interrupt.*;
import kernel.Instruction;
import kernel.PCB;
import kernel.Page;
import kernel.Schedule;
import os.Manager;

import java.util.Iterator;

/**
 * CPU中央处理器，负责程序运行的相关操作
 *
 * 用以处理中断，执行指令等
 *
 * @author ZJC
 */
public class CPU implements InterruptVector {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 程序计数器，下一条指令的执行编号
     */
    private int PC;
    /**
     * 指令寄存器，正在执行的指令编号
     */
    private int IR;
    /**
     * 状态寄存器 0用户态  1内核态
     */
    private int PSW;
    public static final int USER_STATE = 0;
    public static final int KERNEL_STATE = 1;
    /**
     * 当前运行态的PCB指针
     */
    private PCB runningPCB;
    /**
     * 允许调度标志，控制调度时机
     * 只有该标志打开后，才可以进行三级调度，否则CPU执行指令和调度操作将会出现混乱
     */
    private volatile boolean canSchedule;
    /**
     * 当前剩余时间片
     */
    private int timeSlice;
    /**
     * 因缺页中断而剩余的时间片
     */
    private int missPageRemainTimeSlice;

    public CPU(Manager manager) {
        this.manager                    = manager;
        this.PC                         = 1;
        this.IR                         = 0;
        this.PSW                        = USER_STATE;
        this.runningPCB                 = null;
        this.canSchedule                = false;
        this.timeSlice                  = 0;
        this.missPageRemainTimeSlice    = 0;

        this.manager.getDashboard().consoleSuccess("CPU初始化完成");
    }

    /**
     * 中断处理
     * @param interruptVector 中断向量 用以区别中断例程
     * @param index 中断处理的附加参数
     */
    public synchronized void interrupt(int interruptVector, int index) {
        switch (interruptVector) {
            // 时钟中断处理
            case CLOCK_INTERRUPT: {
                // 允许调度
                this.openSchedule();
                break;
            }
            // 缺页中断处理
            case MISS_PAGE_INTERRUPT: {
                this.switchToKernelState();
                // 缺页中断线程启动, index为缺页的逻辑页号
                new MissPageInterrupt(this.manager, this.runningPCB, index).start();
                // 设置缺页标志
                this.runningPCB.setMissPage(true);
                // 当前进程阻塞
                this.runningPCB.block(this.manager.getSchedule().getBlockQueue());
                break;
            }
            // 资源申请中断处理
            case APPLY_RESOURCE_INTERRUPT: {
                // 申请资源，index为资源种类
                this.manager.getDeadlock().applyResource(this.runningPCB, index);
                // 尝试分配资源
                int allocateResult = this.manager.getDeadlock().tryAllocateResource(this.runningPCB, index);
                // 如果返回阻塞标志，则阻塞进程
                if (allocateResult == -1) {
                    this.manager.getDashboard().consoleError("资源 " + index + " 分配失败，进程 " + this.runningPCB.getId() + " 阻塞");
                    this.runningPCB.block(this.manager.getSchedule().getResourceBlockQueues()[index]);
                }
                break;
            }
            // 资源释放中断处理
            case RELEASE_RESOURCE_INTERRUPT: {
                // 释放资源，index为资源种类
                this.manager.getDeadlock().releaseResource(this.runningPCB, index);
                // 尝试重分配资源
                if (this.manager.getSchedule().getResourceBlockQueues()[index].size() > 0) {
                    int reallocateResult = this.manager.getDeadlock().tryReallocateResource(this.manager.getSchedule().getResourceBlockQueues()[index].get(0), index);
                    // 如果返回唤醒标志，则唤醒阻塞队列队首进程
                    if (reallocateResult == -1) {
                        this.manager.getSchedule().getResourceBlockQueues()[index].get(0).wakeUp(this.manager.getSchedule().getResourceBlockQueues()[index]);
                    }
                }
                break;
            }
            // 输入中断
            case INPUT_INTERRUPT: {
                // index 为页框号
                PCB tempPCB = this.runningPCB;
                tempPCB.block(this.manager.getSchedule().getBlockQueue());
                String filePath = tempPCB.getCodeSegment().getInstruction()[this.IR - 1].getExtra().split(" ")[1];
                Iterator<UserFileItem> iterator = tempPCB.getUserOpenFileTable().iterator();
                while (iterator.hasNext()) {
                    UserFileItem userFileItem = iterator.next();
                    try {
                        DiskInode currentInode = this.manager.getFileSystem().getDiskInodeMap().get("" + userFileItem.getFp().getInode().getInodeNo());
                        DiskInode targetInode = this.manager.getFileSystem().getDiskInodeByPath(filePath);
                        if (currentInode == targetInode) {
                            this.switchToKernelState();
                            new IOInterrupt(this.manager, tempPCB, IOInterrupt.INPUT, index, userFileItem.getFd()).start();
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            // 输出中断
            case OUTPUT_INTERRUPT: {
                // index 为页框号
                PCB tempPCB = this.runningPCB;
                tempPCB.block(this.manager.getSchedule().getBlockQueue());
                String filePath = tempPCB.getCodeSegment().getInstruction()[this.IR - 1].getExtra().split(" ")[1];
                Iterator<UserFileItem> iterator = tempPCB.getUserOpenFileTable().iterator();
                while (iterator.hasNext()) {
                    UserFileItem userFileItem = iterator.next();
                    try {
                        if (this.manager.getFileSystem().getDiskInodeMap().get("" + userFileItem.getFp().getInode().getInodeNo())
                                == this.manager.getFileSystem().getDiskInodeByPath(filePath)) {
                            this.switchToKernelState();
                            new IOInterrupt(this.manager, tempPCB, IOInterrupt.OUTPUT, index, userFileItem.getFd()).start();
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            // 作业请求中断
            case JOB_REQUEST_INTERRUPT: {
                this.switchToKernelState();
                new JobRequestInterrupt(this.manager).start();
                break;
            }
            // 文件创建中断
            case CREATE_FILE_INTERRUPT: {
                this.switchToKernelState();
                PCB tempPCB = this.runningPCB;
                tempPCB.block(this.manager.getSchedule().getBlockQueue());
                new FileOperationInterrupt(this.manager, tempPCB, FileOperationInterrupt.CREATE_FILE).start();
                break;
            }
            // 文件关闭中断
            case CLOSE_FILE_INTERRUPT: {
                this.switchToKernelState();
                PCB tempPCB = this.runningPCB;
                tempPCB.block(this.manager.getSchedule().getBlockQueue());
                new FileOperationInterrupt(this.manager, tempPCB, FileOperationInterrupt.CLOSE_FILE).start();
                break;
            }

            default: {
                break;
            }
        }
        this.switchToUserState();
    }

    /**
     * 执行当前指令
     */
    public synchronized void execute() {
        // 刷新GUI
        this.manager.getDashboard().refreshRunningProcess();
        if (this.runningPCB == null) {
            return;
        }
        // 执行指令需要内存中有代码段数据
        int codeSegmentPageItemAddress = this.runningPCB.getPageTableBaseAddress() +
                this.runningPCB.getCodeSegment().getLogicPageStartNo() * InternalMem.PAGE_TABLE_ITEM_SIZE;
        this.manager.getAddressLine().setAddress((short) codeSegmentPageItemAddress);
        Page codePage = this.manager.getInMem().readPageItem(this.manager.getAddressLine());
        if (codePage.getCallFlag() == 0) {
            // 代码段页面未装入，则执行缺页中断，装入代码页
            this.manager.getDashboard().consoleLog("代码段数据未装入内存，优先装入代码段");
            this.interrupt(InterruptVector.MISS_PAGE_INTERRUPT, codePage.getLogicPageNo());
            return;
        } else {
            // 代码段页面已经装入，则看作一次访问内存，并更新快表
            this.manager.getInMem().readPage(codePage);
            this.runningPCB.accessPage(codePage.getLogicPageNo());
            this.manager.getMmu().updateTLB(codePage.getLogicPageNo(), codePage.getInternalFrameNo());
        }

        // 指令指针自增并获取当前指令
        this.IR = this.PC++;
        Instruction currentInstrction = new Instruction();
        int id          = ((((int) codePage.getData()[8 * (IR - 1) + 1]) << 8) & 0x0000FF00) | (((int) codePage.getData()[8 * (IR - 1) + 0]) & 0x0000FF);
        int state       = ((((int) codePage.getData()[8 * (IR - 1) + 3]) << 8) & 0x0000FF00) | (((int) codePage.getData()[8 * (IR - 1) + 2]) & 0x0000FF);
        int argument    = ((((int) codePage.getData()[8 * (IR - 1) + 5]) << 8) & 0x0000FF00) | (((int) codePage.getData()[8 * (IR - 1) + 4]) & 0x0000FF);
        currentInstrction.setId(id);
        currentInstrction.setState(state);
        currentInstrction.setArgument(argument);
        currentInstrction.setExtra(this.runningPCB.getCodeSegment().getInstruction()[this.IR - 1].getExtra());

        this.manager.getDashboard().consoleLog("执行进程 " + this.runningPCB.getId() + " :" +
                " 指令 " + currentInstrction.getId() +
                " 类型 " + currentInstrction.getState() +
                " 参数 " + currentInstrction.getArgument() +
                " 附加数据 " + currentInstrction.getExtra());
        switch (currentInstrction.getState()) {
            // 0 system     系统调用，本系统中仿真为输入、输出、创建文件操作
            case 0: {
                int type = currentInstrction.getArgument();
                String extra = currentInstrction.getExtra();
                if (type == 0) {
                    // 创建文件
                    this.interrupt(InterruptVector.CREATE_FILE_INTERRUPT, 0);
                } else if (type == 1) {
                    // 输入操作
                    try {
                        int logicAddress = (this.runningPCB.getDataSegment().getLogicPageStartNo() + Integer.parseInt(extra.split(" ")[0])) * InternalMem.PAGE_SIZE;
                        String filePath = extra.split(" ")[1];
                        short physicAddress = this.manager.getMmu().resolveLogicAddress((short) logicAddress, this.runningPCB.getPageTableBaseAddress());
                        if (physicAddress == -1) {
                            // 出现缺页，则PC、IR回退一步
                            --this.IR;
                            --this.PC;
                            // 保存当前时间片
                            this.missPageRemainTimeSlice = this.timeSlice;
                            // 发出缺页中断请求
                            this.interrupt(InterruptVector.MISS_PAGE_INTERRUPT, logicAddress / InternalMem.PAGE_SIZE);
                        } else {
                            this.runningPCB.accessPage(logicAddress / InternalMem.PAGE_SIZE);
                            this.interrupt(InterruptVector.INPUT_INTERRUPT, physicAddress / InternalMem.PAGE_SIZE);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else if (type == 2) {
                    // 输出操作
                    int logicAddress = (this.runningPCB.getDataSegment().getLogicPageStartNo() + Integer.parseInt(extra.split(" ")[0])) * InternalMem.PAGE_SIZE;
                    String filePath = extra.split(" ")[1];
                    short physicAddress = this.manager.getMmu().resolveLogicAddress((short) logicAddress, this.runningPCB.getPageTableBaseAddress());
                    if (physicAddress == -1) {
                        // 出现缺页，则PC、IR回退一步
                        --this.IR;
                        --this.PC;
                        // 保存当前时间片
                        this.missPageRemainTimeSlice = this.timeSlice;
                        // 发出缺页中断请求
                        this.interrupt(InterruptVector.MISS_PAGE_INTERRUPT, logicAddress / InternalMem.PAGE_SIZE);
                    } else {
                        this.runningPCB.accessPage(logicAddress / InternalMem.PAGE_SIZE);
                        this.interrupt(InterruptVector.OUTPUT_INTERRUPT, physicAddress / InternalMem.PAGE_SIZE);
                    }
                } else if (type == 3) {
                    // 关闭文件
                    this.interrupt(InterruptVector.CLOSE_FILE_INTERRUPT, 0);
                }
                break;
            }
            // 1 calculate  计算指令，CPU不需要调用任何额外资源，可直接运行
            case 1: {
                // CPU内部进行计算操作，不做任何处理
                this.manager.getDashboard().consoleLog("计算指令");
                break;
            }
            // 2 load       读取指令，对内存数据进行读取
            case 2: {
                // 解析逻辑地址，返回 -1，则表示缺页
                short physicAddress = this.manager.getMmu().resolveLogicAddress((short)currentInstrction.getArgument(), this.runningPCB.getPageTableBaseAddress());
                if (physicAddress == -1) {
                    // 出现缺页，则PC、IR回退一步
                    --this.IR;
                    --this.PC;
                    // 保存当前时间片
                    this.missPageRemainTimeSlice = this.timeSlice;
                    // 发出缺页中断请求
                    this.interrupt(InterruptVector.MISS_PAGE_INTERRUPT, currentInstrction.getArgument() / InternalMem.PAGE_SIZE);
                } else {
                    this.manager.getAddressLine().setAddress(physicAddress);

                    short loadData = this.manager.getInMem().readData(this.manager.getAddressLine());
                    this.runningPCB.accessPage(currentInstrction.getArgument() / InternalMem.PAGE_SIZE);
                    this.manager.getDashboard().consoleLog("从内存地址" + physicAddress + " 读取数据 " + loadData);
                }
                break;
            }
            // 3 store      写入指令，对内存数据进行写入
            case 3: {
                // 解析逻辑地址，返回 -1，则表示缺页
                short physicAddress = this.manager.getMmu().resolveLogicAddress((short)currentInstrction.getArgument(), this.runningPCB.getPageTableBaseAddress());
                if (physicAddress == -1) {
                    // 出现缺页，则PC、IR回退一步
                    --this.IR;
                    --this.PC;
                    // 保存当前时间片
                    this.missPageRemainTimeSlice = this.timeSlice;
                    // 发出缺页中断请求
                    this.interrupt(InterruptVector.MISS_PAGE_INTERRUPT, currentInstrction.getArgument() / InternalMem.PAGE_SIZE);
                } else {
                    this.manager.getAddressLine().setAddress(physicAddress);
                    this.manager.getDataLine().setData((short)0x6666);

                    this.manager.getInMem().writeData(this.manager.getAddressLine(), this.manager.getDataLine());
                    this.runningPCB.accessPage(currentInstrction.getArgument() / InternalMem.PAGE_SIZE);
                    // 设置页表项修改位为1
                    int pageItemAddress = this.runningPCB.getPageTableBaseAddress() +
                            currentInstrction.getArgument() / InternalMem.PAGE_SIZE * InternalMem.PAGE_TABLE_ITEM_SIZE;

                    this.manager.getAddressLine().setAddress((short) pageItemAddress);
                    Page page = this.manager.getInMem().readPageItem(this.manager.getAddressLine());
                    page.setModifyFlag(1);
                    this.manager.getAddressLine().setAddress((short) pageItemAddress);
                    this.manager.getInMem().writePageItem(this.manager.getAddressLine(), page);
                    this.manager.getDashboard().consoleLog("向内存地址" + physicAddress + " 写入数据 " + 0x6666);
                }
                break;
            }
            // 4 switch     进程切换，直接进行调度
            case 4: {
                // 时间片置1，执行指令后时间片统一 -1，完成进程强制切换
                this.timeSlice = 1;
                this.manager.getDashboard().consoleLog("切换指令");
                break;
            }
            // 5 jump       跳转指令，跳过代码段执行
            case 5: {
                // 设置PC指向跳转地址
                this.PC = currentInstrction.getArgument();
                this.manager.getDashboard().consoleLog("跳转指令");
                break;
            }
            // 6 apply      资源申请，申请一个系统资源
            case 6: {
                // 发出资源申请中断
                this.interrupt(InterruptVector.APPLY_RESOURCE_INTERRUPT, currentInstrction.getArgument());
                break;
            }
            // 7 release    资源释放，释放一个系统资源
            case 7: {
                // 发出资源释放中断
                this.interrupt(InterruptVector.RELEASE_RESOURCE_INTERRUPT, currentInstrction.getArgument());
                break;
            }
            default: {
                break;
            }
        }

        // 时间片 -1
        --this.timeSlice;
    }

    /**
     * 恢复CPU现场
     * @param pcb 即将进入运行态的进程 PCB
     */
    public synchronized void recoverSpot(PCB pcb) {
        this.PC         = pcb.getPC();
        this.IR         = pcb.getIR();
        this.timeSlice  = Schedule.SYSTEM_TIME_SLICE;
        // 进程设置运行态
        this.runningPCB = pcb;
        // 初始化快表
        this.manager.getMmu().initTLB();
        // 如果因为缺页中断而恢复CPU现场，则使用之前的时间片
        if (this.missPageRemainTimeSlice != 0) {
            this.timeSlice = this.missPageRemainTimeSlice;
            this.missPageRemainTimeSlice = 0;
        }
        // 更新GUI
        this.manager.getDashboard().refreshRunningProcess();
    }

    /**
     * 保护CPU现场
     */
    public synchronized void protectSpot() {
        this.runningPCB.setIR((short)this.IR);
        this.runningPCB.setPC((short)this.PC);
        // 进程解除运行态
        this.runningPCB = null;
        // 更新GUI
        this.manager.getDashboard().refreshRunningProcess();
    }

    /**
     * 打开调度
     */
    public synchronized void openSchedule() {
        this.canSchedule = true;
    }

    /**
     * 关闭调度
     */
    public synchronized void closeSchedule() {
        this.canSchedule = false;
    }

    /**
     * 切换内核态
     */
    public synchronized void switchToKernelState() {
        if (this.PSW == KERNEL_STATE) {
            return;
        }
        this.PSW = KERNEL_STATE;
        this.manager.getDashboard().consoleLog("CPU -> 内核态");
        this.manager.getDashboard().refreshCPU();
    }

    /**
     * 切换用户态
     */
    public synchronized void switchToUserState() {
        if (this.PSW == USER_STATE) {
            return;
        }
        this.PSW = USER_STATE;
        this.manager.getDashboard().consoleLog("CPU -> 用户态");
        this.manager.getDashboard().refreshCPU();
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public int getPC() {
        return PC;
    }

    public void setPC(int PC) {
        this.PC = PC;
    }

    public int getIR() {
        return IR;
    }

    public void setIR(int IR) {
        this.IR = IR;
    }

    public int getPSW() {
        return PSW;
    }

    public void setPSW(int PSW) {
        this.PSW = PSW;
    }

    public PCB getRunningPCB() {
        return runningPCB;
    }

    public void setRunningPCB(PCB runningPCB) {
        this.runningPCB = runningPCB;
    }

    public boolean isCanSchedule() {
        return canSchedule;
    }

    public void setCanSchedule(boolean canSchedule) {
        this.canSchedule = canSchedule;
    }

    public int getTimeSlice() {
        return timeSlice;
    }

    public void setTimeSlice(int timeSlice) {
        this.timeSlice = timeSlice;
    }
}
