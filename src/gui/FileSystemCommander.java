/*
 * Created by JFormDesigner on Wed Mar 03 02:00:24 CST 2021
 */

package gui;

import org.omg.CORBA.INVALID_ACTIVITY;

import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;
import javax.swing.*;
import javax.swing.text.DefaultCaret;

/**
 * 文件系统 Shell
 *
 * @author ZJC
 */
public class FileSystemCommander extends JDialog {
    private Dashboard dashboard;
    public Vector<String> history;
    public int historyPointer;
    public String commander;
    public String vimState;
    public int vimMode;
    public static final int VIM_PLAIN = 0;
    public static final int VIM_INSERT = 1;
    public static final int VIM_COMMANDER = 2;
    public static final int[] INVALID_CHAR = new int[]{
            KeyEvent.VK_SHIFT,
            KeyEvent.VK_CONTROL,
            KeyEvent.VK_ESCAPE,
            KeyEvent.VK_CAPS_LOCK,
            KeyEvent.VK_NUM_LOCK,
//            KeyEvent.VK_UP,
//            KeyEvent.VK_DOWN,
            KeyEvent.VK_LEFT,
            KeyEvent.VK_RIGHT,
    };


    public FileSystemCommander(Dashboard owner) {
        super(owner);
        this.dashboard = owner;
        this.initComponents();
        this.initDataStructure();
    }

    /**
     * 准备读取下一条指令
     */
    public void readyToNext() {
        String currentDirPath = this.dashboard.getManager().getFileSystem() == null ?
            "/" : this.dashboard.getManager().getFileSystem().getCurrentDirPath();
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String prompt = "[root  " + currentTime +
                "  "  + currentDirPath +
                "  ]  ";
        // 插入cmd提示符，重置指令
        this.cmd.append(prompt);
        // 添加历史记录
        if (this.commander != null && this.commander.length() != 0) {
            this.history.add(this.commander);
        }
        // 重置历史记录指针
        this.historyPointer = this.history.size();
        // 重置当前指令
        this.commander = "";
    }

    /**
     * 进入vim
     */
    public void vimIn() {
        this.vimMode = VIM_PLAIN;
        this.vimState = "PLAIN";
        this.cmdScrollPane.setVisible(false);

        this.vimScrollPane.setVisible(true);
        this.vim.setEnabled(false);

        this.vimModeInput.setVisible(true);
        this.vimModeInput.setText(vimState);
        this.vimModeInput.setEnabled(false);
        this.defaultInput.setVisible(true);
        this.defaultInput.setEnabled(true);
        this.defaultInput.requestFocus(true);
    }

    /**
     * 退出vim
     */
    public void vimOut() {
        this.vim.setEnabled(false);
        this.vim.setText("");
        this.vimScrollPane.setVisible(false);

        this.vimModeInput.setEnabled(false);
        this.vimModeInput.setText("");
        this.vimModeInput.setVisible(false);

        this.defaultInput.setEnabled(false);
        this.defaultInput.setText("");

        this.cmdScrollPane.setVisible(true);
        this.cmd.requestFocus(true);
    }

    /**
     * 初始化页面数据结构
     */
    public void initDataStructure() {
        // 隐藏vim界面
        this.vimScrollPane.setVisible(false);
        this.vimModeInput.setVisible(false);
        // 滚动条默认处于最底端
        DefaultCaret cmdCaret = (DefaultCaret) cmd.getCaret();
        cmdCaret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        DefaultCaret vimCaret = (DefaultCaret) vim.getCaret();
        vimCaret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        // 初始化历史记录和对应指针
        this.history = new Vector<>();
        this.historyPointer = this.history.size();
        // 添加窗口事件
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dashboard.getManager().getFileSystem().setRoot(false);
            }
        });
        // 添加键盘事件
        cmd.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                switch (code) {
                    // 输入 backspace
                    case KeyEvent.VK_BACK_SPACE: {
                        // 禁用过多的退格
                        if (commander.length() == 0) {
                            e.consume();
                            return;
                        }
                        // 命令内容撤销
                        commander = commander.substring(0, commander.length() - 1);
                        break;
                    }
                    // 输入 enter
                    case KeyEvent.VK_ENTER: {
                        // 禁用默认换行
                        e.consume();
                        cmd.append("\n");
                        if (commander.length() > 0) {
                            // 发送一条指令并执行
                            dashboard.getManager().getFileSystem().cmd(commander);
                        }
                        // 准备接收下一条指令
                        readyToNext();
                        break;
                    }
                    // 输入 UP
                    case KeyEvent.VK_UP: {
                        // 禁用默认光标移动
                        e.consume();
                        // 调用上一条历史指令
                        if (historyPointer > 0) {
                            String tempStr = cmd.getText();
                            cmd.setText(tempStr.substring(0, tempStr.length() - commander.length()));
                            commander = history.get(--historyPointer);
                            cmd.append(commander);
                        }
                        break;
                    }
                    // 输入 DOWN
                    case KeyEvent.VK_DOWN: {
                        // 禁用默认光标移动
                        e.consume();
                        // 调用下一条历史指令
                        if (historyPointer < history.size() - 1) {
                            String tempStr = cmd.getText();
                            cmd.setText(tempStr.substring(0, tempStr.length() - commander.length()));
                            commander = history.get(++historyPointer);
                            cmd.append(commander);
                        }
                        break;
                    }
                    // 输入一般字符
                    default: {
                        for (int i = 0; i < INVALID_CHAR.length; ++i) {
                            if (code == INVALID_CHAR[i]) {
                                return;
                            }
                        }
                        // 如果是可显示字符，则更新接收的指令
                        commander += new String(new char[]{e.getKeyChar()});
                        break;
                    }
                }
            }
        });
        // 该form只会在普通模式被激活
        defaultInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                char key = e.getKeyChar();
                // 普通模式
                if (vimMode == VIM_PLAIN) {
                    switch (key) {
                        // 输入 I，进入插入模式
                        case 'i': {
                            // defaultInput禁用
                            defaultInput.setEnabled(false);
                            // vim激活
                            vim.setEnabled(true);
                            vim.requestFocus(true);
                            // Mode禁用，显示INSERT标志
                            vimMode = VIM_INSERT;
                            vimState = "INSERT";
                            vimModeInput.setEnabled(false);
                            vimModeInput.setText(vimState);
                            break;
                        }
                        // 输入 :，进入命令模式
                        case ':': {
                            // defaultInput禁用
                            defaultInput.setEnabled(false);
                            // vim禁用
                            vim.setEnabled(false);
                            // Mode激活
                            vimMode = VIM_COMMANDER;
                            vimState = ":";
                            vimModeInput.setText(vimState);
                            vimModeInput.setEnabled(true);
                            vimModeInput.requestFocus(true);
                            break;
                        }
                        // 输入其他字符即默认输入
                        default: {
                            break;
                        }
                    }
                } else {
                    e.consume();
                }
            }
        });
        // 该form只会在插入模式被激活
        vim.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                // 插入模式
                if (vimMode == VIM_INSERT) {
                    switch (code) {
                        // 输入 ESC，进入普通模式
                        case KeyEvent.VK_ESCAPE: {
                            // defaultInput激活
                            defaultInput.setEnabled(true);
                            defaultInput.requestFocus(true);
                            // vim禁用
                            vim.setEnabled(false);
                            // Mode禁用，显示普通标志
                            vimMode = VIM_PLAIN;
                            vimState = "PLAIN";
                            vimModeInput.setEnabled(false);
                            vimModeInput.setText(vimState);
                            break;
                        }
                        // 输入其他字符即默认键入
                        default: {
                            break;
                        }
                    }
                } else {
                    e.consume();
                }
            }
        });
        // 该form只会在命令模式被激活
        vimModeInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                // 命令模式，仅接受 :wq 和 :q
                if (vimMode == VIM_COMMANDER) {
                    switch (code) {
                        // 输入 ESC，进入普通模式
                        case KeyEvent.VK_ESCAPE: {
                            // defaultInput激活
                            defaultInput.setEnabled(true);
                            defaultInput.requestFocus(true);
                            // vim禁用
                            vim.setEnabled(false);
                            // Mode禁用，显示普通标志
                            vimMode = VIM_PLAIN;
                            vimState = "PLAIN";
                            vimModeInput.setEnabled(false);
                            vimModeInput.setText(vimState);
                            break;
                        }
                        // 输入 enter，尝试保存/退出
                        case KeyEvent.VK_ENTER: {
                            // 禁用默认换行
                            e.consume();
                            if (vimModeInput.getText().equals(":wq")) {
                                // 保存并退出
                                dashboard.getManager().getFileSystem().vimWrite(history.get(history.size() - 1), vim.getText());
                                vimOut();
                            } else if (vimModeInput.getText().equals(":q")) {
                                // 放弃修改并直接退出
                                vimOut();
                            } else {
                                vimModeInput.setText(":");
                            }
                            break;
                        }
                        // 输入一般字符即默认键入
                        default: {
                            break;
                        }
                    }
                } else {
                    e.consume();
                }
            }
        });

        // 准备接收下一条指令
        this.readyToNext();
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        panel = new JPanel();
        defaultInput = new JTextField();
        vimModeInput = new JTextField();
        cmdScrollPane = new JScrollPane();
        cmd = new JTextArea();
        vimScrollPane = new JScrollPane();
        vim = new JTextArea();

        //======== this ========
        setTitle("\u6587\u4ef6\u7cfb\u7edf Shell");
        setModal(true);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== panel ========
        {
            panel.setBackground(Color.black);
            panel.setLayout(null);

            //---- defaultInput ----
            defaultInput.setBackground(Color.pink);
            panel.add(defaultInput);
            defaultInput.setBounds(0, 560, 115, 35);

            //---- vimModeInput ----
            vimModeInput.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 20));
            vimModeInput.setForeground(Color.white);
            vimModeInput.setCaretColor(Color.white);
            vimModeInput.setBackground(Color.darkGray);
            vimModeInput.setBorder(null);
            vimModeInput.setDisabledTextColor(Color.white);
            vimModeInput.setEnabled(false);
            panel.add(vimModeInput);
            vimModeInput.setBounds(0, 510, 840, 45);

            //======== cmdScrollPane ========
            {
                cmdScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                cmdScrollPane.setBorder(null);

                //---- cmd ----
                cmd.setBackground(Color.black);
                cmd.setForeground(Color.white);
                cmd.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 18));
                cmd.setLineWrap(true);
                cmd.setCaretColor(Color.white);
                cmd.setBorder(null);
                cmdScrollPane.setViewportView(cmd);
            }
            panel.add(cmdScrollPane);
            cmdScrollPane.setBounds(0, 0, 825, 535);

            //======== vimScrollPane ========
            {
                vimScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                vimScrollPane.setBorder(null);

                //---- vim ----
                vim.setBackground(Color.black);
                vim.setForeground(Color.white);
                vim.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 18));
                vim.setLineWrap(true);
                vim.setBorder(null);
                vim.setCaretColor(Color.white);
                vimScrollPane.setViewportView(vim);
            }
            panel.add(vimScrollPane);
            vimScrollPane.setBounds(0, 0, 825, 510);

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
        setSize(840, 600);
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JPanel panel;
    public JTextField defaultInput;
    public JTextField vimModeInput;
    public JScrollPane cmdScrollPane;
    public JTextArea cmd;
    public JScrollPane vimScrollPane;
    public JTextArea vim;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
