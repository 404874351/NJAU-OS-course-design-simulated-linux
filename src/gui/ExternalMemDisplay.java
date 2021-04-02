/*
 * Created by JFormDesigner on Wed Mar 03 01:58:56 CST 2021
 */

package gui;


import hardware.ExternalMem;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

/**
 * 外存信息展示
 *
 * @author ZJC
 */
public class ExternalMemDisplay extends JDialog {
    private Dashboard dashboard;

    public JLabel[] ranges;
    public JLabel[] bytes;
    public ExternalMemDisplay(Dashboard owner) {
        super(owner);
        this.dashboard = owner;
        initComponents();
        this.initDataStructure();
    }

    /**
     * 刷新外存数据
     */
    public synchronized void refreshData() {
        int cylinderNo = this.cylinderNoSelector.getSelectedIndex();
        int trackNo = this.trackNoSelector.getSelectedIndex();
        int sectorNo = this.sectorNoSelector.getSelectedIndex();
        int blockNo = cylinderNo * ExternalMem.TRACK_NUM * ExternalMem.SECTOR_NUM + trackNo * ExternalMem.SECTOR_NUM + sectorNo;

        byte[] data = new byte[ExternalMem.SECTOR_SIZE];
        this.dashboard.getManager().getExMem().readPage(blockNo, data);
        for (int i = 0; i < this.bytes.length; ++i) {
            if (i % 8 == 0) {
                String startNo = Integer.toHexString(blockNo * ExternalMem.SECTOR_SIZE + i).toUpperCase();
                String endNo = Integer.toHexString(blockNo * ExternalMem.SECTOR_SIZE + i + 7).toUpperCase();
                while (startNo.length() < 6) {
                    startNo = "0" + startNo;
                }
                while (endNo.length() < 6) {
                    endNo = "0" + endNo;
                }
                ranges[i / 8].setText(startNo + " - " + endNo);
            }
            String dataString = Integer.toHexString((int)data[i]);
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
        for (int i = 0; i < ExternalMem.CYLINDER_NUM; ++i) {
            this.cylinderNoSelector.addItem(i);
        }
        for (int i = 0; i < ExternalMem.TRACK_NUM; ++i) {
            this.trackNoSelector.addItem(i);
        }
        for (int i = 0; i < ExternalMem.SECTOR_NUM; ++i) {
            this.sectorNoSelector.addItem(i);
        }
        this.ranges = new JLabel[ExternalMem.SECTOR_SIZE / 8];
        this.bytes = new JLabel[ExternalMem.SECTOR_SIZE];
        int x = 130, y = 0;
        for (int i = 0; i < this.bytes.length; ++i) {
            if (i % 8 == 0) {
                ranges[i / 8] = new JLabel();
                String startNo = Integer.toHexString(this.cylinderNoSelector.getSelectedIndex() *
                        this.trackNoSelector.getSelectedIndex() *
                        this.sectorNoSelector.getSelectedIndex() *
                        ExternalMem.SECTOR_SIZE + i).toUpperCase();
                String endNo = Integer.toHexString(this.cylinderNoSelector.getSelectedIndex() *
                        this.trackNoSelector.getSelectedIndex() *
                        this.sectorNoSelector.getSelectedIndex() *
                        ExternalMem.SECTOR_SIZE + i + 7).toUpperCase();
                while (startNo.length() < 6) {
                    startNo = "0" + startNo;
                }
                while (endNo.length() < 6) {
                    endNo = "0" + endNo;
                }
                ranges[i / 8].setText(startNo + " - " + endNo);
                ranges[i / 8].setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
                ranges[i / 8].setBounds(10, y, 120, 25);
                this.detailPanel.add(ranges[i / 8]);
            }
            bytes[i] = new JLabel();
            bytes[i].setText("FF");
            bytes[i].setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            bytes[i].setHorizontalAlignment(SwingConstants.CENTER);
            bytes[i].setBounds(x, y, 25, 25);
            this.detailPanel.add(bytes[i]);

            x += 35;
            if (i % 8 == 7) {
                x -= 35 * 8;
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
        cylinderNoLabel = new JLabel();
        cylinderNoSelector = new JComboBox();
        trackNoSelector = new JComboBox();
        trackNoLabel = new JLabel();
        sectorNoSelector = new JComboBox();
        sectorNoLabel = new JLabel();
        button = new JButton();
        blockDetailLabel = new JLabel();
        scrollPane = new JScrollPane();
        detailPanel = new JPanel();

        //======== this ========
        setTitle("\u5916\u5b58\u4fe1\u606f");
        setModal(true);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== panel ========
        {
            panel.setLayout(null);

            //---- cylinderNoLabel ----
            cylinderNoLabel.setText("\u9009\u62e9\u67f1\u9762\u53f7");
            cylinderNoLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            cylinderNoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(cylinderNoLabel);
            cylinderNoLabel.setBounds(25, 15, 80, 25);

            //---- cylinderNoSelector ----
            cylinderNoSelector.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(cylinderNoSelector);
            cylinderNoSelector.setBounds(25, 45, 80, 35);

            //---- trackNoSelector ----
            trackNoSelector.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(trackNoSelector);
            trackNoSelector.setBounds(25, 115, 80, 35);

            //---- trackNoLabel ----
            trackNoLabel.setText("\u9009\u62e9\u78c1\u9053\u53f7");
            trackNoLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            trackNoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(trackNoLabel);
            trackNoLabel.setBounds(25, 85, 80, 25);

            //---- sectorNoSelector ----
            sectorNoSelector.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(sectorNoSelector);
            sectorNoSelector.setBounds(25, 185, 80, 35);

            //---- sectorNoLabel ----
            sectorNoLabel.setText("\u9009\u62e9\u6247\u533a\u53f7");
            sectorNoLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            sectorNoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(sectorNoLabel);
            sectorNoLabel.setBounds(25, 155, 80, 25);

            //---- button ----
            button.setText("\u786e\u5b9a");
            button.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(button);
            button.setBounds(25, 225, 80, 35);

            //---- blockDetailLabel ----
            blockDetailLabel.setText("\u5757\u8be6\u60c5");
            blockDetailLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            panel.add(blockDetailLabel);
            blockDetailLabel.setBounds(135, 15, 50, 25);

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
    public JLabel cylinderNoLabel;
    public JComboBox cylinderNoSelector;
    public JComboBox trackNoSelector;
    public JLabel trackNoLabel;
    public JComboBox sectorNoSelector;
    public JLabel sectorNoLabel;
    public JButton button;
    public JLabel blockDetailLabel;
    public JScrollPane scrollPane;
    public JPanel detailPanel;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
