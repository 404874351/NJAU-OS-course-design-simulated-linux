/*
 * Created by JFormDesigner on Wed Mar 03 01:39:02 CST 2021
 */

package gui;

import javax.swing.border.*;
import hardware.InternalMem;
import kernel.Page;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

/**
 * 内存信息展示框
 *
 * @author ZJC
 */
public class InternalMemDisplay extends JDialog {
    private Dashboard dashboard;

    public JLabel[] ranges;
    public JLabel[] bytes;

    public InternalMemDisplay(Dashboard owner) {
        super(owner);
        this.dashboard = owner;
        this.initComponents();
        this.initDataStructure();
    }

    /**
     * 刷新页框数据
     */
    public synchronized void refreshData() {
        int frameNo = this.frameNoSelector.getSelectedIndex();
        Page page = new Page();
        page.setInternalFrameNo(frameNo);
        this.dashboard.getManager().getInMem().readPage(page);
        for (int i = 0; i < this.bytes.length; ++i) {
            if (i % 8 == 0) {
                String startNo = "" + (this.frameNoSelector.getSelectedIndex() * InternalMem.PAGE_SIZE + i);
                String endNo = "" + (this.frameNoSelector.getSelectedIndex() * InternalMem.PAGE_SIZE + i + 7);
                while (startNo.length() < 5) {
                    startNo = "0" + startNo;
                }
                while (endNo.length() < 5) {
                    endNo = "0" + endNo;
                }
                ranges[i / 8].setText(startNo + " - " + endNo);
            }
            String dataString = Integer.toHexString((int)page.getData()[i]);
            if (dataString.length() >= 2) {
                dataString = dataString.substring(dataString.length() - 2);
            } else {
                int appendZeroNum = 2 - dataString.length();
                for(int j = 0; j < appendZeroNum; ++j) {
                    dataString = "0" + dataString;
                }
            }
            bytes[i].setText(dataString.toUpperCase());
        }

    }

    /**
     * 初始化页面数据结构
     */
    public void initDataStructure() {
        for (int i = 0; i < InternalMem.PAGE_NUM; ++i) {
            this.frameNoSelector.addItem(i);
        }
        this.ranges = new JLabel[InternalMem.PAGE_SIZE / 8];
        this.bytes = new JLabel[InternalMem.PAGE_SIZE];
        int x = 110, y = 0;
        for (int i = 0; i < this.bytes.length; ++i) {
            if (i % 8 == 0) {
                ranges[i / 8] = new JLabel();
                String startNo = "" + (this.frameNoSelector.getSelectedIndex() * InternalMem.PAGE_SIZE + i);
                String endNo = "" + (this.frameNoSelector.getSelectedIndex() * InternalMem.PAGE_SIZE + i + 7);
                while (startNo.length() < 5) {
                    startNo = "0" + startNo;
                }
                while (endNo.length() < 5) {
                    endNo = "0" + endNo;
                }
                ranges[i / 8].setText(startNo + " - " + endNo);
                ranges[i / 8].setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
                ranges[i / 8].setBounds(10, y, 100, 25);
                this.detailPanel.add(ranges[i / 8]);
            }
            bytes[i] = new JLabel();
            bytes[i].setText("FF");
            bytes[i].setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            bytes[i].setHorizontalAlignment(SwingConstants.CENTER);
            bytes[i].setBounds(x, y, 25, 25);
            this.detailPanel.add(bytes[i]);

            x += 40;
            if (i % 8 == 7) {
                x -= 40 * 8;
                y += 25;
            }
        }
        this.detailPanel.setPreferredSize(new Dimension(420, 1605));
        this.scrollPane.setViewportView(this.detailPanel);

        this.button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshData();
            }
        });
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        panel = new JPanel();
        frameNoLabel = new JLabel();
        frameNoSelector = new JComboBox();
        button = new JButton();
        frameDetailLabel = new JLabel();
        scrollPane = new JScrollPane();
        detailPanel = new JPanel();

        //======== this ========
        setTitle("\u5185\u5b58\u4fe1\u606f");
        setModal(true);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== panel ========
        {
            panel.setLayout(null);

            //---- frameNoLabel ----
            frameNoLabel.setText("\u9009\u62e9\u9875\u6846\u53f7");
            frameNoLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            frameNoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(frameNoLabel);
            frameNoLabel.setBounds(25, 15, 80, 25);

            //---- frameNoSelector ----
            frameNoSelector.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(frameNoSelector);
            frameNoSelector.setBounds(25, 45, 80, 35);

            //---- button ----
            button.setText("\u786e\u5b9a");
            button.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(button);
            button.setBounds(25, 85, 80, 35);

            //---- frameDetailLabel ----
            frameDetailLabel.setText("\u9875\u6846\u8be6\u60c5");
            frameDetailLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(frameDetailLabel);
            frameDetailLabel.setBounds(135, 15, 80, 25);

            //======== scrollPane ========
            {
                scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

                //======== detailPanel ========
                {
                    detailPanel.setLayout(null);

                    { // compute preferred size
                        Dimension preferredSize = new Dimension();
                        for(int i = 0; i < detailPanel.getComponentCount(); i++) {
                            Rectangle bounds = detailPanel.getComponent(i).getBounds();
                            preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                            preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                        }
                        Insets insets = detailPanel.getInsets();
                        preferredSize.width += insets.right;
                        preferredSize.height += insets.bottom;
                        detailPanel.setMinimumSize(preferredSize);
                        detailPanel.setPreferredSize(preferredSize);
                    }
                }
                scrollPane.setViewportView(detailPanel);
            }
            panel.add(scrollPane);
            scrollPane.setBounds(135, 45, 430, 390);

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
        setSize(600, 500);
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    public JPanel panel;
    public JLabel frameNoLabel;
    public JComboBox frameNoSelector;
    public JButton button;
    public JLabel frameDetailLabel;
    public JScrollPane scrollPane;
    public JPanel detailPanel;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
