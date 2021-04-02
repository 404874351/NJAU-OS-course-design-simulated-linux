/*
 * Created by JFormDesigner on Tue Mar 30 14:07:06 CST 2021
 */

package gui;

import file.DirectoryItem;
import file.DiskInode;
import file.FileType;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * 文件展示盒
 * @author ZJC
 */
public class FileBox extends JPanel {
    public FileExplorer fileExplorer;
    public ImageIcon img;
    public DirectoryItem directoryItem;
    public DiskInode diskInode;
    public String fileData;

    public FileBox(FileExplorer fileExplorer, ImageIcon img, DirectoryItem directoryItem, DiskInode diskInode) {
        this.fileExplorer = fileExplorer;
        this.img = img;
        this.directoryItem = directoryItem;
        this.diskInode = diskInode;
        this.fileData = "";

        initComponents();
        // 显示文件信息
        img.setImage(img.getImage().getScaledInstance(100, 100, Image.SCALE_DEFAULT));
        this.imgLabel.setIcon(img);
        this.nameInput.setText(directoryItem.getFileName());
        // 添加文件名输入框事件
        nameInput.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String newName = nameInput.getText();
                if (newName.length() == 0) {
                    JOptionPane.showMessageDialog(fileExplorer, "目录名不能为空！", "提示", JOptionPane.ERROR_MESSAGE);
                    nameInput.setText(directoryItem.getFileName());
                    nameInput.setEditable(false);
                    return;
                }
                for (int i = 0; i < fileExplorer.fileList.size(); i++) {
                    if (fileExplorer.fileList.get(i) == getFileBox()) {
                        continue;
                    }
                    if (fileExplorer.fileList.get(i).directoryItem.getFileName().equals(newName)) {
                        JOptionPane.showMessageDialog(fileExplorer, "文件名已存在！", "提示", JOptionPane.ERROR_MESSAGE);
                        nameInput.setText(directoryItem.getFileName());
                        nameInput.setEditable(false);
                        return;
                    }
                }
                // 修改目录
                DefaultMutableTreeNode currentNode = fileExplorer.getTreeNode(
                        fileExplorer.root,
                        (fileExplorer.path.getText() + directoryItem.getFileName()).split("/")
                );
                currentNode.setUserObject(nameInput.getText());
                DefaultTreeModel model = (DefaultTreeModel) fileExplorer.directory.getModel();
                fileExplorer.directory.updateUI();

                // 修改文件显示盒
                directoryItem.setFileName(nameInput.getText());
                nameInput.setEditable(false);
            }
        });
        nameInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // 点击回车，则进行保存
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    nameInput.transferFocus();
                }
            }
        });
        // 添加弹出菜单功能
        this.open.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (diskInode.getType() == FileType.DIR) {
                    // 进入目录
                    fileExplorer.dashboard.getManager().getFileSystem().cmd("cd " + fileExplorer.path.getText() + directoryItem.getFileName());
                    fileExplorer.refreshPath();
                    fileExplorer.refreshFileList();
                } else if (diskInode.getType() == FileType.FILE) {
                    // 打开编辑器
                    fileData = fileExplorer.dashboard.getManager().getFileSystem().getFileData(fileExplorer.path.getText() + directoryItem.getFileName());
                    editor.setText(fileData);
                    editorDialog.setVisible(true);
                }
            }
        });
        this.rename.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nameInput.setEditable(true);
                nameInput.requestFocus();
                nameInput.selectAll();
            }
        });
        this.delete.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (diskInode.getType() == FileType.DIR) {
                    fileExplorer.dashboard.getManager().getFileSystem().cmd("rmdir " + directoryItem.getFileName());
                } else if (diskInode.getType() == FileType.FILE) {
                    fileExplorer.dashboard.getManager().getFileSystem().cmd("rm " + directoryItem.getFileName());
                }

                DefaultMutableTreeNode currentNode = fileExplorer.getTreeNode(
                        fileExplorer.root,
                        (fileExplorer.path.getText() + directoryItem.getFileName()).split("/")
                );
                DefaultTreeModel model = (DefaultTreeModel) fileExplorer.directory.getModel();
                model.removeNodeFromParent(currentNode);
                fileExplorer.directory.updateUI();

                fileExplorer.refreshFileList();
            }
        });
        // 添加鼠标事件
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    // 右键打开弹出菜单
                    popupMenu.show(getFileBox(), e.getX(), e.getY());
                    popupMenu.setVisible(true);
                    return;
                }
                if (e.getClickCount() == 1) {
                    // 单击显示文件信息
                    fileExplorer.refreshFileDetail(directoryItem.getFileName(), diskInode);
                } else if (e.getClickCount() == 2) {
                    // 双击打开文件
                    if (diskInode.getType() == FileType.DIR) {
                        // 进入目录
                        fileExplorer.dashboard.getManager().getFileSystem().cmd("cd " + fileExplorer.path.getText() + directoryItem.getFileName());
                        fileExplorer.refreshPath();
                        fileExplorer.refreshFileList();
                    } else if (diskInode.getType() == FileType.FILE) {
                        // 打开编辑器
                        fileData = fileExplorer.dashboard.getManager().getFileSystem().getFileData(fileExplorer.path.getText() + directoryItem.getFileName());
                        editor.setText(fileData);
                        editorDialog.setVisible(true);
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(new Color(186, 192, 255));
                nameInput.setBackground(new Color(186, 192, 255));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(new Color(255, 255, 255));
                nameInput.setBackground(new Color(255, 255, 255));
            }
        });
        editorDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (editor.getText().equals(fileData)) {
                    return;
                }
                int res = JOptionPane.showConfirmDialog(editorDialog, "是否保存修改", "提示", JOptionPane.YES_NO_OPTION);
                if (res == JOptionPane.YES_OPTION) {
                    fileExplorer.dashboard.getManager().getFileSystem().writeFileData(fileExplorer.path.getText() + directoryItem.getFileName(), editor.getText());
                    editorDialog.setVisible(false);
                } else {
                    editorDialog.setVisible(false);
                }

            }
        });
    }

    public FileBox getFileBox() {
        return this;
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        imgLabel = new JLabel();
        nameInput = new JTextField();
        popupMenu = new JPopupMenu();
        open = new JMenuItem();
        rename = new JMenuItem();
        delete = new JMenuItem();
        editorDialog = new JDialog();
        editorScrollPane = new JScrollPane();
        editor = new JTextArea();

        //======== this ========
        setPreferredSize(new Dimension(120, 150));
        setBackground(Color.white);
        setLayout(null);

        //---- imgLabel ----
        imgLabel.setText("text");
        imgLabel.setBackground(new Color(255, 255, 255, 0));
        add(imgLabel);
        imgLabel.setBounds(10, 10, 100, 100);

        //---- nameInput ----
        nameInput.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        nameInput.setBackground(Color.white);
        nameInput.setCaretColor(Color.black);
        nameInput.setDisabledTextColor(Color.black);
        nameInput.setBorder(null);
        nameInput.setHorizontalAlignment(SwingConstants.CENTER);
        nameInput.setEditable(false);
        add(nameInput);
        nameInput.setBounds(10, 110, 100, nameInput.getPreferredSize().height);

        setPreferredSize(new Dimension(120, 150));

        //======== popupMenu ========
        {

            //---- open ----
            open.setText("\u6253\u5f00");
            open.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            popupMenu.add(open);

            //---- rename ----
            rename.setText("\u91cd\u547d\u540d");
            rename.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            popupMenu.add(rename);

            //---- delete ----
            delete.setText("\u5220\u9664");
            delete.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            popupMenu.add(delete);
        }

        //======== editorDialog ========
        {
            editorDialog.setTitle("\u6587\u672c\u7f16\u8f91\u5668");
            editorDialog.setModal(true);
            Container editorDialogContentPane = editorDialog.getContentPane();
            editorDialogContentPane.setLayout(new BorderLayout());

            //======== editorScrollPane ========
            {
                editorScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

                //---- editor ----
                editor.setBackground(Color.white);
                editor.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 18));
                editor.setLineWrap(true);
                editorScrollPane.setViewportView(editor);
            }
            editorDialogContentPane.add(editorScrollPane, BorderLayout.CENTER);
            editorDialog.setSize(550, 500);
            editorDialog.setLocationRelativeTo(editorDialog.getOwner());
        }
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JLabel imgLabel;
    private JTextField nameInput;
    private JPopupMenu popupMenu;
    private JMenuItem open;
    private JMenuItem rename;
    private JMenuItem delete;
    private JDialog editorDialog;
    private JScrollPane editorScrollPane;
    private JTextArea editor;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
