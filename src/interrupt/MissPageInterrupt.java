package interrupt;

import hardware.InternalMem;
import kernel.BufferHead;
import kernel.PCB;
import kernel.Page;
import os.Manager;

import java.nio.Buffer;

/**
 * 缺页中断
 *
 * 进行相关缺页时的操作
 */
public class MissPageInterrupt extends Thread {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 请求缺页中断的进程 PCB
     */
    private PCB pcb;
    /**
     * 缺页的逻辑页号
     */
    private int missPageLogicNo;

    public MissPageInterrupt(Manager manager, PCB pcb, int missPageLogicNo) {
        super("MissPage");
        this.manager = manager;
        this.pcb = pcb;
        this.missPageLogicNo = missPageLogicNo;
    }

    @Override
    public void run() {
        // 记录进入时间
        int startTime = this.manager.getClock().getCurrentTime();

        // 读取对应页表项信息
        int pageItemAddress = this.pcb.getPageTableBaseAddress() + this.missPageLogicNo * InternalMem.PAGE_TABLE_ITEM_SIZE;
        this.manager.getAddressLine().setAddress((short) pageItemAddress);
        Page missPage = this.manager.getInMem().readPageItem(this.manager.getAddressLine());

        // 判断内存是否已满？内存已满，则选择换出一页；内存未满，则直接添加一页
        if (this.manager.getInMem().getFreeFrameNumOfUserArea() <= 0) {
            // 内存已满，则选择换出一页
            int swapLogicPageNo = this.pcb.getLRU().get(0).intValue();
            int swapPageItemAddress = this.pcb.getPageTableBaseAddress() + swapLogicPageNo * InternalMem.PAGE_TABLE_ITEM_SIZE;
            this.manager.getAddressLine().setAddress((short) swapPageItemAddress);
            Page swapPage = this.manager.getInMem().readPageItem(this.manager.getAddressLine());
            // 将换出页面对应的页表项引用位设为0
            swapPage.setCallFlag(0);
            if (swapPage.getModifyFlag() == 1) {
                // 如果换出页被修改，则同步修改到外存
                this.manager.getInMem().readPage(swapPage);
                this.manager.getDeviceManage().useBuffer(missPage, BufferHead.WRITE);
                swapPage.setModifyFlag(0);
            }
            this.manager.getAddressLine().setAddress((short) swapPageItemAddress);
            this.manager.getInMem().writePageItem(this.manager.getAddressLine(), swapPage);
            // 换出页的页框提供给换入页
            this.manager.getDashboard().consoleInfo("页面换出 -> 逻辑页号：" + swapPage.getLogicPageNo() + "主存框号：" + swapPage.getInternalFrameNo());
        }
        // 申请页框
        int frameIndex = this.manager.getInMem().allocateUserArea();
        // 使用缓冲区从外存中获取缺页
        this.manager.getDeviceManage().useBuffer(missPage, BufferHead.READ);
        while (missPage.getData() == null) {}
        // 设置页框号和调入位
        missPage.setInternalFrameNo(frameIndex + InternalMem.USER_AREA_START_PAGE_NO);
        missPage.setCallFlag(1);
        // 将缺页写入内存
        this.manager.getInMem().writePage(missPage);
        // 刷新GUI
        this.manager.getDashboard().refreshFrame(missPage.getInternalFrameNo(), 1);
        // 修改页表项
        this.manager.getAddressLine().setAddress((short) pageItemAddress);
        this.manager.getInMem().writePageItem(this.manager.getAddressLine(), missPage);

        this.manager.getDashboard().consoleInfo("进程 " + this.pcb.getId() + " 缺页中断 -> " +
                " 逻辑页号：" + missPage.getLogicPageNo() +
                " 分配主存框号：" + missPage.getInternalFrameNo());

        // 缺页中断需要 1 个时钟周期
        while (this.manager.getClock().getCurrentTime() - startTime < 1) { }
        // 进程唤醒
        this.pcb.wakeUp(this.manager.getSchedule().getBlockQueue());
    }
}
