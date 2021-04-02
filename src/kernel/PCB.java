package kernel;

import file.DirectoryItem;
import file.SystemFileItem;
import file.UserFileItem;
import hardware.ExternalMem;
import hardware.InternalMem;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;

/**
 * 进程控制块
 *
 * 负责进程的信息存储，调度管理等
 *
 * @author ZJC
 */
public class PCB {
    // 进程状态常量
    public static final short READY_STATE = 0;
    public static final short RUNNING_STATE = 1;
    public static final short BLOCK_STATE = 2;
    public static final short SUSPEND_STATE = 3;
    public static final short FINISH_STATE = 4;

    /**
     * 调度模块
     */
    private Schedule schedule;
    /**
     * 进程编号
     */
    private short id;
    /**
     * 进程优先级
     * 取值范围1-5，数字越小，优先级越大
     */
    private short priority;
    /**
     * 指令数量
     */
    private short instructionNum;
    /**
     * 程序计数器，下一条指令的执行编号
     */
    private short PC;
    /**
     * 指令寄存器，正在执行的指令编号
     */
    private short IR;
    /**
     * 进程状态寄存器，有运行、就绪、阻塞、挂起、完成
     */
    private short state;
    /**
     * 进程创建时间
     */
    private short inTime;
    /**
     * 进程结束时间
     */
    private short endTime;
    /**
     * 进程周转时间
     */
    private short turnTime;
    /**
     * 进程运行时间
     */
    private short runTime;
    /**
     * 进入阻塞队列时间
     */
    private int inBlockQueueTime;
    /**
     * 页表基址
     */
    private int pageTableBaseAddress;
    /**
     * 分配的页框数
     */
    private short allocatePageFrameNum;
    /**
     * 在PCB池中的位置
     */
    private int indexOfPool;
    /**
     * 是否刚刚出现缺页
     */
    private boolean missPage;
    /**
     * 用于页面置换的LRU算法队列
     */
    private Vector<Integer> LRU;
    /**
     * 挂起资源列表，记录因挂起而释放的资源
     */
    private int[] suspendResource;
    /**
     * 代码段
     */
    private CodeSegment codeSegment;
    /**
     * 数据段
     */
    private DataSegment dataSegment;
    /**
     * 堆栈段
     */
    private StackSegment stackSegment;
    /**
     * 用户打开文件表
     */
    private Vector<UserFileItem> userOpenFileTable;
    /**
     * 打开文件计数，用以分配 fd
     */
    private int openFileCount;

    public PCB(Schedule schedule) {
        this.schedule           = schedule;
        this.PC                 = 1;
        this.IR                 = 0;
        this.state              = READY_STATE;
        this.missPage           = false;
        this.LRU                = new Vector<>();
        this.suspendResource    = new int[Deadlock.RESOURCE_TYPE_NUM];
        this.userOpenFileTable  = new Vector<>();
        this.openFileCount      = 0;
    }

    /**
     * 进程创建原语
     * @param jcb JCB作业控制块
     */
    public void create(JCB jcb) {
        this.id                     = jcb.getId();
        this.priority               = jcb.getPriority();
        this.instructionNum         = jcb.getInstructionNum();
        this.inTime                 = (short)this.schedule.getManager().getClock().getCurrentTime();
        this.turnTime               = jcb.getInTime();
        this.runTime                = 0;
        this.allocatePageFrameNum   = jcb.getNeedPageNum();

        // 第0页 PCB    指令不能访问
        // 第1页 代码段 长度  1页
        // 第2页 堆栈段 长度  1页
        // 第3页 数据段 长度1-2页
        this.codeSegment            = new CodeSegment(jcb.getInstructionNum(), jcb.getInstructions(), 1,1);
        this.stackSegment           = new StackSegment(2, 1);
        this.dataSegment            = new DataSegment(new byte[InternalMem.PAGE_SIZE * (jcb.getNeedPageNum() - 3)], 3,jcb.getNeedPageNum() - 3);

        synchronized (this.schedule) {
            this.schedule.getManager().getCpu().switchToKernelState();
            // 在外存交换区添加物理块，并在主存页表区添加对应的页表项
            // 1.添加PCB信息页（块）
            this.addPCBPage();
            // 2.添加代码段页（块）
            this.addCodePage();
            // 3.添加堆栈段页（块）
            this.addStackPage();
            // 4.添加数据段页（块）
            this.addDataPages();

            // 将进程加入就绪队列
            this.schedule.getReadyQueue().add(this);
            this.schedule.getAllPCBQueue().add(this);
            this.schedule.getManager().getCpu().switchToUserState();
            this.schedule.getManager().getDashboard().consoleInfo("进程 " + this.id + " 创建");
        }
    }

    /**
     * 进程撤销原语
     */
    public void cancel() {
        synchronized (this.schedule) {
            this.schedule.getManager().getCpu().switchToKernelState();
            // 设置必要的收尾信息
            this.state = FINISH_STATE;
            this.endTime = (short) this.schedule.getManager().getClock().getCurrentTime();
            this.turnTime += this.schedule.getManager().getClock().getCurrentTime();
            this.runTime = (short)(this.endTime - this.inTime);
            // 关闭全部打开文件及目录
            int[] fdList = new int[this.userOpenFileTable.size()];
            for (int i = 0; i < this.userOpenFileTable.size(); i++) {
                fdList[i] = this.userOpenFileTable.get(i).getFd();
            }
            synchronized (this.schedule.getManager().getFileSystem()) {
                this.schedule.getManager().getFileSystem().setUserOperatePCB(this);
                for (int i = 0; i < fdList.length; i++) {
                    this.schedule.getManager().getFileSystem().close(fdList[i]);
                }
            }

            // 回收页表项及相应内存页框、外存块
            this.removeAllPages();
            // 将进程加入完成队列
            this.schedule.getFinishQueue().add(this);
            this.schedule.getManager().getInMem().decreasePCB();
            this.schedule.getLRU().remove(this);
            this.schedule.getManager().getCpu().switchToUserState();
            this.schedule.getManager().getDashboard().consoleSuccess("进程 " + this.id + " 撤销");
        }
    }

    /**
     * 进程唤醒原语
     * @param selectedBlockQueue 指定阻塞队列
     */
    public void wakeUp(Vector<PCB> selectedBlockQueue) {
        synchronized (this.schedule) {
            this.schedule.getManager().getCpu().switchToKernelState();
            selectedBlockQueue.remove(this);
            // 对于资源阻塞队列，需要额外判断
            if (Arrays.asList(this.schedule.getResourceBlockQueues()).contains(selectedBlockQueue)) {
                for (int i = 0; i < this.schedule.getResourceBlockQueues().length; ++i) {
                    // 如果进程还处于其他资源阻塞队列中，则不能真正唤醒
                    if (this.schedule.getResourceBlockQueues()[i].contains(this)) {
                        return;
                    }
                }
            }
            this.state = READY_STATE;
            this.schedule.getReadyQueue().add(this);
            this.schedule.getManager().getCpu().switchToUserState();
            this.schedule.getManager().getDashboard().consoleInfo("进程 " + this.id + " 唤醒");
        }
    }

    /**
     * 进程阻塞原语
     * @param selectedBlockQueue 指定阻塞队列
     */
    public void block(Vector<PCB> selectedBlockQueue) {
        synchronized (this.schedule) {
            this.schedule.getManager().getCpu().switchToKernelState();
            // 保护CPU现场
            this.schedule.getManager().getCpu().protectSpot();
            // 进入阻塞队列
            this.state = BLOCK_STATE;
            this.inBlockQueueTime = this.schedule.getManager().getClock().getCurrentTime();
            selectedBlockQueue.add(this);
            this.schedule.getManager().getCpu().switchToUserState();
            this.schedule.getManager().getDashboard().consoleInfo("进程 " + this.id + " 阻塞");
        }
    }

    /**
     * 进程挂起原语
     * @param selectedQueue 指定队列
     */
    public void suspend(Vector<PCB> selectedQueue) {
        synchronized(this.schedule) {
            this.schedule.getManager().getCpu().switchToKernelState();
            // 调出所占内存页框
            for (int i = 1; i < this.allocatePageFrameNum; ++i) {
                // 获取页表项数据
                int pageItemAddress = this.pageTableBaseAddress + i * InternalMem.PAGE_TABLE_ITEM_SIZE;
                this.schedule.getManager().getAddressLine().setAddress((short) pageItemAddress);
                Page page = this.schedule.getManager().getInMem().readPageItem(this.schedule.getManager().getAddressLine());
                // 如果该页已经调入，则删除对应内存页框
                if (page.getCallFlag() == 1) {
                    // 该页被修改，则写回该页
                    if (page.getModifyFlag() == 1) {
                        this.schedule.getManager().getInMem().readPage(page);
                        this.schedule.getManager().getDeviceManage().useBuffer(page, BufferHead.WRITE);
                        page.setModifyFlag(0);
                    }
                    page.setCallFlag(0);
                    // 释放对应页框
                    this.schedule.getManager().getInMem().releaseUserArea(page.getInternalFrameNo() - InternalMem.USER_AREA_START_PAGE_NO);
                    // 修改对应页表项
                    this.schedule.getManager().getAddressLine().setAddress((short) pageItemAddress);
                    this.schedule.getManager().getInMem().writePageItem(this.schedule.getManager().getAddressLine(), page);
                    this.schedule.getManager().getDashboard().consoleInfo("调出进程 " + this.id + " 逻辑页 "  + page.getLogicPageNo() + " 所占内存页框");
                }

            }
            // 释放已占用的资源
            for (int i = 0; i < this.suspendResource.length; i++) {
                if (this.schedule.getManager().getDeadlock().searchAllocation(this.id, i) != -1) {
                    this.schedule.getManager().getDeadlock().releaseResource(this, i);
                    ++this.suspendResource[i];
                }
                // 尝试重分配资源
                if (this.schedule.getResourceBlockQueues()[i].size() > 0) {
                    int reallocateResult = this.schedule.getManager().getDeadlock().
                            tryReallocateResource(this.schedule.getResourceBlockQueues()[i].get(0), i);
                    // 如果返回唤醒标志，则唤醒阻塞队列队首进程
                    if (reallocateResult == -1) {
                        this.schedule.getResourceBlockQueues()[i].get(0).
                                wakeUp(this.schedule.getResourceBlockQueues()[i]);
                    }
                }
            }
            // 进入挂起队列
            this.state = SUSPEND_STATE;
            this.schedule.getSuspendQueue().add(this);
            selectedQueue.remove(this);
            this.schedule.getLRU().remove(this);
            this.schedule.getManager().getCpu().switchToUserState();
            this.schedule.getManager().getDashboard().consoleInfo("进程 " + this.id + " 挂起");
        }
    }

    /**
     * 是否存在挂起资源
     * @return 是否存在
     */
    public boolean hasSuspendResource() {
        for (int i = 0; i < this.suspendResource.length; ++i) {
            if (this.suspendResource[i] > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 访问页
     * @param logicPageNo 逻辑页号
     */
    public void accessPage(int logicPageNo) {
        for (int i = 0; i < this.LRU.size(); ++i) {
            // 遍历查找，是否之前访问过该页，则重新排列
            if (this.LRU.get(i).intValue() == logicPageNo) {
                this.LRU.remove(i);
                break;
            }
        }
        // 最新访问页放在队尾
        this.LRU.add(new Integer(logicPageNo));
    }

    /**
     * 添加PCB到PCB池
     */
    public Page addPCBToPool() {
        Page page = new Page();
        // 获取PCB池中空闲PCB页的索引，并申请一页
        int index = this.schedule.getManager().getInMem().allocatePool();
        // 设置PCB页信息
        page.setLogicPageNo(0);
        page.setInternalFrameNo(InternalMem.PCB_POOL_START_PAGE_NO + index);
        page.setCallFlag(1);
        page.getData()[0] = (byte) this.id;
        page.getData()[1] = (byte) (this.id >> 8);
        page.getData()[2] = (byte) this.priority;
        page.getData()[3] = (byte) (this.priority >> 8);
        page.getData()[4] = (byte) this.instructionNum;
        page.getData()[5] = (byte) (this.instructionNum >> 8);
        page.getData()[6] = (byte) this.inTime;
        page.getData()[7] = (byte) (this.inTime >> 8);
        // PCB信息页写入主存PCB池
        this.schedule.getManager().getInMem().writePage(page);

        // 标记PCB池位置
        this.indexOfPool = index;

        return page;
    }

    /**
     * 添加PCB的一页到交换区
     * @param page 页信息
     */
    public void addBlockToSwapArea(Page page) {
        // 获取外存交换区中空闲块的索引
        int index = this.schedule.getManager().getExMem().allocateSwapAreaBlock();
        // 设置外存块号
        page.setExternalBlockNo(ExternalMem.SWAP_AREA_START_BLOCK_NO + index);
        // 将物理块写入
        this.schedule.getManager().getDeviceManage().useBuffer(page, BufferHead.WRITE);
    }

    /**
     * 添加PCB的一个页表项到内存页表
     * @param page 页信息
     */
    public void addPageItemToPageTable(Page page) {
        // 如果该页逻辑页号为 0，则获取内存页表中空闲页表项的索引，并设置内存页表基址
        if(page.getLogicPageNo() == 0) {
            int index = this.schedule.getManager().getInMem().allocatePageTable();
            this.pageTableBaseAddress = InternalMem.PAGE_TABLE_START_PAGE_NO * InternalMem.PAGE_SIZE + index * 16 * InternalMem.PAGE_TABLE_ITEM_SIZE;
        }
        int pageItemAddress = this.pageTableBaseAddress + page.getLogicPageNo() * InternalMem.PAGE_TABLE_ITEM_SIZE;
        // 设置两个Flag
        if (page.getLogicPageNo() != 0) {
            page.setCallFlag(0);
        }
        page.setModifyFlag(0);

        // 将页表项写入主存页表
        this.schedule.getManager().getAddressLine().setAddress((short) pageItemAddress);
        this.schedule.getManager().getInMem().writePageItem(this.schedule.getManager().getAddressLine(), page);
        this.schedule.getManager().getDashboard().consoleLog("进程" + this.getId() + " 分配页表项 -> " +
                "页表项地址：" + pageItemAddress +
                " 逻辑页号：" + page.getLogicPageNo() +
                " 主存框号：" + page.getInternalFrameNo() +
                " 外存块号：" + page.getExternalBlockNo() +
                " 装入位：" + page.getCallFlag() +
                " 修改位：" + page.getModifyFlag());
    }

    /**
     * 添加PCB信息页到系统中
     */
    public void addPCBPage() {
        Page page = this.addPCBToPool();
        this.addBlockToSwapArea(page);
        this.addPageItemToPageTable(page);
    }

    /**
     * 添加代码段页到系统中
     */
    public void addCodePage() {
        Page page = this.codeSegment.initCodePage();
        this.addBlockToSwapArea(page);
        this.addPageItemToPageTable(page);
    }

    /**
     * 添加堆栈段页到系统中
     */
    public void addStackPage() {
        Page page = this.stackSegment.initSatckPage();
        this.addBlockToSwapArea(page);
        this.addPageItemToPageTable(page);
    }

    /**
     * 添加数据段页到系统中
     */
    public void addDataPages() {
        Page[] pages = this.dataSegment.initDataPages();
        for (int i =0; i < pages.length; ++i) {
            this.addBlockToSwapArea(pages[i]);
            this.addPageItemToPageTable(pages[i]);
        }
    }

    /**
     * 撤销进程占用的所有页表项、内存页框、外存块
     */
    public void removeAllPages() {
        // 遍历每一个页表项，进行操作
        for (int i = 0; i < this.allocatePageFrameNum; ++i) {
            // 获取页表项数据
            this.schedule.getManager().getAddressLine().setAddress((short)(this.pageTableBaseAddress + i * InternalMem.PAGE_TABLE_ITEM_SIZE));
            Page page = this.schedule.getManager().getInMem().readPageItem(this.schedule.getManager().getAddressLine());
            // 如果该页已经调入，则删除对应页框
            if (page.getCallFlag() == 1) {
                if (i == 0) {
                    this.schedule.getManager().getInMem().releasePool(page.getInternalFrameNo() - InternalMem.PCB_POOL_START_PAGE_NO);
                } else {
                    this.schedule.getManager().getInMem().releaseUserArea(page.getInternalFrameNo() - InternalMem.USER_AREA_START_PAGE_NO);
                }
            }
            // 删除对应外存块
            this.schedule.getManager().getExMem().releaseSwapAreaBlock(page.getExternalBlockNo() - ExternalMem.SWAP_AREA_START_BLOCK_NO);
            // 删除页表项
            this.schedule.getManager().getDashboard().consoleLog("释放进程 " + this.id +
                    " 逻辑页 "  + page.getLogicPageNo() +
                    " 所占内存框号 " + page.getInternalFrameNo() +
                    " 外存块号 " + page.getExternalBlockNo());
        }
        // 释放该进程页表区
        this.schedule.getManager().getInMem().releasePageTable(this.pageTableBaseAddress / 64);
    }

    /**
     * 添加用户打开文件表表项
     * @param fp 系统打开文件表指针
     * @return 用户打开文件表长度
     */
    public synchronized int addOpenFileItem(SystemFileItem fp) {
        this.userOpenFileTable.add(new UserFileItem(this.openFileCount, fp));
        return this.openFileCount++;
    }
    /**
     * 添加用户打开文件表表项
     * @param fd 文件描述符
     */
    public synchronized void removeOpenFileItem(int fd) {
        Iterator<UserFileItem> iterator = this.userOpenFileTable.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getFd() == fd) {
                iterator.remove();
                return;
            }
        }
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
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

    public short getInstructionNum() {
        return instructionNum;
    }

    public void setInstructionNum(short instructionNum) {
        this.instructionNum = instructionNum;
    }

    public short getPC() {
        return PC;
    }

    public void setPC(short PC) {
        this.PC = PC;
    }

    public short getIR() {
        return IR;
    }

    public void setIR(short IR) {
        this.IR = IR;
    }

    public short getState() {
        return state;
    }

    public void setState(short state) {
        this.state = state;
    }

    public short getInTime() {
        return inTime;
    }

    public void setInTime(short inTime) {
        this.inTime = inTime;
    }

    public short getEndTime() {
        return endTime;
    }

    public void setEndTime(short endTime) {
        this.endTime = endTime;
    }

    public short getTurnTime() {
        return turnTime;
    }

    public void setTurnTime(short turnTime) {
        this.turnTime = turnTime;
    }

    public short getRunTime() {
        return runTime;
    }

    public void setRunTime(short runTime) {
        this.runTime = runTime;
    }

    public int getInBlockQueueTime() {
        return inBlockQueueTime;
    }

    public void setInBlockQueueTime(int inBlockQueueTime) {
        this.inBlockQueueTime = inBlockQueueTime;
    }

    public int getPageTableBaseAddress() {
        return pageTableBaseAddress;
    }

    public void setPageTableBaseAddress(int pageTableBaseAddress) {
        this.pageTableBaseAddress = pageTableBaseAddress;
    }

    public short getAllocatePageFrameNum() {
        return allocatePageFrameNum;
    }

    public void setAllocatePageFrameNum(short allocatePageFrameNum) {
        this.allocatePageFrameNum = allocatePageFrameNum;
    }

    public int getIndexOfPool() {
        return indexOfPool;
    }

    public void setIndexOfPool(int indexOfPool) {
        this.indexOfPool = indexOfPool;
    }

    public boolean isMissPage() {
        return missPage;
    }

    public void setMissPage(boolean missPage) {
        this.missPage = missPage;
    }

    public Vector<Integer> getLRU() {
        return LRU;
    }

    public void setLRU(Vector<Integer> LRU) {
        this.LRU = LRU;
    }

    public CodeSegment getCodeSegment() {
        return codeSegment;
    }

    public void setCodeSegment(CodeSegment codeSegment) {
        this.codeSegment = codeSegment;
    }

    public DataSegment getDataSegment() {
        return dataSegment;
    }

    public void setDataSegment(DataSegment dataSegment) {
        this.dataSegment = dataSegment;
    }

    public StackSegment getStackSegment() {
        return stackSegment;
    }

    public void setStackSegment(StackSegment stackSegment) {
        this.stackSegment = stackSegment;
    }

    public int[] getSuspendResource() {
        return suspendResource;
    }

    public void setSuspendResource(int[] suspendResource) {
        this.suspendResource = suspendResource;
    }

    public Vector<UserFileItem> getUserOpenFileTable() {
        return userOpenFileTable;
    }

    public void setUserOpenFileTable(Vector<UserFileItem> userOpenFileTable) {
        this.userOpenFileTable = userOpenFileTable;
    }

    public int getOpenFileCount() {
        return openFileCount;
    }

    public void setOpenFileCount(int openFileCount) {
        this.openFileCount = openFileCount;
    }
}
