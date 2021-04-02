/*
 * Created by JFormDesigner on Mon Mar 01 01:02:35 CST 2021
 */

package gui;

import hardware.CPU;
import hardware.ExternalMem;
import hardware.InternalMem;
import interrupt.InterruptVector;
import kernel.FileSystem;
import kernel.JCB;
import kernel.PCB;
import kernel.Page;
import os.Manager;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import static kernel.FileSystem.INODE_SIZE;

/**
 * 操作控制仪表盘，用以实现图像化界面
 *
 * @author ZJC
 */
public class Dashboard extends JFrame {
    private Manager manager;

    public InternalMemDisplay internalMemDisplay;
    public ExternalMemDisplay externalMemDisplay;
    public FileSystemCommander fileSystemCommander;
    public ProcessDetail processDetail;
    public FileExplorer fileExplorer;

    public JLabel[] frames;
    public JLabel[] blocks;
    public StyledDocument doc;
    public JTable reserveTable;
    public DefaultTableModel reserveTableInfo;
    public JTable readyTable;
    public DefaultTableModel readyTableInfo;
    public JTable blockTable;
    public DefaultTableModel blockTableInfo;
    public JTable suspendTable;
    public DefaultTableModel suspendTableInfo;
    public JTable finishTable;
    public DefaultTableModel finishTableInfo;
    public JTable resourceTableA;
    public DefaultTableModel resourceTableInfoA;
    public JTable resourceTableB;
    public DefaultTableModel resourceTableInfoB;
    public JTable resourceTableC;
    public DefaultTableModel resourceTableInfoC;
    public JTable pageTable;
    public DefaultTableModel pageTableInfo;
    public JTable TLBTable;
    public DefaultTableModel TLBTableInfo;

    private SimpleAttributeSet logStyle;
    private SimpleAttributeSet infoStyle;
    private SimpleAttributeSet errorStyle;
    private SimpleAttributeSet successStyle;

    public Dashboard(Manager manager) {
        this.manager = manager;
        this.internalMemDisplay     = new InternalMemDisplay(this);
        this.externalMemDisplay     = new ExternalMemDisplay(this);
        this.fileSystemCommander    = new FileSystemCommander(this);
        this.processDetail          = new ProcessDetail(this);
        this.fileExplorer           = new FileExplorer(this);
        // 初始化界面元素
        this.initComponents();
        // 初始化界面数据格式
        this.initDataStructure();
        // 显示界面
        this.setVisible(true);
    }

    /**
     * 控制台普通日志输出
     * @param content 输出内容
     */
    public synchronized void consoleLog(String content) {
        System.out.println(content);
        try {
            this.doc.insertString(this.doc.getLength(), content + "\n", logStyle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 控制台关键日志输出
     * @param content 输出内容
     */
    public synchronized void consoleInfo(String content) {
        System.out.println(content);
        try {
            this.doc.insertString(this.doc.getLength(), content + "\n", infoStyle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 控制台错误日志输出
     * @param content 输出内容
     */
    public synchronized void consoleError(String content) {
        System.out.println(content);
        try {
            this.doc.insertString(this.doc.getLength(), content + "\n", errorStyle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 控制台成功日志输出
     * @param content 输出内容
     */
    public synchronized void consoleSuccess(String content) {
        System.out.println(content);
        try {
            this.doc.insertString(this.doc.getLength(), content + "\n", successStyle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 激活所有按钮
     */
    public synchronized void enableAllButton() {
        this.startButton.setEnabled(true);
        this.addJobButton.setEnabled(true);
        this.inMemButton.setEnabled(true);
        this.exMemButton.setEnabled(true);
        this.fileSystemButton.setEnabled(true);
    }

    /**
     * 刷新时间
     * @param time 时间
     */
    public synchronized void refreshTime(int time) {
        this.time.setText("" + time);
    }

    /**
     * 刷新CPU信息
     */
    public synchronized void refreshCPU() {
        this.cpuState.setText(this.manager.getCpu().getPSW() == CPU.USER_STATE ? "用户态" : "内核态");
        this.PC.setText("" + this.manager.getCpu().getPC());
        this.IR.setText("" + this.manager.getCpu().getIR());
    }

    /**
     * 刷新运行进程信息
     */
    public synchronized void refreshRunningProcess() {
        PCB running = this.manager.getCpu().getRunningPCB();
        this.runningPCBId.setText(running == null ? " " : "" + running.getId());
        this.runningPCBPriority.setText(running == null ? " " : "" + running.getPriority());
        this.runningPCBInstructionNum.setText(running == null ? " " : "" + running.getInstructionNum());
        this.runningPCBNeedPageNum.setText(running == null ? " " : "" + running.getAllocatePageFrameNum());
        this.runningPCBPC.setText(running == null ? " " : "" + running.getPC());
        this.runningPCBIR.setText(running == null ? " " : "" + running.getIR());
        this.runningPCBPageTableBaseAddress.setText(running == null ? " " : "" + running.getPageTableBaseAddress());
        this.runningPCBTimeSlice.setText(running == null ? " " : "" + this.manager.getCpu().getTimeSlice());
    }

    /**
     * 刷新页表
     */
    public synchronized void refreshPageTable() {
        while (this.pageTableInfo.getRowCount() > 0) {
            this.pageTableInfo.removeRow(0);
        }
        if (this.manager.getCpu().getRunningPCB() == null) {
            return;
        }
        int pageTableBaseAddress = this.manager.getCpu().getRunningPCB().getPageTableBaseAddress();
        int allocatePageFrameNum = this.manager.getCpu().getRunningPCB().getAllocatePageFrameNum();
        for (int i = 0; i < allocatePageFrameNum; i ++) {
            this.manager.getAddressLine().setAddress((short)(pageTableBaseAddress + i * InternalMem.PAGE_TABLE_ITEM_SIZE));
            Page page = this.manager.getInMem().readPageItem(this.manager.getAddressLine());
            this.pageTableInfo.addRow(new String[]{
                    Integer.toString(page.getLogicPageNo()),
                    Integer.toString(page.getInternalFrameNo()),
                    Integer.toString(page.getExternalBlockNo()),
                    Integer.toString(page.getCallFlag()),
                    Integer.toString(page.getModifyFlag())});
        }
    }

    /**
     * 刷新快表TLB
     */
    public synchronized void refreshTLB() {
        while (this.TLBTableInfo.getRowCount() > 0) {
            this.TLBTableInfo.removeRow(0);
        }
        for (int i = 0; i < this.manager.getMmu().getTLB().size(); i ++) {
            int[] TLBItem = this.manager.getMmu().getTLB().get(i);
            this.TLBTableInfo.addRow(new String[]{
                    Integer.toString(TLBItem[0]),
                    Integer.toString(TLBItem[1])});
        }
    }

    /**
     * 刷新页框占用情况
     * @param frameNo 页框号
     * @param mode 占用情况
     */
    public synchronized void refreshFrame(int frameNo, int mode) {
        this.frames[frameNo].setText(mode == 1 ? "■" : "□");
    }

    /**
     * 刷新交换区占用情况
     * @param blockNo 交换区块号
     * @param mode 占用情况
     */
    public synchronized void refreshBlock(int blockNo, int mode) {
        this.blocks[blockNo].setText(mode == 1 ? "■" : "□");
    }

    /**
     * 刷新队列信息
     */
    public synchronized void refreshQueues() {
        // 刷新后备队列
        while (this.reserveTableInfo.getRowCount() > 0) {
            this.reserveTableInfo.removeRow(0);
        }
        for (int i = 0; i < this.manager.getSchedule().getReserveQueue().size(); ++i) {
            JCB temp = this.manager.getSchedule().getReserveQueue().get(i);
            this.reserveTableInfo.addRow(new String[]{
                    Integer.toString(temp.getId()),
                    Integer.toString(temp.getPriority()),
                    Integer.toString(temp.getInTime()),
                    Integer.toString(temp.getInstructionNum()),
                    Integer.toString(temp.getNeedPageNum())});
        }
        // 刷新就绪队列
        while (this.readyTableInfo.getRowCount() > 0) {
            this.readyTableInfo.removeRow(0);
        }
        for (int i = 0; i < this.manager.getSchedule().getReadyQueue().size(); ++i) {
            PCB temp = this.manager.getSchedule().getReadyQueue().get(i);
            this.readyTableInfo.addRow(new String[]{
                    Integer.toString(temp.getId()),
                    Integer.toString(temp.getPriority()),
                    Integer.toString(temp.getInstructionNum()),
                    Integer.toString(temp.getPC()),
                    Integer.toString(temp.getIR())});
        }
        // 刷新阻塞队列
        while (this.blockTableInfo.getRowCount() > 0) {
            this.blockTableInfo.removeRow(0);
        }
        for (int i = 0; i < this.manager.getSchedule().getBlockQueue().size(); ++i) {
            PCB temp = this.manager.getSchedule().getBlockQueue().get(i);
            this.blockTableInfo.addRow(new String[]{
                    Integer.toString(temp.getId())});
        }
        // 刷新资源队列
        while (this.resourceTableInfoA.getRowCount() > 0) {
            this.resourceTableInfoA.removeRow(0);
        }
        for (int i = 0; i < this.manager.getSchedule().getResourceBlockQueues()[0].size(); ++i) {
            PCB temp = this.manager.getSchedule().getResourceBlockQueues()[0].get(i);
            this.resourceTableInfoA.addRow(new String[]{
                    Integer.toString(temp.getId())});
        }
        while (this.resourceTableInfoB.getRowCount() > 0) {
            this.resourceTableInfoB.removeRow(0);
        }
        for (int i = 0; i < this.manager.getSchedule().getResourceBlockQueues()[1].size(); ++i) {
            PCB temp = this.manager.getSchedule().getResourceBlockQueues()[1].get(i);
            this.resourceTableInfoB.addRow(new String[]{
                    Integer.toString(temp.getId())});
        }
        while (this.resourceTableInfoC.getRowCount() > 0) {
            this.resourceTableInfoC.removeRow(0);
        }
        for (int i = 0; i < this.manager.getSchedule().getResourceBlockQueues()[2].size(); ++i) {
            PCB temp = this.manager.getSchedule().getResourceBlockQueues()[2].get(i);
            this.resourceTableInfoC.addRow(new String[]{
                    Integer.toString(temp.getId())});
        }
        // 刷新挂起队列
        while (this.suspendTableInfo.getRowCount() > 0) {
            this.suspendTableInfo.removeRow(0);
        }
        for (int i = 0; i < this.manager.getSchedule().getSuspendQueue().size(); ++i) {
            PCB temp = this.manager.getSchedule().getSuspendQueue().get(i);
            this.suspendTableInfo.addRow(new String[]{
                    Integer.toString(temp.getId())});
        }
        // 刷新完成队列
        while (this.finishTableInfo.getRowCount() > 0) {
            this.finishTableInfo.removeRow(0);
        }
        for (int i = 0; i < this.manager.getSchedule().getFinishQueue().size(); ++i) {
            PCB temp = this.manager.getSchedule().getFinishQueue().get(i);
            this.finishTableInfo.addRow(new String[]{
                    Integer.toString(temp.getId())});
        }
    }

    /**
     * 初始化页框位示图
     */
    public void initFrame() {
        this.frames = new JLabel[InternalMem.PAGE_NUM];
        int x = 1235, y = 45;
        for (int i = 0; i < this.frames.length; ++i) {
            frames[i] = new JLabel();
            frames[i].setText(i < InternalMem.PAGE_TABLE_PAGE_NUM ? "■" : "□");
            frames[i].setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 30));
            frames[i].setHorizontalAlignment(SwingConstants.CENTER);
            frames[i].setBounds(x, y, 25, 25);
            panel.add(frames[i]);

            x += 40;
            if (i % 8 == 7) {
                x -= 40 * 8;
                y += 25;
            }
        }
    }

    /**
     * 初始化交换区位示图
     */
    public void initBlock() {
        this.blocks = new JLabel[ExternalMem.SWAP_AREA_BLOCK_NUM];
        int x = 95, y = 0;
        for (int i = 0; i < this.blocks.length; ++i) {
            if (i % 8 == 0) {
                JLabel inodeLable = new JLabel();
                String startNo = "" + i;
                String endNo = "" + (i + 7);
                while (startNo.length() < 3) {
                    startNo = "0" + startNo;
                }
                while (endNo.length() < 3) {
                    endNo = "0" + endNo;
                }
                inodeLable.setText(startNo + " - " + endNo);
                inodeLable.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
                inodeLable.setBounds(10, y + 5, 80, 25);
                this.swapAreaPanel.add(inodeLable);
            }
            blocks[i] = new JLabel();
            blocks[i].setText("□");
            blocks[i].setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 30));
            blocks[i].setHorizontalAlignment(SwingConstants.CENTER);
            blocks[i].setBounds(x, y, 25, 25);
            this.swapAreaPanel.add(blocks[i]);

            x += 40;
            if (i % 8 == 7) {
                x -= 40 * 8;
                y += 25;
            }
        }
        this.swapAreaPanel.setPreferredSize(new Dimension(420, 805));
        this.swapAreaAllocationScrollPane.setViewportView(this.swapAreaPanel);
    }

    /**
     * 初始化控制台
     */
    public void initConsole() {
        // 绘制控制台富文本
        doc = this.console.getStyledDocument();
        // 保持滚动条在底端
        DefaultCaret caret = (DefaultCaret) this.console.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        // 设置控制台输出样式
        this.logStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(this.logStyle, "Microsoft YaHei UI");
        StyleConstants.setFontSize(this.logStyle, 14);
        StyleConstants.setForeground(this.logStyle, Color.DARK_GRAY);

        this.infoStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(this.infoStyle, "Microsoft YaHei UI");
        StyleConstants.setFontSize(this.infoStyle, 14);
        StyleConstants.setForeground(this.infoStyle, Color.BLUE);

        this.errorStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(this.errorStyle, "Microsoft YaHei UI");
        StyleConstants.setFontSize(this.errorStyle, 14);
        StyleConstants.setForeground(this.errorStyle, Color.RED);

        this.successStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(this.successStyle, "Microsoft YaHei UI");
        StyleConstants.setFontSize(this.successStyle, 14);
        StyleConstants.setForeground(this.successStyle, Color.GREEN);
    }

    /**
     * 初始化所有表格
     */
    public void initTable() {
        // 后备队列
        String[] reserveTableHeader = new String[]{"作业ID", "优先级", "请求时间", "指令数", "所需页数"};
        this.reserveTableInfo = new DefaultTableModel(new String[][]{}, reserveTableHeader) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.reserveTable = new JTable(this.reserveTableInfo);
        this.reserveTable.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.reserveTable.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.reserveQueueScrollPane.setViewportView(this.reserveTable);
        // 就绪队列
        String[] readyTableHeader = new String[]{"进程ID", "优先级", "指令数", "PC", "IR"};
        this.readyTableInfo = new DefaultTableModel(new String[][]{}, readyTableHeader) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.readyTable = new JTable(this.readyTableInfo);
        this.readyTable.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.readyTable.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.readyQueueScrollPane.setViewportView(this.readyTable);
        this.readyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击显示进程详情
                if (e.getClickCount() == 2) {
                    // 强制暂停
                    if (!manager.getClock().isPause()) {
                        startButton.doClick();
                    }
                    try {
                        // 获取选中的进程id
                        int id = Integer.parseInt(readyTableInfo.getValueAt(readyTable.getSelectedRow(), 0).toString());
                        Iterator<PCB> iterator = manager.getSchedule().getAllPCBQueue().iterator();
                        while (iterator.hasNext()) {
                            PCB pcb = iterator.next();
                            // 找到对应进程并展示
                            if (pcb.getId() == id) {
                                processDetail.setPcb(pcb);
                                processDetail.refreshData();
                                processDetail.setVisible(true);
                                break;
                            }
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        });
        // 阻塞队列
        String[] blockTableHeader = new String[]{"进程ID"};
        this.blockTableInfo = new DefaultTableModel(new String[][]{}, blockTableHeader) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.blockTable = new JTable(this.blockTableInfo);
        this.blockTable.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.blockTable.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.blockQueueScrollPane.setViewportView(this.blockTable);
        this.blockTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击显示进程详情
                if (e.getClickCount() == 2) {
                    // 强制暂停
                    if (!manager.getClock().isPause()) {
                        startButton.doClick();
                    }
                    try {
                        // 获取选中的进程id
                        int id = Integer.parseInt(blockTableInfo.getValueAt(blockTable.getSelectedRow(), 0).toString());
                        Iterator<PCB> iterator = manager.getSchedule().getAllPCBQueue().iterator();
                        while (iterator.hasNext()) {
                            PCB pcb = iterator.next();
                            // 找到对应进程并展示
                            if (pcb.getId() == id) {
                                processDetail.setPcb(pcb);
                                processDetail.refreshData();
                                processDetail.setVisible(true);
                                break;
                            }
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        });
        // 资源A阻塞队列
        String[] resourceTableHeaderA = new String[]{"进程ID"};
        this.resourceTableInfoA = new DefaultTableModel(new String[][]{}, resourceTableHeaderA) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.resourceTableA = new JTable(this.resourceTableInfoA);
        this.resourceTableA.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.resourceTableA.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.resourceblockQueueScrollPaneA.setViewportView(this.resourceTableA);
        this.resourceTableA.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击显示进程详情
                if (e.getClickCount() == 2) {
                    // 强制暂停
                    if (!manager.getClock().isPause()) {
                        startButton.doClick();
                    }
                    try {
                        // 获取选中的进程id
                        int id = Integer.parseInt(resourceTableInfoA.getValueAt(resourceTableA.getSelectedRow(), 0).toString());
                        Iterator<PCB> iterator = manager.getSchedule().getAllPCBQueue().iterator();
                        while (iterator.hasNext()) {
                            PCB pcb = iterator.next();
                            // 找到对应进程并展示
                            if (pcb.getId() == id) {
                                processDetail.setPcb(pcb);
                                processDetail.refreshData();
                                processDetail.setVisible(true);
                                break;
                            }
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        });

        // 资源B阻塞队列
        String[] resourceTableHeaderB = new String[]{"进程ID"};
        this.resourceTableInfoB = new DefaultTableModel(new String[][]{}, resourceTableHeaderB) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.resourceTableB = new JTable(this.resourceTableInfoB);
        this.resourceTableB.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.resourceTableB.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.resourceblockQueueScrollPaneB.setViewportView(this.resourceTableB);
        this.resourceTableB.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击显示进程详情
                if (e.getClickCount() == 2) {
                    // 强制暂停
                    if (!manager.getClock().isPause()) {
                        startButton.doClick();
                    }
                    try {
                        // 获取选中的进程id
                        int id = Integer.parseInt(resourceTableInfoB.getValueAt(resourceTableB.getSelectedRow(), 0).toString());
                        Iterator<PCB> iterator = manager.getSchedule().getAllPCBQueue().iterator();
                        while (iterator.hasNext()) {
                            PCB pcb = iterator.next();
                            // 找到对应进程并展示
                            if (pcb.getId() == id) {
                                processDetail.setPcb(pcb);
                                processDetail.refreshData();
                                processDetail.setVisible(true);
                                break;
                            }
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        });
        // 资源C阻塞队列
        String[] resourceTableHeaderC = new String[]{"进程ID"};
        this.resourceTableInfoC = new DefaultTableModel(new String[][]{}, resourceTableHeaderC) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.resourceTableC = new JTable(this.resourceTableInfoC);
        this.resourceTableC.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.resourceTableC.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.resourceblockQueueScrollPaneC.setViewportView(this.resourceTableC);
        this.resourceTableC.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击显示进程详情
                if (e.getClickCount() == 2) {
                    // 强制暂停
                    if (!manager.getClock().isPause()) {
                        startButton.doClick();
                    }
                    try {
                        // 获取选中的进程id
                        int id = Integer.parseInt(resourceTableInfoC.getValueAt(resourceTableC.getSelectedRow(), 0).toString());
                        Iterator<PCB> iterator = manager.getSchedule().getAllPCBQueue().iterator();
                        while (iterator.hasNext()) {
                            PCB pcb = iterator.next();
                            // 找到对应进程并展示
                            if (pcb.getId() == id) {
                                processDetail.setPcb(pcb);
                                processDetail.refreshData();
                                processDetail.setVisible(true);
                                break;
                            }
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        });
        // 挂起队列
        String[] suspendTableHeader = new String[]{"进程ID"};
        this.suspendTableInfo = new DefaultTableModel(new String[][]{}, suspendTableHeader) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.suspendTable = new JTable(this.suspendTableInfo);
        this.suspendTable.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.suspendTable.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.suspendQueueScrollPane.setViewportView(this.suspendTable);
        this.suspendTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击显示进程详情
                if (e.getClickCount() == 2) {
                    // 强制暂停
                    if (!manager.getClock().isPause()) {
                        startButton.doClick();
                    }
                    try {
                        // 获取选中的进程id
                        int id = Integer.parseInt(suspendTableInfo.getValueAt(suspendTable.getSelectedRow(), 0).toString());
                        Iterator<PCB> iterator = manager.getSchedule().getAllPCBQueue().iterator();
                        while (iterator.hasNext()) {
                            PCB pcb = iterator.next();
                            // 找到对应进程并展示
                            if (pcb.getId() == id) {
                                processDetail.setPcb(pcb);
                                processDetail.refreshData();
                                processDetail.setVisible(true);
                                break;
                            }
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        });
        // 完成队列
        String[] finishTableHeader = new String[]{"进程ID"};
        this.finishTableInfo = new DefaultTableModel(new String[][]{}, finishTableHeader) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.finishTable = new JTable(this.finishTableInfo);
        this.finishTable.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.finishTable.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.finishQueueScrollPane.setViewportView(this.finishTable);
        this.finishTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击显示进程详情
                if (e.getClickCount() == 2) {
                    // 强制暂停
                    if (!manager.getClock().isPause()) {
                        startButton.doClick();
                    }
                    try {
                        // 获取选中的进程id
                        int id = Integer.parseInt(finishTableInfo.getValueAt(finishTable.getSelectedRow(), 0).toString());
                        Iterator<PCB> iterator = manager.getSchedule().getAllPCBQueue().iterator();
                        while (iterator.hasNext()) {
                            PCB pcb = iterator.next();
                            // 找到对应进程并展示
                            if (pcb.getId() == id) {
                                processDetail.setPcb(pcb);
                                processDetail.refreshData();
                                processDetail.setVisible(true);
                                break;
                            }
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        });
        // 页表
        String[] pageTableHeader = new String[]{"逻辑页号", "内存框号", "外存块号", "装入位", "修改位"};
        this.pageTableInfo = new DefaultTableModel(new String[][]{}, pageTableHeader) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.pageTable = new JTable(this.pageTableInfo);
        this.pageTable.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.pageTable.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.pageTableScrollPane.setViewportView(this.pageTable);
        // 快表TLB
        String[] TLBTableHeader = new String[]{"逻辑页号", "内存框号"};
        this.TLBTableInfo = new DefaultTableModel(new String[][]{}, TLBTableHeader) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 关闭单元格编辑
                return false;
            }
        };
        this.TLBTable = new JTable(this.TLBTableInfo);
        this.TLBTable.getTableHeader().setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.TLBTable.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        this.TLBScrollPane.setViewportView(this.TLBTable);
    }

    /**
     * 添加按钮点击事件
     */
    public void addButtonHandler() {
        // 启动/暂停按钮
        this.startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                manager.getClock().setPause(!manager.getClock().isPause());
                if (manager.getClock().isPause()) {
                    clockState.setText("暂停");
                    startButton.setText("启动");
                } else {
                    clockState.setText("运行中");
                    startButton.setText("暂停");
                }
            }
        });
        // 添加作业按钮
        this.addJobButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                manager.getCpu().interrupt(InterruptVector.JOB_REQUEST_INTERRUPT, 0);
            }
        });
        // 内存展示按钮
        this.inMemButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 强制暂停
                if (!manager.getClock().isPause()) {
                    startButton.doClick();
                }
                // 显示弹框
                internalMemDisplay.setVisible(true);
                internalMemDisplay.refreshData();
            }
        });
        // 外存展示按钮
        this.exMemButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 强制暂停
                if (!manager.getClock().isPause()) {
                    startButton.doClick();
                }
                // 显示弹框
                externalMemDisplay.setVisible(true);
                externalMemDisplay.refreshData();
            }
        });
        // 文件系统按钮
        this.fileSystemButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 强制暂停
                if (!manager.getClock().isPause()) {
                    startButton.doClick();
                }
                // 显示弹框
                manager.getFileSystem().setRoot(true);
                fileSystemCommander.setVisible(true);

            }
        });
    }

    /**
     * 初始化页面数据结构
     */
    public void initDataStructure() {
        // 绘制内存分配情况
        this.initFrame();
        // 绘制交换区分配情况
        this.initBlock();
        // 初始化控制台
        this.initConsole();
        // 设置表格格式
        this.initTable();
        // 添加按钮绑定函数
        this.addButtonHandler();
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        panel = new JPanel();
        startButton = new JButton();
        addJobButton = new JButton();
        inMemButton = new JButton();
        exMemButton = new JButton();
        fileSystemButton = new JButton();
        consoleScrollPane = new JScrollPane();
        console = new JTextPane();
        consoleLabel = new JLabel();
        readyQueueScrollPane = new JScrollPane();
        readyQueueLabel = new JLabel();
        reserveQueueScrollPane = new JScrollPane();
        reserveQueueLabel = new JLabel();
        blockQueueLabel = new JLabel();
        blockQueueScrollPane = new JScrollPane();
        resourceblockQueueLabelA = new JLabel();
        resourceblockQueueScrollPaneA = new JScrollPane();
        resourceblockQueueLabelB = new JLabel();
        resourceblockQueueScrollPaneB = new JScrollPane();
        resourceblockQueueLabelC = new JLabel();
        resourceblockQueueScrollPaneC = new JScrollPane();
        suspendQueueLabel = new JLabel();
        suspendQueueScrollPane = new JScrollPane();
        finishQueueLabel = new JLabel();
        finishQueueScrollPane = new JScrollPane();
        clockLabel = new JLabel();
        timeLabel = new JLabel();
        clockStateLabel = new JLabel();
        time = new JLabel();
        clockState = new JLabel();
        clockBox = new JLabel();
        cpuLabel = new JLabel();
        PCLabel = new JLabel();
        IRLabel = new JLabel();
        PC = new JLabel();
        IR = new JLabel();
        cpuStateLabel = new JLabel();
        cpuState = new JLabel();
        cpuBox = new JLabel();
        runningPCBLabel = new JLabel();
        runningPCBIdLabel = new JLabel();
        runningPCBId = new JLabel();
        runningPCBPriorityLabel = new JLabel();
        runningPCBPriority = new JLabel();
        runningPCBInstructionNumLabel = new JLabel();
        runningPCBInstructionNum = new JLabel();
        runningPCBNeedPageNumLabel = new JLabel();
        runningPCBNeedPageNum = new JLabel();
        runningPCBPCLabel = new JLabel();
        runningPCBPC = new JLabel();
        runningPCBIRLabel = new JLabel();
        runningPCBIR = new JLabel();
        runningPCBPageTableBaseAddressLabel = new JLabel();
        runningPCBPageTableBaseAddress = new JLabel();
        runningPCBTimeSliceLabel = new JLabel();
        runningPCBTimeSlice = new JLabel();
        runnningBox = new JLabel();
        pageTableLabel = new JLabel();
        pageTableScrollPane = new JScrollPane();
        TLBLabel = new JLabel();
        TLBScrollPane = new JScrollPane();
        inMemAllocationLabel = new JLabel();
        inMemLabel1 = new JLabel();
        inMemLabel2 = new JLabel();
        inMemLabel3 = new JLabel();
        inMemLabel4 = new JLabel();
        inMemLabel5 = new JLabel();
        inMemLabel6 = new JLabel();
        inMemLabel7 = new JLabel();
        inMemLabel8 = new JLabel();
        inMemBox = new JLabel();
        swapAreaAllocationLabel = new JLabel();
        swapAreaAllocationScrollPane = new JScrollPane();
        swapAreaPanel = new JPanel();

        //======== this ========
        setTitle("\u4eff\u771fLinux\u7cfb\u7edf");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== panel ========
        {
            panel.setLayout(null);

            //---- startButton ----
            startButton.setText("\u542f\u52a8");
            startButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            startButton.setEnabled(false);
            panel.add(startButton);
            startButton.setBounds(770, 780, 150, 50);

            //---- addJobButton ----
            addJobButton.setText("\u6dfb\u52a0\u4f5c\u4e1a");
            addJobButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            addJobButton.setEnabled(false);
            panel.add(addJobButton);
            addJobButton.setBounds(930, 780, 150, 50);

            //---- inMemButton ----
            inMemButton.setText("\u67e5\u770b\u5185\u5b58\u4fe1\u606f");
            inMemButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            inMemButton.setEnabled(false);
            panel.add(inMemButton);
            inMemButton.setBounds(1090, 780, 150, 50);

            //---- exMemButton ----
            exMemButton.setText("\u67e5\u770b\u5916\u5b58\u4fe1\u606f");
            exMemButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            exMemButton.setEnabled(false);
            panel.add(exMemButton);
            exMemButton.setBounds(1250, 780, 150, 50);

            //---- fileSystemButton ----
            fileSystemButton.setText("\u6587\u4ef6\u7cfb\u7edf");
            fileSystemButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            fileSystemButton.setEnabled(false);
            panel.add(fileSystemButton);
            fileSystemButton.setBounds(1410, 780, 150, 50);

            //======== consoleScrollPane ========
            {
                consoleScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

                //---- console ----
                console.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                console.setEditable(false);
                consoleScrollPane.setViewportView(console);
            }
            panel.add(consoleScrollPane);
            consoleScrollPane.setBounds(25, 530, 720, 300);

            //---- consoleLabel ----
            consoleLabel.setText("\u8fd0\u884c\u65e5\u5fd7");
            consoleLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(consoleLabel);
            consoleLabel.setBounds(25, 500, 80, 30);

            //======== readyQueueScrollPane ========
            {
                readyQueueScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(readyQueueScrollPane);
            readyQueueScrollPane.setBounds(395, 45, 350, 175);

            //---- readyQueueLabel ----
            readyQueueLabel.setText("\u5c31\u7eea\u961f\u5217");
            readyQueueLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(readyQueueLabel);
            readyQueueLabel.setBounds(395, 15, 345, 30);

            //======== reserveQueueScrollPane ========
            {
                reserveQueueScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(reserveQueueScrollPane);
            reserveQueueScrollPane.setBounds(25, 45, 350, 175);

            //---- reserveQueueLabel ----
            reserveQueueLabel.setText("\u540e\u5907\u961f\u5217");
            reserveQueueLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(reserveQueueLabel);
            reserveQueueLabel.setBounds(25, 15, 80, 30);

            //---- blockQueueLabel ----
            blockQueueLabel.setText("\u963b\u585e\u961f\u5217");
            blockQueueLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(blockQueueLabel);
            blockQueueLabel.setBounds(25, 230, 80, 30);

            //======== blockQueueScrollPane ========
            {
                blockQueueScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(blockQueueScrollPane);
            blockQueueScrollPane.setBounds(25, 260, 90, 220);

            //---- resourceblockQueueLabelA ----
            resourceblockQueueLabelA.setText("\u8d44\u6e90A\u963b\u585e");
            resourceblockQueueLabelA.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(resourceblockQueueLabelA);
            resourceblockQueueLabelA.setBounds(165, 230, 115, 30);

            //======== resourceblockQueueScrollPaneA ========
            {
                resourceblockQueueScrollPaneA.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(resourceblockQueueScrollPaneA);
            resourceblockQueueScrollPaneA.setBounds(165, 260, 90, 220);

            //---- resourceblockQueueLabelB ----
            resourceblockQueueLabelB.setText("\u8d44\u6e90B\u963b\u585e");
            resourceblockQueueLabelB.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(resourceblockQueueLabelB);
            resourceblockQueueLabelB.setBounds(280, 230, 115, 30);

            //======== resourceblockQueueScrollPaneB ========
            {
                resourceblockQueueScrollPaneB.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(resourceblockQueueScrollPaneB);
            resourceblockQueueScrollPaneB.setBounds(280, 260, 90, 220);

            //---- resourceblockQueueLabelC ----
            resourceblockQueueLabelC.setText("\u8d44\u6e90C\u963b\u585e");
            resourceblockQueueLabelC.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(resourceblockQueueLabelC);
            resourceblockQueueLabelC.setBounds(395, 230, 115, 30);

            //======== resourceblockQueueScrollPaneC ========
            {
                resourceblockQueueScrollPaneC.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(resourceblockQueueScrollPaneC);
            resourceblockQueueScrollPaneC.setBounds(395, 260, 90, 220);

            //---- suspendQueueLabel ----
            suspendQueueLabel.setText("\u6302\u8d77\u961f\u5217");
            suspendQueueLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(suspendQueueLabel);
            suspendQueueLabel.setBounds(540, 230, 80, 30);

            //======== suspendQueueScrollPane ========
            {
                suspendQueueScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(suspendQueueScrollPane);
            suspendQueueScrollPane.setBounds(540, 260, 90, 220);

            //---- finishQueueLabel ----
            finishQueueLabel.setText("\u5b8c\u6210\u961f\u5217");
            finishQueueLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(finishQueueLabel);
            finishQueueLabel.setBounds(655, 230, 80, 30);

            //======== finishQueueScrollPane ========
            {
                finishQueueScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(finishQueueScrollPane);
            finishQueueScrollPane.setBounds(655, 260, 90, 220);

            //---- clockLabel ----
            clockLabel.setText("\u7cfb\u7edf\u65f6\u949f");
            clockLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(clockLabel);
            clockLabel.setBounds(770, 15, 80, 32);

            //---- timeLabel ----
            timeLabel.setText("\u5f53\u524d\u65f6\u95f4\uff1a");
            timeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(timeLabel);
            timeLabel.setBounds(775, 45, 75, 35);

            //---- clockStateLabel ----
            clockStateLabel.setText("\u5f53\u524d\u72b6\u6001\uff1a");
            clockStateLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(clockStateLabel);
            clockStateLabel.setBounds(775, 80, 75, 35);

            //---- time ----
            time.setText("0");
            time.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 18));
            panel.add(time);
            time.setBounds(850, 45, 75, 35);

            //---- clockState ----
            clockState.setText("\u672a\u542f\u52a8");
            clockState.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 18));
            panel.add(clockState);
            clockState.setBounds(850, 80, 75, 35);

            //---- clockBox ----
            clockBox.setBorder(LineBorder.createGrayLineBorder());
            panel.add(clockBox);
            clockBox.setBounds(770, 45, 160, 75);

            //---- cpuLabel ----
            cpuLabel.setText("CPU");
            cpuLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(cpuLabel);
            cpuLabel.setBounds(950, 15, 80, 32);

            //---- PCLabel ----
            PCLabel.setText("PC\uff1a");
            PCLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(PCLabel);
            PCLabel.setBounds(955, 45, 35, 35);

            //---- IRLabel ----
            IRLabel.setText("IR\uff1a");
            IRLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(IRLabel);
            IRLabel.setBounds(1030, 45, 35, 35);

            //---- PC ----
            PC.setText("0");
            PC.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 18));
            panel.add(PC);
            PC.setBounds(990, 45, 40, 35);

            //---- IR ----
            IR.setText("0");
            IR.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 18));
            panel.add(IR);
            IR.setBounds(1065, 45, 40, 35);

            //---- cpuStateLabel ----
            cpuStateLabel.setText("CPU\u72b6\u6001\uff1a");
            cpuStateLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(cpuStateLabel);
            cpuStateLabel.setBounds(955, 80, 75, 35);

            //---- cpuState ----
            cpuState.setText("\u7528\u6237\u6001");
            cpuState.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 18));
            panel.add(cpuState);
            cpuState.setBounds(1030, 80, 75, 35);

            //---- cpuBox ----
            cpuBox.setBorder(LineBorder.createGrayLineBorder());
            panel.add(cpuBox);
            cpuBox.setBounds(950, 45, 160, 75);

            //---- runningPCBLabel ----
            runningPCBLabel.setText("\u5f53\u524d\u8fd0\u884c\u8fdb\u7a0b\u4fe1\u606f");
            runningPCBLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(runningPCBLabel);
            runningPCBLabel.setBounds(770, 135, 160, 32);

            //---- runningPCBIdLabel ----
            runningPCBIdLabel.setText("\u8fdb\u7a0b ID");
            runningPCBIdLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBIdLabel);
            runningPCBIdLabel.setBounds(775, 165, 75, 35);

            //---- runningPCBId ----
            runningPCBId.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBId);
            runningPCBId.setBounds(855, 165, 75, 35);

            //---- runningPCBPriorityLabel ----
            runningPCBPriorityLabel.setText("\u8fdb\u7a0b\u4f18\u5148\u7ea7");
            runningPCBPriorityLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBPriorityLabel);
            runningPCBPriorityLabel.setBounds(775, 200, 75, 35);

            //---- runningPCBPriority ----
            runningPCBPriority.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBPriority);
            runningPCBPriority.setBounds(855, 200, 75, 35);

            //---- runningPCBInstructionNumLabel ----
            runningPCBInstructionNumLabel.setText("\u8fdb\u7a0b\u6307\u4ee4\u6570");
            runningPCBInstructionNumLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBInstructionNumLabel);
            runningPCBInstructionNumLabel.setBounds(775, 235, 75, 35);

            //---- runningPCBInstructionNum ----
            runningPCBInstructionNum.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBInstructionNum);
            runningPCBInstructionNum.setBounds(855, 235, 75, 35);

            //---- runningPCBNeedPageNumLabel ----
            runningPCBNeedPageNumLabel.setText("\u8fdb\u7a0b\u5206\u914d\u9875");
            runningPCBNeedPageNumLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBNeedPageNumLabel);
            runningPCBNeedPageNumLabel.setBounds(775, 270, 75, 35);

            //---- runningPCBNeedPageNum ----
            runningPCBNeedPageNum.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBNeedPageNum);
            runningPCBNeedPageNum.setBounds(855, 270, 75, 35);

            //---- runningPCBPCLabel ----
            runningPCBPCLabel.setText("\u8fdb\u7a0b PC");
            runningPCBPCLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBPCLabel);
            runningPCBPCLabel.setBounds(945, 165, 75, 35);

            //---- runningPCBPC ----
            runningPCBPC.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBPC);
            runningPCBPC.setBounds(1025, 165, 75, 35);

            //---- runningPCBIRLabel ----
            runningPCBIRLabel.setText("\u8fdb\u7a0b IR");
            runningPCBIRLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBIRLabel);
            runningPCBIRLabel.setBounds(945, 200, 75, 35);

            //---- runningPCBIR ----
            runningPCBIR.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBIR);
            runningPCBIR.setBounds(1025, 200, 75, 35);

            //---- runningPCBPageTableBaseAddressLabel ----
            runningPCBPageTableBaseAddressLabel.setText("\u9875\u8868\u57fa\u5740");
            runningPCBPageTableBaseAddressLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBPageTableBaseAddressLabel);
            runningPCBPageTableBaseAddressLabel.setBounds(945, 235, 75, 35);

            //---- runningPCBPageTableBaseAddress ----
            runningPCBPageTableBaseAddress.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBPageTableBaseAddress);
            runningPCBPageTableBaseAddress.setBounds(1025, 235, 75, 35);

            //---- runningPCBTimeSliceLabel ----
            runningPCBTimeSliceLabel.setText("\u5269\u4f59\u65f6\u95f4\u7247");
            runningPCBTimeSliceLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBTimeSliceLabel);
            runningPCBTimeSliceLabel.setBounds(945, 270, 75, 35);

            //---- runningPCBTimeSlice ----
            runningPCBTimeSlice.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runningPCBTimeSlice);
            runningPCBTimeSlice.setBounds(1025, 270, 75, 35);

            //---- runnningBox ----
            runnningBox.setBorder(LineBorder.createGrayLineBorder());
            runnningBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runnningBox);
            runnningBox.setBounds(770, 165, 340, 140);

            //---- pageTableLabel ----
            pageTableLabel.setText("\u9875\u8868\u4fe1\u606f");
            pageTableLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(pageTableLabel);
            pageTableLabel.setBounds(770, 320, 160, 32);

            //======== pageTableScrollPane ========
            {
                pageTableScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(pageTableScrollPane);
            pageTableScrollPane.setBounds(770, 350, 340, 230);

            //---- TLBLabel ----
            TLBLabel.setText("\u5feb\u8868\u4fe1\u606f");
            TLBLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(TLBLabel);
            TLBLabel.setBounds(770, 590, 160, 32);

            //======== TLBScrollPane ========
            {
                TLBScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
            panel.add(TLBScrollPane);
            TLBScrollPane.setBounds(770, 620, 340, 150);

            //---- inMemAllocationLabel ----
            inMemAllocationLabel.setText("\u5185\u5b58\u5206\u914d\u60c5\u51b5");
            inMemAllocationLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(inMemAllocationLabel);
            inMemAllocationLabel.setBounds(1140, 15, 160, 32);

            //---- inMemLabel1 ----
            inMemLabel1.setText("00 - 07");
            inMemLabel1.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inMemLabel1);
            inMemLabel1.setBounds(1160, 50, 60, 25);

            //---- inMemLabel2 ----
            inMemLabel2.setText("08 - 15");
            inMemLabel2.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inMemLabel2);
            inMemLabel2.setBounds(1160, 75, 60, 25);

            //---- inMemLabel3 ----
            inMemLabel3.setText("16 - 23");
            inMemLabel3.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inMemLabel3);
            inMemLabel3.setBounds(1160, 100, 60, 25);

            //---- inMemLabel4 ----
            inMemLabel4.setText("24 - 31");
            inMemLabel4.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inMemLabel4);
            inMemLabel4.setBounds(1160, 125, 60, 25);

            //---- inMemLabel5 ----
            inMemLabel5.setText("32 - 39");
            inMemLabel5.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inMemLabel5);
            inMemLabel5.setBounds(1160, 150, 60, 25);

            //---- inMemLabel6 ----
            inMemLabel6.setText("40 - 47");
            inMemLabel6.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inMemLabel6);
            inMemLabel6.setBounds(1160, 175, 60, 25);

            //---- inMemLabel7 ----
            inMemLabel7.setText("48 - 55");
            inMemLabel7.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inMemLabel7);
            inMemLabel7.setBounds(1160, 200, 60, 25);

            //---- inMemLabel8 ----
            inMemLabel8.setText("56 - 63");
            inMemLabel8.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inMemLabel8);
            inMemLabel8.setBounds(1160, 225, 60, 25);

            //---- inMemBox ----
            inMemBox.setBorder(LineBorder.createGrayLineBorder());
            panel.add(inMemBox);
            inMemBox.setBounds(1140, 45, 420, 210);

            //---- swapAreaAllocationLabel ----
            swapAreaAllocationLabel.setText("\u5916\u5b58\u4ea4\u6362\u533a\u5206\u914d\u60c5\u51b5");
            swapAreaAllocationLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            panel.add(swapAreaAllocationLabel);
            swapAreaAllocationLabel.setBounds(1140, 265, 160, 32);

            //======== swapAreaAllocationScrollPane ========
            {
                swapAreaAllocationScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

                //======== swapAreaPanel ========
                {
                    swapAreaPanel.setAutoscrolls(true);
                    swapAreaPanel.setPreferredSize(null);
                    swapAreaPanel.setLayout(null);

                    { // compute preferred size
                        Dimension preferredSize = new Dimension();
                        for(int i = 0; i < swapAreaPanel.getComponentCount(); i++) {
                            Rectangle bounds = swapAreaPanel.getComponent(i).getBounds();
                            preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                            preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                        }
                        Insets insets = swapAreaPanel.getInsets();
                        preferredSize.width += insets.right;
                        preferredSize.height += insets.bottom;
                        swapAreaPanel.setMinimumSize(preferredSize);
                        swapAreaPanel.setPreferredSize(preferredSize);
                    }
                }
                swapAreaAllocationScrollPane.setViewportView(swapAreaPanel);
            }
            panel.add(swapAreaAllocationScrollPane);
            swapAreaAllocationScrollPane.setBounds(1140, 300, 420, 470);

            { // compute preferred size
                Dimension preferredSize = new Dimension();
                for(int i = 0; i < panel.getComponentCount(); i++) {
                    Rectangle bounds = panel.getComponent(i).getBounds();
                    preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                    preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                }
                Insets insets = panel.getInsets();
                preferredSize.width += insets.right;
                preferredSize.height += insets.bottom;
                panel.setMinimumSize(preferredSize);
                panel.setPreferredSize(preferredSize);
            }
        }
        contentPane.add(panel, BorderLayout.CENTER);
        setSize(1600, 900);
        setLocationRelativeTo(null);
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    public JPanel panel;
    public JButton startButton;
    public JButton addJobButton;
    public JButton inMemButton;
    public JButton exMemButton;
    public JButton fileSystemButton;
    public JScrollPane consoleScrollPane;
    public JTextPane console;
    public JLabel consoleLabel;
    public JScrollPane readyQueueScrollPane;
    public JLabel readyQueueLabel;
    public JScrollPane reserveQueueScrollPane;
    public JLabel reserveQueueLabel;
    public JLabel blockQueueLabel;
    public JScrollPane blockQueueScrollPane;
    public JLabel resourceblockQueueLabelA;
    public JScrollPane resourceblockQueueScrollPaneA;
    public JLabel resourceblockQueueLabelB;
    public JScrollPane resourceblockQueueScrollPaneB;
    public JLabel resourceblockQueueLabelC;
    public JScrollPane resourceblockQueueScrollPaneC;
    public JLabel suspendQueueLabel;
    public JScrollPane suspendQueueScrollPane;
    public JLabel finishQueueLabel;
    public JScrollPane finishQueueScrollPane;
    public JLabel clockLabel;
    public JLabel timeLabel;
    public JLabel clockStateLabel;
    public JLabel time;
    public JLabel clockState;
    public JLabel clockBox;
    public JLabel cpuLabel;
    public JLabel PCLabel;
    public JLabel IRLabel;
    public JLabel PC;
    public JLabel IR;
    public JLabel cpuStateLabel;
    public JLabel cpuState;
    public JLabel cpuBox;
    public JLabel runningPCBLabel;
    public JLabel runningPCBIdLabel;
    public JLabel runningPCBId;
    public JLabel runningPCBPriorityLabel;
    public JLabel runningPCBPriority;
    public JLabel runningPCBInstructionNumLabel;
    public JLabel runningPCBInstructionNum;
    public JLabel runningPCBNeedPageNumLabel;
    public JLabel runningPCBNeedPageNum;
    public JLabel runningPCBPCLabel;
    public JLabel runningPCBPC;
    public JLabel runningPCBIRLabel;
    public JLabel runningPCBIR;
    public JLabel runningPCBPageTableBaseAddressLabel;
    public JLabel runningPCBPageTableBaseAddress;
    public JLabel runningPCBTimeSliceLabel;
    public JLabel runningPCBTimeSlice;
    public JLabel runnningBox;
    public JLabel pageTableLabel;
    public JScrollPane pageTableScrollPane;
    public JLabel TLBLabel;
    public JScrollPane TLBScrollPane;
    public JLabel inMemAllocationLabel;
    public JLabel inMemLabel1;
    public JLabel inMemLabel2;
    public JLabel inMemLabel3;
    public JLabel inMemLabel4;
    public JLabel inMemLabel5;
    public JLabel inMemLabel6;
    public JLabel inMemLabel7;
    public JLabel inMemLabel8;
    public JLabel inMemBox;
    public JLabel swapAreaAllocationLabel;
    public JScrollPane swapAreaAllocationScrollPane;
    public JPanel swapAreaPanel;
    // JFormDesigner - End of variables declaration  //GEN-END:variables


    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }
}
