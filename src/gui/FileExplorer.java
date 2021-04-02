/*
 * Created by JFormDesigner on Tue Mar 30 00:39:11 CST 2021
 */

package gui;

import javax.swing.border.*;

import file.DirectoryItem;
import file.DiskInode;
import file.FileType;
import file.Mode;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Vector;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * 文件系统资源管理器
 * 提供视窗方式操纵文件系统
 * @author ZJC
 */
public class FileExplorer extends JDialog {
    public static ImageIcon FOLD = new ImageIcon("./static/fold.png");
    public static ImageIcon UNFOLD = new ImageIcon("./static/unfold.png");
    public static ImageIcon TXT = new ImageIcon("./static/txt.png");
    public static ImageIcon OPEN = new ImageIcon("./static/open.png");
    public static ImageIcon CLOSE = new ImageIcon("./static/close.png");
    public static ImageIcon BACK = new ImageIcon("./static/back.png");
    public static ImageIcon FORWARD = new ImageIcon("./static/forward.png");

    public Dashboard dashboard;
    public DefaultMutableTreeNode root;
    public Vector<FileBox> fileList;
    public Vector<String> historyPath;
    public int historyPointer;

    public FileExplorer(Dashboard owner) {
        super(owner);
        this.dashboard = owner;
        this.changeRenderStyle();
        this.initComponents();
        this.initDataStucture();
    }

    /**
     * 刷新目录
     * @param node 目录结点
     */
    public void refreshDirectory(DefaultMutableTreeNode node) {
        try {
            // 获取该节点的完整文件路径
            TreeNode[] path = node.getPath();
            String filePath = "";
            for (int i = 0; i < path.length; i++) {
                filePath += path[i].toString();
                if (i != path.length - 1) {
                    filePath += "/";
                }
            }
            if (filePath.length() != 1) {
                filePath = filePath.substring(1);
            }
            // 获取inode
            DiskInode diskInode = dashboard.getManager().getFileSystem().getDiskInodeByPath(filePath);
            // 如果有目录项，则添加目录项
            if (diskInode.getType() == FileType.DIR && diskInode.getDirectoryItemList().size() > 2) {
                for (int i = 2; i < diskInode.getDirectoryItemList().size(); i++) {
                    // 添加子节点
                    String fileName = diskInode.getDirectoryItemList().get(i).getFileName();
                    DiskInode childDiskInode = dashboard.getManager().getFileSystem().getDiskInodeByPath(filePath.length() == 1 ? filePath + fileName : filePath + "/" + fileName);
                    DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(fileName) {
                        @Override
                        public boolean isLeaf() {
                            if (childDiskInode.getType() == FileType.DIR) {
                                return false;
                            } else {
                                return true;
                            }
                        }
                    };
                    node.add(childNode);
                    // 递归创建子目录
                    this.refreshDirectory(childNode);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 刷新历史路径
     * @param newPath 新路径
     */
    public void refreshHistory(String newPath) {
        this.historyPath.add(newPath);
        this.historyPointer = this.historyPath.size() - 1;
    }

    /**
     * 刷新当前路径
     */
    public void refreshPath() {
        this.path.setText(this.dashboard.getManager().getFileSystem().getCurrentDirPath());
    }

    /**
     *刷新文件详情
     * @param name 文件名
     * @param diskInode 对应inode
     */
    public void refreshFileDetail (String name, DiskInode diskInode) {
        this.fileName.setText(name);
        this.inode.setText("" + diskInode.getCurrentDir().getInodeNo());
        this.user.setText(diskInode.getUserId() == 0 ? "root" : "进程 " + diskInode.getUserId());
        this.group.setText(diskInode.getGroupId() == 0 ? "root" : "进程 " + diskInode.getGroupId());
        this.type.setText(diskInode.getType() == FileType.DIR ? "DIR" : "FILE");
        this.size.setText("" + diskInode.getFileSize());
        this.link.setText("" + diskInode.getHardLinkNum());
        this.createTime.setText("" + diskInode.getCreatedTime());
        this.updateTime.setText("" + diskInode.getLastUpdateTime());
        String mode = "";
        mode += (diskInode.getMode() & Mode.USER_READ) != 0 ? "r" : "-";
        mode += (diskInode.getMode() & Mode.USER_WRITE) != 0 ? "w" : "-";
        mode += (diskInode.getMode() & Mode.USER_EXEC) != 0 ? "x" : "-";
        mode += (diskInode.getMode() & Mode.GROUP_READ) != 0 ? "r" : "-";
        mode += (diskInode.getMode() & Mode.GROUP_WRITE) != 0 ? "w" : "-";
        mode += (diskInode.getMode() & Mode.GROUP_EXEC) != 0 ? "x" : "-";
        mode += (diskInode.getMode() & Mode.OTHER_READ) != 0 ? "r" : "-";
        mode += (diskInode.getMode() & Mode.OTHER_WRITE) != 0 ? "w" : "-";
        mode += (diskInode.getMode() & Mode.OTHER_EXEC) != 0 ? "x" : "-";
        this.mode.setText(mode);
    }

    /**
     * 刷新文件列表
     */
    public void refreshFileList() {
        this.fileList.clear();
        this.filePanel.removeAll();
        Vector<DirectoryItem> directoryItemList = this.dashboard.getManager().getFileSystem().getCurrentDir().getDirectoryItemList();
        this.num.setText("" + (directoryItemList.size() - 2));
        try {
            this.filePanel.setPreferredSize(new Dimension(798, 60 + 210 * ((directoryItemList.size() - 3) / 5) + 1));
            for (int i = 2; i < directoryItemList.size(); i++) {
                DiskInode diskInode = this.dashboard.getManager().getFileSystem().getDiskInodeMap().get("" + directoryItemList.get(i).getInodeNo());
                ImageIcon img = new ImageIcon(diskInode.getType() == FileType.DIR ? "./static/dir.png" : "./static/txt.png");
                FileBox fileBox = new FileBox(this, img, directoryItemList.get(i), diskInode);
                this.fileList.add(fileBox);
                this.filePanel.add(fileBox);
            }
            this.filePanel.updateUI();
            this.fileScrollPane.getViewport().add(this.filePanel);
            this.fileScrollPane.validate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 数据结构初始化
     */
    public void initDataStucture() {
        // 删除连接线
        this.directory.putClientProperty("JTree.lineStyle", "None");
        // 初始化路由历史
        this.historyPath = new Vector<>();
        // 配置路由按钮
        this.back.setIcon(BACK);
        this.back.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (historyPointer < 1) {
                    return;
                }
                dashboard.getManager().getFileSystem().cmd("cd " + historyPath.get(--historyPointer));
                refreshPath();
                refreshFileList();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                back.setBorder(new LineBorder(Color.GRAY));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                back.setBorder(null);
            }
        });
        this.forward.setIcon(FORWARD);
        this.forward.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (historyPointer >= historyPath.size() - 1) {
                    return;
                }
                dashboard.getManager().getFileSystem().cmd("cd " + historyPath.get(++historyPointer));
                refreshPath();
                refreshFileList();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                forward.setBorder(new LineBorder(Color.GRAY));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                forward.setBorder(null);
            }
        });
        // 添加目录选择事件
        this.directory.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
                if (selectedNode.isLeaf()) {
                    return;
                }
                String[] paths = e.getPath().toString().substring(1, e.getPath().toString().length() - 1).split(", ");
                String dirPath = "";
                for (int i = 0; i < paths.length; i++) {
                    dirPath += paths[i];
                    if (i != paths.length - 1) {
                        dirPath +=  "/";
                    }
                }
                if (dirPath.length() != 1) {
                    dirPath = dirPath.substring(1);
                }
                dashboard.getManager().getFileSystem().cmd("cd " + dirPath);
                refreshHistory(dashboard.getManager().getFileSystem().getCurrentDirPath());
                refreshPath();
                refreshFileList();
            }
        });
        // 添加右键点击事件
        this.filePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 右键弹出菜单
                if (e.getButton() == MouseEvent.BUTTON3) {
                    popupMenu.show(filePanel, e.getX(), e.getY());
                    popupMenu.setVisible(true);
                }
            }
        });
        this.createFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createFileNameInput.setText("");
                createFileDialog.setVisible(true);
            }
        });
        this.createDir.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createDirNameInput.setText("");
                createDirDialog.setVisible(true);
            }
        });
        // 新建文件对话框
        this.confirmFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = createFileNameInput.getText();
                if (name.length() == 0) {
                    JOptionPane.showMessageDialog(createFileDialog, "文件名不能为空！", "提示", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                for (int i = 0; i < fileList.size(); i++) {
                    if (fileList.get(i).directoryItem.getFileName().equals(name)) {
                        JOptionPane.showMessageDialog(createFileDialog, "文件名已存在！", "提示", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                dashboard.getManager().getFileSystem().cmd("touch " + name);
                dashboard.getManager().getFileSystem().cmd("close " + name);

                DefaultMutableTreeNode parentNode = getTreeNode(root, path.getText().split("/"));
                parentNode.add(new DefaultMutableTreeNode(name) {
                    @Override
                    public boolean isLeaf() {
                        return true;
                    }
                });
                directory.updateUI();

                refreshFileList();
                createFileDialog.setVisible(false);
            }
        });
        this.cancelFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createFileDialog.setVisible(false);
            }
        });
        // 新建目录对话框
        this.confirmDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = createDirNameInput.getText();
                if (name.length() == 0) {
                    JOptionPane.showMessageDialog(createDirDialog, "目录名不能为空！", "提示", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                for (int i = 0; i < fileList.size(); i++) {
                    if (fileList.get(i).directoryItem.getFileName().equals(name)) {
                        JOptionPane.showMessageDialog(createDirDialog, "文件名已存在！", "提示", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                dashboard.getManager().getFileSystem().cmd("mkdir " + name);
                dashboard.getManager().getFileSystem().cmd("close " + name);
                DefaultMutableTreeNode parentNode = getTreeNode(root, path.getText().split("/"));
                parentNode.add(new DefaultMutableTreeNode(name) {
                    @Override
                    public boolean isLeaf() {
                        return false;
                    }
                });
                directory.updateUI();

                refreshFileList();
                createDirDialog.setVisible(false);
            }
        });
        this.cancelDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createDirDialog.setVisible(false);
            }
        });
        // 添加文件列表
        this.fileList = new Vector<>();
    }

    /**
     * 初始化目录结构，在每次打开窗口时调用
     */
    public void refresh() {
        this.root = new DefaultMutableTreeNode("/");
        DefaultTreeModel model = new DefaultTreeModel(root);
        this.directory.setModel(model);
        this.refreshDirectory(this.root);
        this.directory.expandPath(new TreePath(root));
        this.refreshPath();
        this.refreshFileList();
        this.historyPath.clear();
        this.refreshHistory("/");
    }

    /**
     * 获取树节点
     * @param currentNode 当前节点
     * @param pathElements 寻找路径成分
     * @return 获取目标节点
     */
    public DefaultMutableTreeNode getTreeNode(DefaultMutableTreeNode currentNode, String[] pathElements) {
        if (pathElements.length == 0) {
            return currentNode;
        }
        if (pathElements[0].length() == 0) {
            pathElements = Arrays.copyOfRange(pathElements, 1, pathElements.length);
        }
        for (int i = 0; i < currentNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) currentNode.getChildAt(i);
            if (childNode.toString().equals(pathElements[0])) {
                return this.getTreeNode(childNode, Arrays.copyOfRange(pathElements, 1, pathElements.length));
            }
        }
        return null;
    }

    /**
     * 调整渲染样式
     */
    public void changeRenderStyle() {
        FOLD.setImage(FOLD.getImage().getScaledInstance(20, 20, Image.SCALE_DEFAULT));
        UNFOLD.setImage(UNFOLD.getImage().getScaledInstance(20, 20, Image.SCALE_DEFAULT));
        TXT.setImage(TXT.getImage().getScaledInstance(20, 20, Image.SCALE_DEFAULT));
        OPEN.setImage(OPEN.getImage().getScaledInstance(20, 20, Image.SCALE_DEFAULT));
        CLOSE.setImage(CLOSE.getImage().getScaledInstance(20, 20, Image.SCALE_DEFAULT));
        BACK.setImage(BACK.getImage().getScaledInstance(30, 30, Image.SCALE_DEFAULT));
        FORWARD.setImage(FORWARD.getImage().getScaledInstance(30, 30, Image.SCALE_DEFAULT));

        UIManager.put("Tree.collapsedIcon", FOLD);
        UIManager.put("Tree.expandedIcon", UNFOLD);
        UIManager.put("Tree.openIcon", OPEN);
        UIManager.put("Tree.closedIcon", CLOSE);
        UIManager.put("Tree.leafIcon", TXT);
    }

    public FileExplorer getFileExplorer() {
        return this;
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        pathPanel = new JPanel();
        back = new JLabel();
        forward = new JLabel();
        path = new JLabel();
        directoryPanel = new JPanel();
        directoryScrollPane = new JScrollPane();
        directory = new JTree();
        Detailpanel = new JPanel();
        fileNameLabel = new JLabel();
        fileName = new JLabel();
        inodeLabel = new JLabel();
        inode = new JLabel();
        userLabel = new JLabel();
        user = new JLabel();
        groupLabel = new JLabel();
        group = new JLabel();
        typeLabel = new JLabel();
        type = new JLabel();
        modeLabel = new JLabel();
        mode = new JLabel();
        sizeLabel = new JLabel();
        size = new JLabel();
        linkLabel = new JLabel();
        link = new JLabel();
        createTimeLabel = new JLabel();
        createTime = new JLabel();
        updateTimeLabel = new JLabel();
        updateTime = new JLabel();
        fileArea = new JPanel();
        fileScrollPane = new JScrollPane();
        filePanel = new JPanel();
        statePanel = new JPanel();
        numLabel = new JLabel();
        num = new JLabel();
        popupMenu = new JPopupMenu();
        createFile = new JMenuItem();
        createDir = new JMenuItem();
        createFileDialog = new JDialog();
        createFileName = new JLabel();
        createFileNameInput = new JTextField();
        confirmFileButton = new JButton();
        cancelFileButton = new JButton();
        createDirDialog = new JDialog();
        createDirName = new JLabel();
        createDirNameInput = new JTextField();
        confirmDirButton = new JButton();
        cancelDirButton = new JButton();

        //======== this ========
        setTitle("\u6587\u4ef6\u7cfb\u7edf\u8d44\u6e90\u7ba1\u7406\u5668");
        setModal(true);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== pathPanel ========
        {
            pathPanel.setMinimumSize(new Dimension(0, 0));
            pathPanel.setPreferredSize(new Dimension(0, 40));
            pathPanel.setBackground(Color.white);
            pathPanel.setBorder(new LineBorder(Color.lightGray));
            pathPanel.setLayout(null);

            //---- back ----
            back.setText("text");
            back.setIcon(null);
            back.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            pathPanel.add(back);
            back.setBounds(10, 5, 30, 30);

            //---- forward ----
            forward.setText("text");
            forward.setIcon(null);
            forward.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            pathPanel.add(forward);
            forward.setBounds(60, 5, 30, 30);

            //---- path ----
            path.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            path.setBorder(LineBorder.createGrayLineBorder());
            pathPanel.add(path);
            path.setBounds(120, 5, 1055, 30);
        }
        contentPane.add(pathPanel, BorderLayout.NORTH);

        //======== directoryPanel ========
        {
            directoryPanel.setPreferredSize(new Dimension(200, 0));
            directoryPanel.setLayout(new BorderLayout());

            //======== directoryScrollPane ========
            {

                //---- directory ----
                directory.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
                directoryScrollPane.setViewportView(directory);
            }
            directoryPanel.add(directoryScrollPane, BorderLayout.CENTER);
        }
        contentPane.add(directoryPanel, BorderLayout.WEST);

        //======== Detailpanel ========
        {
            Detailpanel.setPreferredSize(new Dimension(200, 0));
            Detailpanel.setBackground(Color.white);
            Detailpanel.setBorder(new LineBorder(Color.lightGray));
            Detailpanel.setLayout(null);

            //---- fileNameLabel ----
            fileNameLabel.setText("\u6587\u4ef6\u540d");
            fileNameLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(fileNameLabel);
            fileNameLabel.setBounds(10, 15, 75, 35);

            //---- fileName ----
            fileName.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(fileName);
            fileName.setBounds(90, 15, 100, 35);

            //---- inodeLabel ----
            inodeLabel.setText("inode\u7f16\u53f7");
            inodeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(inodeLabel);
            inodeLabel.setBounds(10, 55, 75, 35);

            //---- inode ----
            inode.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(inode);
            inode.setBounds(90, 55, 100, 35);

            //---- userLabel ----
            userLabel.setText("\u7528\u6237ID");
            userLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(userLabel);
            userLabel.setBounds(10, 95, 75, 35);

            //---- user ----
            user.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(user);
            user.setBounds(90, 95, 100, 35);

            //---- groupLabel ----
            groupLabel.setText("\u7528\u6237\u7ec4ID");
            groupLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(groupLabel);
            groupLabel.setBounds(10, 135, 75, 35);

            //---- group ----
            group.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(group);
            group.setBounds(90, 135, 100, 35);

            //---- typeLabel ----
            typeLabel.setText("\u6587\u4ef6\u7c7b\u578b");
            typeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(typeLabel);
            typeLabel.setBounds(10, 175, 75, 35);

            //---- type ----
            type.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(type);
            type.setBounds(90, 175, 100, 35);

            //---- modeLabel ----
            modeLabel.setText("\u8bbf\u95ee\u6743\u9650");
            modeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(modeLabel);
            modeLabel.setBounds(10, 215, 75, 35);

            //---- mode ----
            mode.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(mode);
            mode.setBounds(90, 215, 100, 35);

            //---- sizeLabel ----
            sizeLabel.setText("\u6587\u4ef6\u5927\u5c0f");
            sizeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(sizeLabel);
            sizeLabel.setBounds(10, 255, 75, 35);

            //---- size ----
            size.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(size);
            size.setBounds(90, 255, 100, 35);

            //---- linkLabel ----
            linkLabel.setText("\u786c\u94fe\u63a5\u6570");
            linkLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(linkLabel);
            linkLabel.setBounds(10, 295, 75, 35);

            //---- link ----
            link.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(link);
            link.setBounds(90, 295, 100, 35);

            //---- createTimeLabel ----
            createTimeLabel.setText("\u521b\u5efa\u65f6\u95f4");
            createTimeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(createTimeLabel);
            createTimeLabel.setBounds(10, 335, 75, 35);

            //---- createTime ----
            createTime.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(createTime);
            createTime.setBounds(90, 335, 100, 35);

            //---- updateTimeLabel ----
            updateTimeLabel.setText("\u66f4\u65b0\u65f6\u95f4");
            updateTimeLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(updateTimeLabel);
            updateTimeLabel.setBounds(10, 375, 75, 35);

            //---- updateTime ----
            updateTime.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            Detailpanel.add(updateTime);
            updateTime.setBounds(90, 375, 100, 35);
        }
        contentPane.add(Detailpanel, BorderLayout.EAST);

        //======== fileArea ========
        {
            fileArea.setLayout(new BorderLayout());

            //======== fileScrollPane ========
            {
                fileScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                fileScrollPane.setMaximumSize(new Dimension(798, 563));
                fileScrollPane.setPreferredSize(new Dimension(798, 563));

                //======== filePanel ========
                {
                    filePanel.setBackground(Color.white);
                    filePanel.setPreferredSize(new Dimension(798, 500));
                    filePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 30, 20));
                }
                fileScrollPane.setViewportView(filePanel);
            }
            fileArea.add(fileScrollPane, BorderLayout.CENTER);
        }
        contentPane.add(fileArea, BorderLayout.CENTER);

        //======== statePanel ========
        {
            statePanel.setPreferredSize(new Dimension(0, 40));
            statePanel.setBackground(Color.white);
            statePanel.setBorder(new LineBorder(Color.lightGray));
            statePanel.setLayout(null);

            //---- numLabel ----
            numLabel.setText("\u9879\u76ee\u6570\uff1a");
            numLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            statePanel.add(numLabel);
            numLabel.setBounds(10, 5, 70, 30);

            //---- num ----
            num.setText("0");
            num.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 16));
            statePanel.add(num);
            num.setBounds(70, 5, 70, 30);
        }
        contentPane.add(statePanel, BorderLayout.SOUTH);
        setSize(1200, 675);
        setLocationRelativeTo(getOwner());

        //======== popupMenu ========
        {

            //---- createFile ----
            createFile.setText("\u65b0\u5efa\u6587\u4ef6");
            createFile.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            popupMenu.add(createFile);

            //---- createDir ----
            createDir.setText("\u65b0\u5efa\u76ee\u5f55");
            createDir.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            popupMenu.add(createDir);
        }

        //======== createFileDialog ========
        {
            createFileDialog.setTitle("\u65b0\u5efa\u6587\u4ef6");
            createFileDialog.setAlwaysOnTop(true);
            createFileDialog.setModal(true);
            Container createFileDialogContentPane = createFileDialog.getContentPane();
            createFileDialogContentPane.setLayout(null);

            //---- createFileName ----
            createFileName.setText("\u6587\u4ef6\u540d");
            createFileName.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            createFileName.setHorizontalAlignment(SwingConstants.CENTER);
            createFileDialogContentPane.add(createFileName);
            createFileName.setBounds(15, 20, 55, 30);

            //---- createFileNameInput ----
            createFileNameInput.setBackground(Color.white);
            createFileNameInput.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            createFileDialogContentPane.add(createFileNameInput);
            createFileNameInput.setBounds(75, 15, 255, 40);

            //---- confirmFileButton ----
            confirmFileButton.setText("\u786e\u5b9a");
            confirmFileButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            createFileDialogContentPane.add(confirmFileButton);
            confirmFileButton.setBounds(new Rectangle(new Point(75, 65), confirmFileButton.getPreferredSize()));

            //---- cancelFileButton ----
            cancelFileButton.setText("\u53d6\u6d88");
            cancelFileButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            createFileDialogContentPane.add(cancelFileButton);
            cancelFileButton.setBounds(195, 65, 78, 30);

            { // compute preferred size
                Dimension preferredSize = new Dimension();
                for(int i = 0; i < createFileDialogContentPane.getComponentCount(); i++) {
                    Rectangle bounds = createFileDialogContentPane.getComponent(i).getBounds();
                    preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                    preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                }
                Insets insets = createFileDialogContentPane.getInsets();
                preferredSize.width += insets.right;
                preferredSize.height += insets.bottom;
                createFileDialogContentPane.setMinimumSize(preferredSize);
                createFileDialogContentPane.setPreferredSize(preferredSize);
            }
            createFileDialog.setSize(360, 150);
            createFileDialog.setLocationRelativeTo(createFileDialog.getOwner());
        }

        //======== createDirDialog ========
        {
            createDirDialog.setTitle("\u65b0\u5efa\u76ee\u5f55");
            createDirDialog.setAlwaysOnTop(true);
            createDirDialog.setModal(true);
            Container createDirDialogContentPane = createDirDialog.getContentPane();
            createDirDialogContentPane.setLayout(null);

            //---- createDirName ----
            createDirName.setText("\u76ee\u5f55\u540d");
            createDirName.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            createDirName.setHorizontalAlignment(SwingConstants.CENTER);
            createDirDialogContentPane.add(createDirName);
            createDirName.setBounds(15, 20, 55, 30);

            //---- createDirNameInput ----
            createDirNameInput.setBackground(Color.white);
            createDirNameInput.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            createDirDialogContentPane.add(createDirNameInput);
            createDirNameInput.setBounds(75, 15, 255, 40);

            //---- confirmDirButton ----
            confirmDirButton.setText("\u786e\u5b9a");
            confirmDirButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            createDirDialogContentPane.add(confirmDirButton);
            confirmDirButton.setBounds(new Rectangle(new Point(75, 65), confirmDirButton.getPreferredSize()));

            //---- cancelDirButton ----
            cancelDirButton.setText("\u53d6\u6d88");
            cancelDirButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            createDirDialogContentPane.add(cancelDirButton);
            cancelDirButton.setBounds(195, 65, 78, 30);

            { // compute preferred size
                Dimension preferredSize = new Dimension();
                for(int i = 0; i < createDirDialogContentPane.getComponentCount(); i++) {
                    Rectangle bounds = createDirDialogContentPane.getComponent(i).getBounds();
                    preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                    preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                }
                Insets insets = createDirDialogContentPane.getInsets();
                preferredSize.width += insets.right;
                preferredSize.height += insets.bottom;
                createDirDialogContentPane.setMinimumSize(preferredSize);
                createDirDialogContentPane.setPreferredSize(preferredSize);
            }
            createDirDialog.setSize(360, 150);
            createDirDialog.setLocationRelativeTo(createDirDialog.getOwner());
        }
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    public JPanel pathPanel;
    public JLabel back;
    public JLabel forward;
    public JLabel path;
    public JPanel directoryPanel;
    public JScrollPane directoryScrollPane;
    public JTree directory;
    public JPanel Detailpanel;
    public JLabel fileNameLabel;
    public JLabel fileName;
    public JLabel inodeLabel;
    public JLabel inode;
    public JLabel userLabel;
    public JLabel user;
    public JLabel groupLabel;
    public JLabel group;
    public JLabel typeLabel;
    public JLabel type;
    public JLabel modeLabel;
    public JLabel mode;
    public JLabel sizeLabel;
    public JLabel size;
    public JLabel linkLabel;
    public JLabel link;
    public JLabel createTimeLabel;
    public JLabel createTime;
    public JLabel updateTimeLabel;
    public JLabel updateTime;
    public JPanel fileArea;
    public JScrollPane fileScrollPane;
    public JPanel filePanel;
    public JPanel statePanel;
    public JLabel numLabel;
    public JLabel num;
    private JPopupMenu popupMenu;
    private JMenuItem createFile;
    private JMenuItem createDir;
    private JDialog createFileDialog;
    private JLabel createFileName;
    private JTextField createFileNameInput;
    private JButton confirmFileButton;
    private JButton cancelFileButton;
    private JDialog createDirDialog;
    private JLabel createDirName;
    private JTextField createDirNameInput;
    private JButton confirmDirButton;
    private JButton cancelDirButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables


    public DefaultMutableTreeNode getRoot() {
        return root;
    }

    public void setRoot(DefaultMutableTreeNode root) {
        this.root = root;
    }
}
