package kernel;

import hardware.CPU;
import hardware.InternalMem;
import interrupt.InterruptVector;
import os.Manager;

import java.util.Vector;

/**
 * 调度模块
 *
 * 负责作业调度（高级）、内存调度（中级）、进程调度（低级）
 *
 * @author ZJC
 */
public class Schedule extends Thread{
    /**
     * 系统时间片长度 4
     */
    public static final int SYSTEM_TIME_SLICE = 4;
    /**
     * 最大并发进程数，等于PCB池容量，当前设置为 14
     */
    public static final int MAX_CONCURRENT_PROCESS_NUM = InternalMem.PCB_POOL_PAGE_NUM;
    /**
     * 页框数最小阈值，当前用户区空闲页框数小于该阈值，则需挂起进程
     */
    public static final int MIN_FRAME_NUM_THRESHOLD = 6;
    /**
     * 页框数最大阈值，当前用户区空闲页框数大于该阈值，则需恢复挂起进程
     */
    public static final int MAX_FRAME_NUM_THRESHOLD = 12;
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 作业管理模块
     */
    private JobManage jobManage;
    /**
     * 全体PCB队列
     */
    private Vector<PCB> allPCBQueue;
    /**
     * 就绪队列
     */
    private Vector<PCB> readyQueue;
    /**
     * 阻塞队列
     */
    private Vector<PCB> blockQueue;
    /**
     * 资源阻塞队列
     */
    private Vector<PCB>[] resourceBlockQueues;
    /**
     * 挂起队列
     */
    private Vector<PCB> suspendQueue;
    /**
     * 完成队列
     */
    private Vector<PCB> finishQueue;
    /**
     * 外存 后备队列
     */
    private Vector<JCB> reserveQueue;
    /**
     * 用于中级调度的LRU算法队列
     */
    private Vector<PCB> LRU;


    public Schedule(Manager manager) {
        super("Schedule");
        this.manager                = manager;
        this.jobManage              = new JobManage(this);
        this.LRU                    = new Vector<>();

        this.allPCBQueue            = new Vector<>();
        this.readyQueue             = new Vector<>();
        this.suspendQueue           = new Vector<>();
        this.finishQueue            = new Vector<>();
        this.reserveQueue           = new Vector<>();
        this.blockQueue             = new Vector<>();
        this.resourceBlockQueues    = new Vector[Deadlock.RESOURCE_TYPE_NUM];
        for (int i = 0; i < this.resourceBlockQueues.length; ++i) {
            this.resourceBlockQueues[i] = new Vector<>();
        }

        this.manager.getDashboard().consoleSuccess("调度模块初始化完成");
    }

    @Override
    public void run() {
        while (true) {
            // 等待可调度时机
            while (!this.manager.getCpu().isCanSchedule()) { }
            // 每5个时钟中断读取一次新作业请求
            if (this.manager.getClock().getCurrentTime() % 5 == 0) {
                this.getJobManage().readJobs();
            }
            // 高级调度
            this.highLevelSchdule();
            // 中级调度
            this.middleLevelSchdule();
            // 低级调度
            this.lowLevelSchdule();

            // 关闭三级调度
            this.manager.getCpu().closeSchedule();
            // 刷新GUI
            this.manager.getDashboard().refreshRunningProcess();

            // 判断是否存在运行态进程
            if (this.manager.getCpu().getRunningPCB() == null) {
                // 不存在，则CPU空闲
                this.manager.getDashboard().consoleLog("CPU空闲中...");
            } else {
                 // 更新优先级
                this.accessPCB(this.manager.getCpu().getRunningPCB());
                // 如果存在因挂起而释放的占用资源，则优先申请资源
                if (this.manager.getCpu().getRunningPCB().hasSuspendResource()) {
                    for (int i = 0; i < this.manager.getCpu().getRunningPCB().getSuspendResource().length; ++i) {
                        if (this.manager.getCpu().getRunningPCB().getSuspendResource()[i] != 0) {
                            --this.manager.getCpu().getRunningPCB().getSuspendResource()[i];
                            this.manager.getCpu().interrupt(InterruptVector.APPLY_RESOURCE_INTERRUPT, i);
                        }
                        // 因为申请资源失败而被阻塞
                        if (this.manager.getCpu().getRunningPCB() == null) {
                            // 刷新GUI
                            this.manager.getDashboard().refreshRunningProcess();
                            break;
                        }
                    }
                }
                // 刷新GUI
                this.manager.getDashboard().refreshCPU();
                this.manager.getDashboard().refreshRunningProcess();
                this.manager.getDashboard().refreshPageTable();
                this.manager.getDashboard().refreshTLB();

                // 存在，则执行当前指令
                this.manager.getCpu().execute();
                // 判断当前指令执行后，进程是否还在运行态（否则已经发生阻塞）
                if (this.manager.getCpu().getRunningPCB() != null) {
                    // 判断进程是否运行完毕
                    if (this.manager.getCpu().getPC() > this.manager.getCpu().getRunningPCB().getInstructionNum()) {
                        // 进程运行完毕
                        // CPU切换内核态
                        this.getManager().getCpu().switchToKernelState();
                        // 撤销当前进程
                        this.manager.getCpu().getRunningPCB().cancel();
                        this.manager.getCpu().setRunningPCB(null);
                        this.manager.getCpu().setTimeSlice(0);
                        // CPU切换用户态
                        this.getManager().getCpu().switchToUserState();
                    } else {
                        // 进程未运行完毕
                        // 如果时间片用完，则当前进程 运行态 -> 就绪态
                        if (this.manager.getCpu().getTimeSlice() == 0) {
                            // CPU切换内核态
                            this.getManager().getCpu().switchToKernelState();
                            // 当前PCB 运行态 -> 就绪态
                            synchronized (this) {
                                this.readyQueue.add(this.manager.getCpu().getRunningPCB());
                            }
                            // 保护CPU现场
                            this.manager.getCpu().getRunningPCB().setState(PCB.READY_STATE);
                            this.manager.getCpu().protectSpot();
                            // CPU切换用户态
                            this.getManager().getCpu().switchToUserState();
                            this.manager.getDashboard().consoleLog("时间片轮转调度");
                        }
                    }
                }
            }

            // 刷新GUI
            this.manager.getDashboard().refreshQueues();
        }
    }

    /**
     * 高级调度
     */
    public synchronized void highLevelSchdule() {
        // 尝试从后备队列中挑选作业，创建相应进程
        this.getJobManage().tryAddProcess();
    }
    /**
     * 中级调度
     */
    public synchronized void middleLevelSchdule() {
        int currentFreeFrameNum = this.manager.getInMem().getFreeFrameNumOfUserArea();
        if (currentFreeFrameNum < MIN_FRAME_NUM_THRESHOLD) {
            if (this.LRU.size() == 0) {
                return;
            }
            this.manager.getDashboard().consoleError("当前内存空闲页框数 " + currentFreeFrameNum +
                    " 小于内存紧张阈值 " + MIN_FRAME_NUM_THRESHOLD +
                    " 尝试挂起进程");
            // 挂起进程
            if (this.readyQueue.contains(this.LRU.get(0))) {
                this.LRU.get(0).suspend(this.readyQueue);
            } else {
                this.readyQueue.get(0).suspend(this.readyQueue);
            }

        } else if (currentFreeFrameNum > MAX_FRAME_NUM_THRESHOLD) {
            if (this.suspendQueue.size() == 0) {
                return;
            }
            this.manager.getDashboard().consoleSuccess("当前内存空闲页框数 " + currentFreeFrameNum +
                    " 大于内存充裕阈值 " + MAX_FRAME_NUM_THRESHOLD +
                    " 尝试调入进程");
            // 恢复挂起队列首个进程
            PCB resumePCB = this.suspendQueue.get(0);
            this.manager.getDashboard().consoleInfo("进程 " + resumePCB.getId() + " 挂起恢复");
            // 将代码段优先放入内存用户区
            resumePCB.wakeUp(this.suspendQueue);
            int pageItemAddress = resumePCB.getPageTableBaseAddress() + 1 * InternalMem.PAGE_TABLE_ITEM_SIZE;
            this.manager.getAddressLine().setAddress((short) pageItemAddress);
            Page page = this.manager.getInMem().readPageItem(this.manager.getAddressLine());

            // 使用缓冲区获取外存资源
            this.manager.getDeviceManage().useBuffer(page, BufferHead.READ);
            while (page.getData() == null) { }
            // 更新页表项，并申请新页框
            int frameIndex = this.manager.getInMem().allocateUserArea();
            page.setInternalFrameNo(frameIndex + InternalMem.USER_AREA_START_PAGE_NO);
            page.setCallFlag(1);

            this.manager.getInMem().writePage(page);
            this.manager.getAddressLine().setAddress((short) pageItemAddress);
            this.manager.getInMem().writePageItem(this.manager.getAddressLine(), page);

            // 刷新GUI
            this.manager.getDashboard().consoleInfo("进程 " + resumePCB.getId() +
                    " 逻辑页号：" + page.getLogicPageNo() +
                    " 分配主存框号：" + page.getInternalFrameNo());
        }
    }

    /**
     * 低级调度
     */
    public synchronized void lowLevelSchdule() {
        // 就绪队列为空，或已有进程处于运行态，则低级调度结束
        if (this.readyQueue.size() == 0 || this.manager.getCpu().getRunningPCB() != null) {
            return;
        }
        // 搜索优先级最高的PCB
        int indexOfMaxPriority = 0;
        for (int i = 0; i < this.readyQueue.size(); ++i) {
            // 如果某进程刚刚进行缺页中断，则优先调度该进程
            if (this.readyQueue.get(i).isMissPage()) {
                this.readyQueue.get(i).setMissPage(false);
                indexOfMaxPriority = i;
                this.manager.getDashboard().consoleLog("检测到某进程刚刚发生缺页中断，优先调度");
                break;
            }
            // 判定优先级
            if (this.readyQueue.get(i).getPriority() < this.readyQueue.get(indexOfMaxPriority).getPriority()) {
                indexOfMaxPriority = i;
            }
        }
        this.manager.getDashboard().consoleLog("低级调度 -> 进程 " + this.readyQueue.get(indexOfMaxPriority).getId() + " 进入运行态");
        // CPU切换内核态
        this.getManager().getCpu().switchToKernelState();
        // 恢复CPU现场
        this.readyQueue.get(indexOfMaxPriority).setState(PCB.RUNNING_STATE);
        this.getManager().getCpu().recoverSpot(this.readyQueue.get(indexOfMaxPriority));
        // 指定PCB 就绪态 -> 运行态
        this.readyQueue.remove(indexOfMaxPriority);
        // CPU切换用户态
        this.getManager().getCpu().switchToUserState();
    }

    public synchronized void accessPCB(PCB pcb) {
        for (int i = 0; i < this.LRU.size(); ++i) {
            // 遍历查找，是否之前访问过该进程，则重新排列
            if (this.LRU.get(i) == pcb) {
                this.LRU.remove(i);
                break;
            }
        }
        // 最新访问进程放在队尾
        this.LRU.add(pcb);
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public JobManage getJobManage() {
        return jobManage;
    }

    public void setJobManage(JobManage jobManage) {
        this.jobManage = jobManage;
    }

    public Vector<PCB> getAllPCBQueue() {
        return allPCBQueue;
    }

    public void setAllPCBQueue(Vector<PCB> allPCBQueue) {
        this.allPCBQueue = allPCBQueue;
    }

    public Vector<PCB> getReadyQueue() {
        return readyQueue;
    }

    public void setReadyQueue(Vector<PCB> readyQueue) {
        this.readyQueue = readyQueue;
    }

    public Vector<PCB> getBlockQueue() {
        return blockQueue;
    }

    public void setBlockQueue(Vector<PCB> blockQueue) {
        this.blockQueue = blockQueue;
    }

    public Vector<PCB> getSuspendQueue() {
        return suspendQueue;
    }

    public void setSuspendQueue(Vector<PCB> suspendQueue) {
        this.suspendQueue = suspendQueue;
    }

    public Vector<PCB> getFinishQueue() {
        return finishQueue;
    }

    public void setFinishQueue(Vector<PCB> finishQueue) {
        this.finishQueue = finishQueue;
    }

    public Vector<JCB> getReserveQueue() {
        return reserveQueue;
    }

    public void setReserveQueue(Vector<JCB> reserveQueue) {
        this.reserveQueue = reserveQueue;
    }

    public Vector<PCB>[] getResourceBlockQueues() {
        return resourceBlockQueues;
    }

    public void setResourceBlockQueues(Vector<PCB>[] resourceBlockQueues) {
        this.resourceBlockQueues = resourceBlockQueues;
    }

    public Vector<PCB> getLRU() {
        return LRU;
    }

    public void setLRU(Vector<PCB> LRU) {
        this.LRU = LRU;
    }
}
