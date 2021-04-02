/*
 * Created by JFormDesigner on Mon Mar 29 16:00:24 CST 2021
 */

package gui;

import kernel.PCB;

import java.awt.*;
import javax.swing.*;

/**
 * 进程详情弹框
 *
 * @author ZJC
 */
public class ProcessDetail extends JDialog {
    private Dashboard dashboard;
    private PCB pcb;

    public ProcessDetail(Dashboard owner) {
        super(owner);
        this.dashboard = owner;
        this.pcb = null;
        initComponents();
        this.refreshData();
    }

    /**
     * 刷新数据
     */
    public void refreshData() {
        if (this.pcb == null) {
            this.id.setText("");
            this.priority.setText("");
            this.instructionNum.setText("");
            this.needPageNum.setText("");
            this.PC.setText("");
            this.IR.setText("");
            this.pageTableBaseAddress.setText("");
            this.state.setText("");
            this.inBlockTime.setText("");
            this.createTime.setText("");
            this.endTime.setText("");
            this.runTime.setText("");
            this.turnTime.setText("");
        } else {
            this.id.setText("" + this.pcb.getId());
            this.priority.setText("" + this.pcb.getPriority());
            this.instructionNum.setText("" + this.pcb.getInstructionNum());
            this.needPageNum.setText("" + this.pcb.getAllocatePageFrameNum());
            this.PC.setText("" + this.pcb.getPC());
            this.IR.setText("" + this.pcb.getIR());
            this.pageTableBaseAddress.setText("" + this.pcb.getPageTableBaseAddress());
            this.state.setText("" + this.pcb.getState());
            this.inBlockTime.setText("" + this.pcb.getInBlockQueueTime());
            this.createTime.setText("" + this.pcb.getInTime());
            this.endTime.setText("" + this.pcb.getEndTime());
            this.runTime.setText("" + this.pcb.getRunTime());
            this.turnTime.setText("" + this.pcb.getTurnTime());
        }
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        panel = new JPanel();
        idLabel = new JLabel();
        id = new JLabel();
        PCLabel = new JLabel();
        PC = new JLabel();
        IRLabel = new JLabel();
        IR = new JLabel();
        priority = new JLabel();
        priorityLabel = new JLabel();
        instructionNumLabel = new JLabel();
        instructionNum = new JLabel();
        pageTableBaseAddressLabel = new JLabel();
        pageTableBaseAddress = new JLabel();
        needPageNum = new JLabel();
        needPageNumLabel = new JLabel();
        stateLabel = new JLabel();
        state = new JLabel();
        inBLockTimeLabel = new JLabel();
        inBlockTime = new JLabel();
        createTimeLabel = new JLabel();
        createTime = new JLabel();
        endTimeLabel = new JLabel();
        endTime = new JLabel();
        runTimeLabel = new JLabel();
        runTime = new JLabel();
        turnTimeLabel = new JLabel();
        turnTime = new JLabel();

        //======== this ========
        setTitle("\u8fdb\u7a0b\u8be6\u60c5");
        setModal(true);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== panel ========
        {
            panel.setLayout(null);

            //---- idLabel ----
            idLabel.setText("\u8fdb\u7a0b ID");
            idLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(idLabel);
            idLabel.setBounds(20, 10, 75, 35);

            //---- id ----
            id.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(id);
            id.setBounds(100, 10, 75, 35);

            //---- PCLabel ----
            PCLabel.setText("\u8fdb\u7a0b PC");
            PCLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(PCLabel);
            PCLabel.setBounds(190, 10, 75, 35);

            //---- PC ----
            PC.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(PC);
            PC.setBounds(270, 10, 75, 35);

            //---- IRLabel ----
            IRLabel.setText("\u8fdb\u7a0b IR");
            IRLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(IRLabel);
            IRLabel.setBounds(190, 45, 75, 35);

            //---- IR ----
            IR.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(IR);
            IR.setBounds(270, 45, 75, 35);

            //---- priority ----
            priority.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(priority);
            priority.setBounds(100, 45, 75, 35);

            //---- priorityLabel ----
            priorityLabel.setText("\u8fdb\u7a0b\u4f18\u5148\u7ea7");
            priorityLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(priorityLabel);
            priorityLabel.setBounds(20, 45, 75, 35);

            //---- instructionNumLabel ----
            instructionNumLabel.setText("\u8fdb\u7a0b\u6307\u4ee4\u6570");
            instructionNumLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(instructionNumLabel);
            instructionNumLabel.setBounds(20, 80, 75, 35);

            //---- instructionNum ----
            instructionNum.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(instructionNum);
            instructionNum.setBounds(100, 80, 75, 35);

            //---- pageTableBaseAddressLabel ----
            pageTableBaseAddressLabel.setText("\u9875\u8868\u57fa\u5740");
            pageTableBaseAddressLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(pageTableBaseAddressLabel);
            pageTableBaseAddressLabel.setBounds(190, 80, 75, 35);

            //---- pageTableBaseAddress ----
            pageTableBaseAddress.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(pageTableBaseAddress);
            pageTableBaseAddress.setBounds(270, 80, 75, 35);

            //---- needPageNum ----
            needPageNum.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(needPageNum);
            needPageNum.setBounds(100, 115, 75, 35);

            //---- needPageNumLabel ----
            needPageNumLabel.setText("\u8fdb\u7a0b\u5206\u914d\u9875");
            needPageNumLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(needPageNumLabel);
            needPageNumLabel.setBounds(20, 115, 75, 35);

            //---- stateLabel ----
            stateLabel.setText("\u8fdb\u7a0b\u72b6\u6001");
            stateLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(stateLabel);
            stateLabel.setBounds(20, 170, 75, 35);

            //---- state ----
            state.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(state);
            state.setBounds(100, 170, 75, 35);

            //---- inBLockTimeLabel ----
            inBLockTimeLabel.setText("\u6700\u8fd1\u963b\u585e\u65f6");
            inBLockTimeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inBLockTimeLabel);
            inBLockTimeLabel.setBounds(190, 170, 75, 35);

            //---- inBlockTime ----
            inBlockTime.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(inBlockTime);
            inBlockTime.setBounds(270, 170, 75, 35);

            //---- createTimeLabel ----
            createTimeLabel.setText("\u521b\u5efa\u65f6\u95f4");
            createTimeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(createTimeLabel);
            createTimeLabel.setBounds(20, 205, 75, 35);

            //---- createTime ----
            createTime.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(createTime);
            createTime.setBounds(100, 205, 75, 35);

            //---- endTimeLabel ----
            endTimeLabel.setText("\u7ed3\u675f\u65f6\u95f4");
            endTimeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(endTimeLabel);
            endTimeLabel.setBounds(190, 205, 75, 35);

            //---- endTime ----
            endTime.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(endTime);
            endTime.setBounds(270, 205, 75, 35);

            //---- runTimeLabel ----
            runTimeLabel.setText("\u8fd0\u884c\u65f6\u95f4");
            runTimeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runTimeLabel);
            runTimeLabel.setBounds(20, 240, 75, 35);

            //---- runTime ----
            runTime.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(runTime);
            runTime.setBounds(100, 240, 75, 35);

            //---- turnTimeLabel ----
            turnTimeLabel.setText("\u5468\u8f6c\u65f6\u95f4");
            turnTimeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(turnTimeLabel);
            turnTimeLabel.setBounds(190, 240, 75, 35);

            //---- turnTime ----
            turnTime.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(turnTime);
            turnTime.setBounds(270, 240, 75, 35);

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
        setSize(365, 330);
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    public JPanel panel;
    public JLabel idLabel;
    public JLabel id;
    public JLabel PCLabel;
    public JLabel PC;
    public JLabel IRLabel;
    public JLabel IR;
    public JLabel priority;
    public JLabel priorityLabel;
    public JLabel instructionNumLabel;
    public JLabel instructionNum;
    public JLabel pageTableBaseAddressLabel;
    public JLabel pageTableBaseAddress;
    public JLabel needPageNum;
    public JLabel needPageNumLabel;
    public JLabel stateLabel;
    public JLabel state;
    public JLabel inBLockTimeLabel;
    public JLabel inBlockTime;
    public JLabel createTimeLabel;
    public JLabel createTime;
    public JLabel endTimeLabel;
    public JLabel endTime;
    public JLabel runTimeLabel;
    public JLabel runTime;
    public JLabel turnTimeLabel;
    public JLabel turnTime;
    // JFormDesigner - End of variables declaration  //GEN-END:variables


    public Dashboard getDashboard() {
        return dashboard;
    }

    public void setDashboard(Dashboard dashboard) {
        this.dashboard = dashboard;
    }

    public PCB getPcb() {
        return pcb;
    }

    public void setPcb(PCB pcb) {
        this.pcb = pcb;
    }
}
