package hardware;

import kernel.Page;
import os.Manager;

import java.util.Vector;

/**
 * MMU 内存管理单元，用于请求访页和虚拟存储技术
 *
 * 负责访问内存时，虚拟地址到物理地址的转化等操作
 *
 * @author ZJC
 */
public class MMU {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 快表容量，存储的快表项个数
     */
    public static final int TLB_SIZE = 8;
    /**
     * 快表结构，每个快表项存储逻辑页号、内存页框号
     */
    private Vector<int[]> TLB;
    /**
     * 用于替换快表项的LRU算法队列，最近访问的页面移到队尾，替换时选择队首页面
     */
    private Vector<Integer> LRU;

    public MMU(Manager manager) {
        this.manager = manager;
        this.TLB = new Vector<>();
        this.LRU = new Vector<>();

        this.manager.getDashboard().consoleSuccess("MMU初始化完成");
    }

    /**
     * 初始化TLB
     */
    public void initTLB() {
        this.TLB.clear();
        this.LRU.clear();
    }

    /**
     * 检索TLB
     * @param logicPageNo 逻辑页号
     * @return TLB索引
     */
    public int searchTLB(int logicPageNo) {
        for (int i = 0; i < this.TLB.size(); ++i) {
            if (this.TLB.get(i)[0] == logicPageNo) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 访问一个快表项，为 LRU 算法提供依据
     * @param logicPageNo 快表项的逻辑页号
     */
    public void visitTLB(int logicPageNo) {
        for (int i = 0; i < this.LRU.size(); ++i) {
            // 遍历查找，是否之前访问过该页，则重新排列
            if (this.LRU.get(i).intValue() == logicPageNo) {
                this.LRU.remove(i);
                break;
            }
        }
        // 最新访问页放在队尾
        this.LRU.add(new Integer(logicPageNo));
//        this.manager.getDashboard().consoleLog("逻辑页 " + logicPageNo + " 移动到TLB优先级队尾");
    }

    /**
     * 更新TLB
     * @param logicPageNo 逻辑页号
     * @param internalFrameNo  内存页框号
     */
    public void updateTLB(int logicPageNo, int internalFrameNo) {
        // 获取该页在快表的索引
        int index = this.searchTLB(logicPageNo);

        // 如果该页在快表中，则直接进行一次访问
        if (index >= 0) {
            visitTLB(logicPageNo);
            return;
        }
        // 如果该页不在快表中，则需要添加快表项
        if(this.TLB.size() >= TLB_SIZE) {
            // 快表已满，则选择换出一项
            int removeLogicPageNo = this.LRU.get(0).intValue();
            this.removeTLB(removeLogicPageNo);
        }
        // 添加快表项
        this.TLB.add(new int[]{logicPageNo, internalFrameNo});
        this.manager.getDashboard().consoleLog("TLB添加表项 ->" +
                " 逻辑页号：" + logicPageNo +
                " 内存框号：" + internalFrameNo);
        // 对该页进行一次访问
        this.visitTLB(logicPageNo);
    }

    /**
     * 删除一个快表项，适用于 内存页框发生替换时，去除无效快表项
     * @param logicPageNo 快表项的逻辑页号
     */
    public void removeTLB(int logicPageNo) {
        for (int i = 0; i < this.TLB.size(); ++i) {
            if (this.TLB.get(i)[0] == logicPageNo) {
                this.TLB.remove(i);
                break;
            }
        }
        for (int i = 0; i < this.LRU.size(); ++i) {
            if (this.LRU.get(i).intValue() == logicPageNo) {
                this.LRU.remove(i);
                break;
            }
        }
    }

    /**
     * 解析逻辑地址
     * @param logicAddress 逻辑地址
     * @param pageTableBaseAddress 页表基址
     * @return 内存物理地址
     */
    public short resolveLogicAddress(short logicAddress, int pageTableBaseAddress) {
        // 将16位逻辑地址拆分成 7位逻辑页号 + 9位页内偏移
        int logicPageNo = (logicAddress >> 9) & 0x007F;
        int offset      = logicAddress & 0x01FF;
        // 依次检索快表和页表
        // 快表命中，则直接返回物理地址
        int index = this.searchTLB(logicPageNo);
        if (index >= 0) {
            int frameNo = this.TLB.get(index)[1];
            this.updateTLB(logicPageNo, frameNo);
            this.manager.getDashboard().consoleInfo("TLB命中 -> " +
                    " 逻辑页号：" + logicPageNo +
                    " 内存框号：" + frameNo);
            return (short) (frameNo * InternalMem.PAGE_SIZE + offset);
        }

        //页表命中，则返回物理地址
        this.manager.getAddressLine().setAddress((short)(pageTableBaseAddress + logicPageNo * InternalMem.PAGE_TABLE_ITEM_SIZE));
        Page page = this.manager.getInMem().readPageItem(this.manager.getAddressLine());
        if (page.getCallFlag() == 1) {
            int frameNo = page.getInternalFrameNo();
            this.updateTLB(logicPageNo, frameNo);
            this.manager.getDashboard().consoleLog("页表命中 -> " +
                    " 逻辑页号：" + logicPageNo +
                    " 内存框号：" + frameNo);
            return (short) (frameNo * InternalMem.PAGE_SIZE + offset);
        }

        // 快表、页表都没命中，则返回 -1
        return -1;
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public Vector<int[]> getTLB() {
        return TLB;
    }

    public void setTLB(Vector<int[]> TLB) {
        this.TLB = TLB;
    }

    public Vector<Integer> getLRU() {
        return LRU;
    }

    public void setLRU(Vector<Integer> LRU) {
        this.LRU = LRU;
    }
}
