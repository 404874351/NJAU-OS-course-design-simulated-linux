package kernel;

import file.*;
import hardware.ExternalMem;
import hardware.InternalMem;
import os.Manager;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

/**
 * 文件系统
 *
 * 管理外存引导块、超级块、inode区，以及相关的文件操作
 *
 * @author ZJC
 */
public class FileSystem {
    /**
     * 块设备号，即本硬盘编号
     */
    public static final int DEVICE_NO = 0;
    /**
     * inode大小 单位：B
     */
    public static final int INODE_SIZE = 32;
    /**
     * 文件默认权限 775
     */
    public static final int DEFAULT_MODE = Mode.USER_READ | Mode.USER_WRITE | Mode.USER_EXEC |
            Mode.GROUP_READ | Mode.GROUP_WRITE | Mode.GROUP_EXEC |
            Mode.OTHER_READ | Mode.OTHER_EXEC;
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 活动inode列表
     */
    private Vector<ActivityInode> activityInodeList;

    /**
     * 系统打开文件表
     */
    private Vector<SystemFileItem> systemOpenFileTable;
    /**
     * 磁盘 inode表
     */
    private HashMap<String, DiskInode> diskInodeMap;
    /**
     * 超级块
     */
    private SuperBlock superBlock;
    /**
     * 根目录
     */
    private DiskInode rootDir;
    /**
     * 当前目录
     */
    private DiskInode currentDir;
    /**
     * 当前目录路径
     */
    private String currentDirPath;
    /**
     * 是否root用户（具有最高权限）
     */
    private boolean isRoot;
    /**
     * root用户打开文件表
     */
    private Vector<UserFileItem> rootOpenFileTable;
    /**
     * 打开文件计数，用以分配 fd
     */
    private int openFileCount;
    /**
     * 正在操作文件系统的PCB
     */
    private PCB userOperatePCB;

    public FileSystem(Manager manager) {
        this.manager                = manager;
        this.activityInodeList      = new Vector<>();
        this.systemOpenFileTable    = new Vector<>();
        this.diskInodeMap           = new HashMap<>();
        this.isRoot                 = false;
        this.rootOpenFileTable      = new Vector<>();
        this.openFileCount          = 0;
        this.userOperatePCB         = null;
        this.init();

        this.manager.getDashboard().consoleSuccess("文件系统初始化完成");
    }

    /**
     * 文件系统初始化
     */
    public void init() {
        // 初始化超级块
        this.initSuperBlock();
        // 初始化根目录
        this.initRootDir();
        // 初始化基本目录层次
        this.initBaseDir();

    }

    /**
     * 初始化超级块
     */
    public void initSuperBlock() {
        this.superBlock = new SuperBlock(
                this,
                ExternalMem.INODE_AREA_BLOCK_NUM,
                ExternalMem.CYLINDER_NUM * ExternalMem.TRACK_NUM * ExternalMem.SECTOR_NUM,
                ExternalMem.STORE_AREA_BLOCK_NUM,
                ExternalMem.INODE_AREA_BLOCK_NUM * ExternalMem.SECTOR_SIZE / INODE_SIZE
        );
        // 保存到外存 #1
        this.superBlock.saveToDisk();
    }

    /**
     * 初始化根目录
     */
    public void initRootDir() {
        int freeInodeIndex = this.superBlock.allocateInode();
        this.rootDir = new DiskInode(
                this,
                0,
                0,
                DEFAULT_MODE,
                FileType.DIR,
                0
        );
        // 添加当前目录、父目录
        this.rootDir.addCurrentDir(freeInodeIndex);
        this.rootDir.addParentDir(freeInodeIndex);
        // 保存到磁盘inode区
        this.rootDir.saveToDisk(freeInodeIndex);
        // 添加到磁盘inode列表
        this.diskInodeMap.put("" + freeInodeIndex, this.rootDir);

        // 设置当前目录、根目录等信息
        this.currentDir         = this.rootDir;
        this.currentDirPath     = "/";
    }

    /**
     * 初始化基本目录结构
     */
    public void initBaseDir() {
        String[] baseDirList = new String[]{
                "dev",
                "usr",
                "lib",
                "etc",
                "home",
                "var"
        };
        for (int i = 0; i < baseDirList.length; ++i) {
            int freeInodeIndex = this.superBlock.allocateInode();
            DiskInode diskInode = new DiskInode(
                    this,
                    0,
                    0,
                    DEFAULT_MODE,
                    FileType.DIR,
                    0
            );
            // 添加当前目录、父目录
            diskInode.addCurrentDir(freeInodeIndex);
            diskInode.addParentDir(this.rootDir.getCurrentDir().getInodeNo());
            // 保存到磁盘inode区
            diskInode.saveToDisk(freeInodeIndex);
            // 添加到磁盘inode表
            this.diskInodeMap.put("" + freeInodeIndex, diskInode);
            // 为父节点添加目录项
            this.rootDir.addDir(freeInodeIndex, baseDirList[i]);
        }
    }

    /**
     * 根据路径寻找目标文件的磁盘inode
     * @param filePath 路径
     * @return 磁盘inode
     */
    public synchronized DiskInode getDiskInodeByPath(String filePath) throws Exception{
        // 根目录，则直接返回根目录
        if (filePath.equals("/")) {
            return this.rootDir;
        }

        DiskInode currentInode;
        // 判断路径类型
        if (filePath.startsWith("/")) {
            // 如果以 / 开头，则以绝对路径作为当前路径
            currentInode = this.rootDir;
            filePath = "." + filePath;
        } else {
            // 相对路径
            currentInode = this.currentDir;
        }
        // 拆分目录层次
        String[] pathElements = filePath.split("/");
        // 层层深入
        for (int i = 0; i < pathElements.length; ++i) {
            int directoryItemIndex = currentInode.findDirectoryItem(pathElements[i]);
            // 如果当前目录下不存在指定文件或目录，则直接返回
            if (directoryItemIndex == -1) {
                return null;
            }
            // 如果目录没有执行权限，则抛出异常
            if (currentInode.getType() == FileType.DIR && !this.hasMode(currentInode, Mode.USER_EXEC | Mode.GROUP_EXEC | Mode.OTHER_EXEC)) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("权限不足，无法进入 " + pathElements[i]);
                throw new Exception();
            }
            // 如果将文件当作目录使用，则抛出异常
            if (currentInode.getType() == FileType.FILE && i != pathElements.length - 1) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("无效目录：" + pathElements[i]);
                throw new Exception();
            }
            // 存在且可执行，则设置当前inode
            currentInode = this.diskInodeMap.get("" + currentInode.getDirectoryItemList().get(directoryItemIndex).getInodeNo());
        }
        return currentInode;
    }

    /**
     * 由磁盘inode获取绝对路径
     * @param diskInode 磁盘inode
     * @return 绝对路径
     */
    public synchronized String getAbsolutePathByDiskInode(DiskInode diskInode) {
        String path = "/";
        DiskInode parentDir;
        DiskInode currentDir = diskInode;
        int currentDirInodeNo;
        while (currentDir.getParentDir().getInodeNo() != currentDir.getCurrentDir().getInodeNo()) {
            currentDirInodeNo = currentDir.getCurrentDir().getInodeNo();
            parentDir = this.diskInodeMap.get("" + currentDir.getParentDir().getInodeNo());
            path = "/" + parentDir.getDirectoryItemList().get(parentDir.findDirectoryItem(currentDirInodeNo)).getFileName() + path;
            currentDir = parentDir;
        }
        return path;
    }

    /**
     * 有磁盘inode获取inode编号
     * @param diskInode 磁盘inode
     * @return inode编号
     */
    public synchronized int getInodeNoByDiskInode(DiskInode diskInode) {
        return diskInode.getCurrentDir().getInodeNo();
    }

    /**
     * 由inode编号获取活动inode
     * @param inodeNo inode编号
     * @return 活动inode
     */
    public synchronized ActivityInode getActivityInodeByInodeNo(int inodeNo) {
        Iterator<ActivityInode> iterator = this.activityInodeList.iterator();
        while (iterator.hasNext()) {
            ActivityInode activityInode = iterator.next();
            if (activityInode.getInodeNo() == inodeNo) {
                return activityInode;
            }
        }
        return null;
    }

    /**
     * 由fd获取用户打开文件表表项
     * @param userOpenFileTable 用户打开文件表
     * @param fd 文件描述符
     * @return 用户打开文件表表项
     */
    public synchronized UserFileItem getUserFileItemByFd(Vector<UserFileItem> userOpenFileTable, int fd) {
        Iterator<UserFileItem> iterator = userOpenFileTable.iterator();
        while (iterator.hasNext()) {
            UserFileItem userFileItem = iterator.next();
            if (userFileItem.getFd() == fd) {
                return userFileItem;
            }
        }
        return null;
    }

    /**
     * 添加root用户打开文件表
     * @param fp 系统打开文件表指针
     * @return root用户打开文件表长度
     */
    public synchronized int addRootOpenFileItem(SystemFileItem fp) {
        this.rootOpenFileTable.add(new UserFileItem(this.openFileCount, fp));
        return this.openFileCount++;
    }

    /**
     * 删除root用户打开文件表表项
     * @param fd 文件描述符
     */
    public synchronized void removeRootOpenFileItem(int fd) {
        Iterator<UserFileItem> iterator = this.rootOpenFileTable.iterator();
        while (iterator.hasNext()) {
            UserFileItem userFileItem = iterator.next();
            if (userFileItem.getFd() == fd) {
               iterator.remove();
               return;
            }
        }
    }

    /**
     * 查看inode是否有mode权限
     * @param inode 磁盘inode
     * @param mode 文件权限
     * @return 是否有权限
     */
    public synchronized boolean hasMode(DiskInode inode, int mode) {
        return (inode.getMode() & mode) != 0;
    }

    /**
     * 格式化文件路径
     * @param filePath 文件路径
     * @return 格式化路径
     */
    public String formatPath(String filePath) {
        // 去除路径中所有空格
        String formatPath = filePath.replaceAll(" ","");
        // 去除结尾的无效 /
        while (formatPath.endsWith("/") && !formatPath.equals("/")) {
            formatPath = formatPath.substring(0, formatPath.length() - 1);
        }
        return formatPath;
    }

    /**
     * 创建文件
     * @param filePath 文件路径
     * @param mode 文件权限
     * @param recurse 递归模式
     */
    public synchronized void createFile(String filePath, int mode, boolean recurse) {
        // 路径格式化
        String formatPath = this.formatPath(filePath);
        try {
            DiskInode targetDiskInode = this.getDiskInodeByPath(formatPath);
            // 需要创建的文件已存在，则模拟进行一次更新操作
            if (targetDiskInode != null) {
                targetDiskInode.setLastUpdateTime(this.manager.getClock().getCurrentTime());
                return;
            }
        } catch (Exception e) {
            // 检索目录出错
            e.printStackTrace();
            return;
        }

        // 记录下递归时的当前inode信息
        DiskInode currentInode;
        int currentInodeNo;
        String currentFilePath = ".";
        // 判断路径类型
        if (formatPath.startsWith("/")) {
            // 如果以 / 开头，则以绝对路径作为当前路径
            currentInode = this.rootDir;
            currentInodeNo = this.rootDir.getCurrentDir().getInodeNo();
            formatPath = "." + formatPath;
        } else {
            // 相对路径
            currentInode = this.currentDir;
            currentInodeNo = this.currentDir.getCurrentDir().getInodeNo();
        }
        // 拆分目录层次
        String[] pathElements = formatPath.split("/");
        // 递归创建文件
        for (int i = 0; i < pathElements.length; ++i) {
            int directoryItemIndex = currentInode.findDirectoryItem(pathElements[i]);
            int freeInodeIndex = 0;
            String fileName = pathElements[i];
            DiskInode diskInode = null;
            // 如果当前目录下不存在指定文件或目录，则创建
            if (directoryItemIndex == -1) {
                if (!recurse && i != pathElements.length - 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("不允许在空目录下创建文件！\n");
                    return;
                }
                freeInodeIndex = this.superBlock.allocateInode();
                diskInode = new DiskInode(
                        this,
                        this.isRoot ? 0 : this.userOperatePCB.getId(),
                        this.isRoot ? 0 : this.userOperatePCB.getId(),
                        mode != -1 ? mode : DEFAULT_MODE,
                        i != pathElements.length - 1 ? FileType.DIR : FileType.FILE,
                        this.manager.getClock().getCurrentTime()
                );
                // 添加当前目录、父目录
                diskInode.addCurrentDir(freeInodeIndex);
                diskInode.addParentDir(currentInodeNo);
                // 保存到磁盘inode区
                diskInode.saveToDisk(freeInodeIndex);
                // 添加到磁盘inode表
                this.diskInodeMap.put("" + freeInodeIndex, diskInode);
                // 为当前节点添加目录项
                currentInode.addDir(freeInodeIndex, fileName);
                // 执行打开操作
                this.open(currentFilePath + "/" + fileName, Flag.FILE_READ | Flag.FILE_WRITE);
            }
            // 设置当前inode信息
            DiskInode oldInode = currentInode;
            currentInode = directoryItemIndex != -1 ?
                    this.diskInodeMap.get("" + oldInode.getDirectoryItemList().get(directoryItemIndex).getInodeNo()) : diskInode;
            currentInodeNo = directoryItemIndex != -1 ?
                    oldInode.getDirectoryItemList().get(directoryItemIndex).getInodeNo() : freeInodeIndex;
            currentFilePath += "/" + fileName;
        }
    }

    /**
     * 创建目录
     * @param dirPath 目录路径
     * @param mode 文件权限
     * @param recurse 递归模式
     */
    public synchronized void createDir(String dirPath, int mode, boolean recurse) {
        // 路径格式化
        String formatPath = this.formatPath(dirPath);
        try {
            DiskInode targetDiskInode = this.getDiskInodeByPath(formatPath);
            // 需要创建的目录已存在，则直接返回
            if (targetDiskInode != null) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("文件或目录已存在！\n");
                return;
            }
        } catch (Exception e) {
            // 检索目录出错
            e.printStackTrace();
            return;
        }

        // 记录下递归时的当前inode信息
        DiskInode currentInode;
        int currentInodeNo;
        String currentFilePath;
        // 判断路径类型
        if (formatPath.startsWith("/")) {
            // 如果以 / 开头，则以绝对路径作为当前路径
            currentInode = this.rootDir;
            currentInodeNo = this.rootDir.getCurrentDir().getInodeNo();
            currentFilePath = "";
            formatPath = "." + formatPath;
        } else {
            // 相对路径
            currentInode = this.currentDir;
            currentInodeNo = this.currentDir.getCurrentDir().getInodeNo();
            currentFilePath = ".";
        }
        // 拆分目录层次
        String[] pathElements = formatPath.split("/");

        // 创建文件，有递归和非递归两种选择
        for (int i = 0; i < pathElements.length; ++i) {
            int directoryItemIndex = currentInode.findDirectoryItem(pathElements[i]);
            int freeInodeIndex = 0;
            String fileName = pathElements[i];
            DiskInode diskInode = null;
            // 如果当前目录下不存在指定目录，则创建
            if (directoryItemIndex == -1) {
                if (!recurse && i != pathElements.length - 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("请使用 -p 方式创建目录！\n");
                    return;
                }
                freeInodeIndex = this.superBlock.allocateInode();
                diskInode = new DiskInode(
                        this,
                        this.isRoot ? 0 : this.userOperatePCB.getId(),
                        this.isRoot ? 0 : this.userOperatePCB.getId(),
                        mode != -1 ? mode : DEFAULT_MODE,
                        FileType.DIR,
                        this.manager.getClock().getCurrentTime()
                );
                // 添加当前目录、父目录
                diskInode.addCurrentDir(freeInodeIndex);
                diskInode.addParentDir(currentInodeNo);
                // 保存到磁盘inode区
                diskInode.saveToDisk(freeInodeIndex);
                // 添加到磁盘inode表
                this.diskInodeMap.put("" + freeInodeIndex, diskInode);
                // 为当前节点添加目录项
                currentInode.addDir(freeInodeIndex, fileName);
                // 执行打开操作
                this.open(currentFilePath + "/" + fileName, Flag.FILE_READ | Flag.FILE_WRITE);
            }

            // 设置当前inode信息
            DiskInode oldInode = currentInode;
            currentInode = directoryItemIndex != -1 ?
                    this.diskInodeMap.get("" + oldInode.getDirectoryItemList().get(directoryItemIndex).getInodeNo()) : diskInode;
            currentInodeNo = directoryItemIndex != -1 ?
                    oldInode.getDirectoryItemList().get(directoryItemIndex).getInodeNo() : freeInodeIndex;
            currentFilePath += "/" + fileName;
        }

    }

    /**
     * 打开一个已经创建的文件
     *
     * @param filePath 文件路径
     * @param mode 打开方式
     * @return fd
     */
    public synchronized int open(String filePath, int mode) {
        // 路径格式化
        String formatPath = this.formatPath(filePath);
        try {
            DiskInode targetDiskInode = this.getDiskInodeByPath(formatPath);
            // 需要打开的文件不存在，则直接返回
            if (targetDiskInode == null) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("文件不存在！\n");
                return -1;
            }
            // 读方式打开，但没有读权限，则直接返回
            if ((mode & Flag.FILE_READ) != 0 &&
                    (targetDiskInode.getMode() & (Mode.USER_READ | Mode.GROUP_READ | Mode.OTHER_READ)) == 0) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("没有读权限！\n");
                return -1;
            }
            // 写方式打开，但没有写权限，则直接返回
            if ((mode & Flag.FILE_WRITE) != 0 &&
                    (targetDiskInode.getMode() & (Mode.USER_WRITE | Mode.GROUP_WRITE | Mode.OTHER_WRITE)) == 0) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("没有写权限！\n");
                return -1;
            }
            // 文件存在且有对应权限，创建 <用户打开文件表-系统打开文件表-活动inode> 结构
            int inodeNo = this.getInodeNoByDiskInode(targetDiskInode);
            // 如果已有此文件的活动inode，则引用数 +1，否则重新创建
            ActivityInode activityInode = this.getActivityInodeByInodeNo(inodeNo);
            if (activityInode != null) {
                activityInode.increaseReferenceCount();
            } else {
                activityInode = new ActivityInode(this, DEVICE_NO, inodeNo, 1);
                this.activityInodeList.add(activityInode);
            }
            // 添加系统打开文件表，指针偏移为 0
            SystemFileItem systemFileItem = new SystemFileItem(mode, 1, 0, activityInode);
            this.systemOpenFileTable.add(systemFileItem);
            // 添加用户打开文件表
            int fd;
            if (this.isRoot) {
                fd = this.addRootOpenFileItem(systemFileItem);
            } else {
                fd = this.userOperatePCB.addOpenFileItem(systemFileItem);
            }
            return fd;

        } catch (Exception e) {
            // 检索目录出错
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * 关闭文件
     * @param fd 文件描述符
     */
    public synchronized void close(int fd) {
        // 由fd找到用户打开文件表项，再找到系统打开文件表项，然后释放用户打开文件表项
        SystemFileItem fp;
        if (isRoot) {
            fp = this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp();
            this.removeRootOpenFileItem(fd);
        } else {
            fp = this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
            this.userOperatePCB.removeOpenFileItem(fd);
        }
        // 系统打开文件表项 count -1；如果此时count非零，则返回；否则释放该表项，并找到对应活动inode
        fp.decreaseCount();
        if (fp.getCount() != 0) {
            return;
        }
        ActivityInode activityInode = fp.getInode();
        this.systemOpenFileTable.remove(fp);
        // 活动inode referenceCount -1；如果此时referenceCount非零，则返回；否则释放活动inode，并将更新内容写回磁盘inode
        activityInode.decreaseReferenceCount();
        if (activityInode.getReferenceCount() != 0) {
            return;
        }
        activityInode.writeBack();
        this.activityInodeList.remove(activityInode);
    }

    /**
     * 读文件
     * @param fd 文件描述符
     * @param buf 数据区首地址 (物理地址)
     * @param count 传送字节数
     * @return 实际读取长度
     */
    public synchronized int read(int fd, int buf, int count) {
        // 获取系统打开文件表项
        SystemFileItem systemFileItem = this.isRoot ?
                this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp() :
                this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
        // 没有读权限，则直接返回
        if ((systemFileItem.getFlag() & Flag.FILE_READ) == 0) {
            this.manager.getDashboard().fileSystemCommander.cmd.append("非读方式打开，读文件失败！\n");
            return 0;
        }
        // 读取长度为 0，则直接返回
        if (count == 0) {
            return 0;
        }
        // 获取磁盘inode
        DiskInode diskInode = this.diskInodeMap.get("" + systemFileItem.getInode().getInodeNo());
        // 文件为目录，则直接返回
        if (diskInode.getType() != FileType.FILE) {
            this.manager.getDashboard().fileSystemCommander.cmd.append("目录不可读！\n");
            return 0;
        }
        // 文件大小为 0，则直接返回
        if (diskInode.getFileSize() == 0) {
            return 0;
        }

        // 记录全部读取数据
        byte[] readData = new byte[count];
        // 记录已经读取的字节数
        int hasreadSize = 0;
        // 生成数据中间结构，便于操作
        Page page = new Page();
        // 当没有读完规定字数 且 没有读到文件尾，则循环读取
        while (hasreadSize < readData.length && systemFileItem.getOffset() < diskInode.getFileSize()) {
            // 获取读取物理块号
            int readBlockNo = diskInode.getStoreBlockNoList().get(systemFileItem.getOffset() / ExternalMem.SECTOR_SIZE).intValue();
            page.setExternalBlockNo(readBlockNo);
            // 整块读取
            this.manager.getDeviceManage().useBuffer(page, BufferHead.READ);
            while (page.getData() == null) {}
            // 记录本轮已经读取的字节数和块内偏移
            int hasreadSizeInCycle;
            int offsetInBlock = systemFileItem.getOffset() % ExternalMem.SECTOR_SIZE;
            // 从偏移指针处开始循环读取
            for (hasreadSizeInCycle = 0;
                 offsetInBlock + hasreadSizeInCycle < page.getData().length &&
                         hasreadSize + hasreadSizeInCycle < readData.length;
                 ++hasreadSizeInCycle) {
                readData[hasreadSize + hasreadSizeInCycle] = page.getData()[offsetInBlock + hasreadSizeInCycle];
            }
            // 调整偏移指针等信息
            systemFileItem.setOffset(systemFileItem.getOffset() + hasreadSizeInCycle);
            hasreadSize += hasreadSizeInCycle;
        }
        // 传输实际读取到的数据
        for (int i = 0; i < hasreadSize; ++i) {
            this.manager.getInMem().getMemery()[buf + i] = readData[i];
        }

        return hasreadSize;
    }
    /**
     * 写文件
     * @param fd 文件描述符
     * @param buf 数据区首地址 (物理地址)
     * @param count 传送字节数
     * @return 实际写入长度
     */
    public synchronized int write(int fd, int buf, int count) {
        // 获取系统打开文件表项
        SystemFileItem systemFileItem = this.isRoot ?
                this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp() :
                this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
        // 没有写权限，则直接返回
        if ((systemFileItem.getFlag() & Flag.FILE_WRITE) == 0) {
            this.manager.getDashboard().fileSystemCommander.cmd.append("非写方式打开，写文件失败！\n");
            return -1;
        }
        // 写入长度为 0，则直接返回
        if (count == 0) {
            return -1;
        }
        // 获取磁盘inode
        DiskInode diskInode = this.diskInodeMap.get("" + systemFileItem.getInode().getInodeNo());
        // 记录全部写入数据
        byte[] writeData = new byte[count];
        for (int i = 0; i < count; ++i) {
            writeData[i] = this.manager.getInMem().getMemery()[buf + i];
        }
        // 记录已经写入的字节数
        int hasWrittenSize = 0;
        // 生成数据中间结构，便于操作
        Page page = new Page();
        // 当没有写完，则循环写入
        while (hasWrittenSize < writeData.length) {
            // 获取写入物理块号
            int writeBlockNo;
            if (systemFileItem.getOffset() / ExternalMem.SECTOR_SIZE < diskInode.getStoreBlockNoList().size()) {
                // 如果当前指针偏移处于的逻辑块号 小于 文件总块号，则说明该逻辑块已存在，直接获取
                writeBlockNo = diskInode.getStoreBlockNoList().get(systemFileItem.getOffset() / ExternalMem.SECTOR_SIZE).intValue();
            } else {
                // 否则，需要申请一个新块，并添加到inode存储块列表中
                writeBlockNo = ExternalMem.SWAP_AREA_START_BLOCK_NO + this.superBlock.allocateStoreBlock();
                diskInode.addStoreBlock(writeBlockNo);
            }
            page.setExternalBlockNo(writeBlockNo);

            // 记录本轮已经写入的字节数和块内偏移
            int haswritedSizeInCycle;
            int offsetInBlock = systemFileItem.getOffset() % ExternalMem.SECTOR_SIZE;
            if (offsetInBlock != 0) {
                // 如果偏移指针指向的不是一块的开头，则需要读取并保留前方的原有数据
                this.manager.getDeviceManage().useBuffer(page, BufferHead.READ);
                while (page.getData() == null) { }
                // 修改后方数据
                for (haswritedSizeInCycle = 0;
                     offsetInBlock + haswritedSizeInCycle < page.getData().length &&
                         hasWrittenSize + haswritedSizeInCycle < writeData.length;
                     ++haswritedSizeInCycle) {
                    page.getData()[offsetInBlock + haswritedSizeInCycle] = writeData[hasWrittenSize + haswritedSizeInCycle];
                }
            } else {
                // 否则，直接进行整块数据填充
                page.setData(new byte[ExternalMem.SECTOR_SIZE]);
                for (haswritedSizeInCycle = 0;
                     haswritedSizeInCycle < page.getData().length &&
                         hasWrittenSize + haswritedSizeInCycle < writeData.length;
                     ++haswritedSizeInCycle) {
                    page.getData()[haswritedSizeInCycle] = writeData[hasWrittenSize + haswritedSizeInCycle];
                }
            }
            // 整块写入
            this.manager.getDeviceManage().useBuffer(page, BufferHead.WRITE);
            // 调整偏移指针等信息
            systemFileItem.setOffset(systemFileItem.getOffset() + haswritedSizeInCycle);
            hasWrittenSize += haswritedSizeInCycle;
        }
        // 如果偏移指针超出文件大小，则更新文件大小
        if (systemFileItem.getOffset() > diskInode.getFileSize()) {
            diskInode.setFileSize(systemFileItem.getOffset());
        }
        // 修改文件更新时间
        diskInode.setLastUpdateTime(this.manager.getClock().getCurrentTime());

        return hasWrittenSize;
    }

    /**
     * 链接文件
     * @param linkedFilePath 被链接文件路径
     * @param newFilePath 新文件路径
     */
    public synchronized void link(String linkedFilePath, String newFilePath) {
        // 路径格式化
        String formatOldPath = this.formatPath(linkedFilePath);
        String formatNewPath = this.formatPath(newFilePath);
        try {
            DiskInode targetDiskInode = this.getDiskInodeByPath(formatOldPath);
            // 如果需要链接的文件不存在，则直接返回
            if (targetDiskInode == null) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("链接文件不存在！\n");
                return;
            }
            if (targetDiskInode.getType() != FileType.FILE) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("链接对象不是文件！\n");
                return;
            }
            // 寻找新目录的父目录
            String dirPath;
            String fileName;
            DiskInode targetDirInode;
            if (formatNewPath.lastIndexOf("/") < 0) {
                targetDirInode = this.currentDir;
                fileName = formatNewPath;
            } else {
                if (formatNewPath.equals("/")) {
                    targetDirInode = this.rootDir;
                } else {
                    dirPath = formatNewPath.substring(0, formatNewPath.lastIndexOf("/"));
                    targetDirInode = this.getDiskInodeByPath(dirPath);
                }
                fileName = formatNewPath.substring(formatNewPath.lastIndexOf("/") + 1);
            }
            // 父目录不存在，则直接返回
            if (targetDirInode == null) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("目标父目录不存在！\n");
                return;
            }
            // 父目录下已有同名文件，则直接返回
            if (targetDirInode.findDirectoryItem(fileName) != -1) {
                this.manager.getDashboard().fileSystemCommander.cmd.append(fileName + " 已存在！\n");
                return;
            }
            // 父目录不是目录文件，则直接返回
            if (targetDirInode.getType() != FileType.DIR) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("不允许在非目录下添加链接文件！\n");
                return;
            }
            // 添加目录项、硬链接数 +1
            targetDirInode.getDirectoryItemList().add(new DirectoryItem(targetDiskInode.getCurrentDir().getInodeNo(), fileName));
            targetDiskInode.increaseHardLinkNum();
        } catch (Exception e) {
            // 检索目录出错
            e.printStackTrace();
            return;
        }
    }
    /**
     * 删除文件/撤销链接
     * @param filePath 相对文件路径
     */
    public synchronized void unlink(String filePath) {
        // 路径格式化
        String formatPath = this.formatPath(filePath);
        try {
            DiskInode targetDiskInode = this.getDiskInodeByPath(formatPath);
            // 需要删除的文件不存在，则直接返回
            if (targetDiskInode == null) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("指定文件或目录不存在！\n");
                return;
            }
            // 需要删除的是非空目录，则直接返回
            if (targetDiskInode.getType() == FileType.DIR && targetDiskInode.getDirectoryItemList().size() > 2) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("指定目录非空，不可直接删除！\n");
                return;
            }
            // 如果该文件正被打开，则直接返回
            ActivityInode activityInode = this.getActivityInodeByInodeNo(targetDiskInode.getCurrentDir().getInodeNo());
            if (activityInode != null) {
                this.manager.getDashboard().fileSystemCommander.cmd.append("文件或目录正在被使用，无法删除！\n");
                return;
            }
            // 删除父目录中的目录项
            DiskInode parentDir = this.currentDir;
            String[] pathElements = formatPath.split("/");
            for (int i = 0; i < pathElements.length - 1; i++) {
                if (pathElements[i].equals("..")) {
                    parentDir = this.diskInodeMap.get("" + parentDir.getParentDir().getInodeNo());
                } else if (!pathElements[i].equals(".")) {
                    parentDir = this.diskInodeMap.get("" + parentDir.getDirectoryItemList().get(parentDir.findDirectoryItem(pathElements[i])).getInodeNo());
                }
            }
            parentDir.removeDir(pathElements[pathElements.length - 1]);

            // 如果链接数 > 1，则还有其他链接的目录项，链接数 -1，然后返回
            if (targetDiskInode.getHardLinkNum() > 1) {
                targetDiskInode.decreaseHardLinkNum();
                return;
            }
            // 如果链接数 = 1，则没有其他链接的目录项，删除磁盘inode及其存储数据
            this.diskInodeMap.remove("" + targetDiskInode.getCurrentDir().getInodeNo());
            targetDiskInode.remove();
        } catch (Exception e) {
            // 检索目录出错
            e.printStackTrace();
            return;
        }
    }

    /**
     * 随机存取，改变文件偏移指针
     * @param fd 文件描述符
     * @param offset 指定偏移
     * @param whence 追加状态
     */
    public synchronized void seek(int fd, int offset, int whence) {
        if (offset < 0) {
            this.manager.getDashboard().fileSystemCommander.cmd.append("无效偏移量！\n");
            return;
        }
        SystemFileItem systemFileItem = this.isRoot ?
                this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp() :
                this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
        systemFileItem.setOffset(whence == 0 ? offset : offset + systemFileItem.getOffset());

        // 检查偏移指针是否移动到文件尾
        DiskInode diskInode = this.diskInodeMap.get("" + systemFileItem.getInode().getInodeNo());
        if (systemFileItem.getOffset() > diskInode.getFileSize()) {
            this.manager.getDashboard().fileSystemCommander.cmd.append("偏移量过大，指针移动到文件尾！\n");
            systemFileItem.setOffset(diskInode.getFileSize());
        }
    }

    /**
     * 修改文件权限
     * @param filePath 文件路径
     * @param mode 设置的权限
     */
    public synchronized void chmod(String filePath, int mode) {
        // 路径格式化
        String formatPath = this.formatPath(filePath);
        try {
            DiskInode targetDiskInode = this.getDiskInodeByPath(formatPath);
            // 需要修改的目录或文件不存在，则直接返回
            if (targetDiskInode == null) {
                return;
            }
            targetDiskInode.setMode(mode);
            // 修改更新时间
            targetDiskInode.setLastUpdateTime(this.manager.getClock().getCurrentTime());
        } catch (Exception e) {
            // 检索目录出错
            e.printStackTrace();
            return;
        }
    }

    /**
     * 处理命令行信息
     * 目前提供的命令如下：
     * ls [-l]          list 显示当前目录下的文件信息
     *                  -l          显示详细信息
     *
     * cd dir           change directory 切换当前工作目录
     *                  dir         指定目录绝对或相对路径
     *
     * mkdir dir [-p]   make directory 当前目录下创建目录
     *                  dir         指定目录绝对或相对路径
     *                  -p          递归创建
     *
     * touch file/dir   create file 当前目录下创建一个新的空白文件；如果文件已存在，则更新文件的修改时间
     *                  file/dir    文件或目录路径
     *
     * rmdir dir -p     remove directory 当前目录下删除目录
     *                  dir         目录路径
     *                  -p          当子目录被删除后使其也成为空目录，则一并删除
     *
     * rm file/dir [-r] remove file 当前目录下删除文件或目录
     *                  file/dir    文件或目录路径
     *                  -r          递归删除，如果参数是一个目录，则删除目录及其以下所有子文件和子目录
     *
     * close file/dir   close file/dir 关闭文件或目录
     *                  file/dir    文件或目录路径
     *
     * cat file         concatenate file 显示文件内容
     *                  file        文件路径
     *
     * vim file         edit file 修改文件内容
     *                  file        文件路径
     *
     * link old new     link new file to old file 链接到一个已存在文件
     *                  old         已存在文件的目录
     *                  new         新文件目录
     *
     * chmod mode path [-r] change mode to file/dir 改变某个文件或目录的权限
     *                  mode        权限模式 000-777
     *                  path        文件或目录路径
     *                  -r          递归修改，一并修改目录及其以下所有子文件和子目录的权限
     *
     * explorer         explorer file in resource manager 打开资源管理器
     *
     * @param cmd 命令
     */
    public synchronized void cmd(String cmd) {
        this.manager.getCpu().switchToKernelState();
        // 命令规格化
        String formatCmd = cmd.replaceAll("[ \t]+", " ").replaceAll("- +", "-");
        String[] cmdElements = formatCmd.split(" ");
        switch (cmdElements[0]) {
            // ls [-l]
            case "ls": {
                if (cmdElements.length > 1) {
                    String param = cmdElements[1];
                    if (!param.contains("-")) {
                        this.manager.getDashboard().fileSystemCommander.cmd.append("错误参数！\n");
                        return;
                    }
                    if (param.equals("-l")) {
                        // 带参数 -l，显示详细信息
                        Iterator<DirectoryItem> iterator = this.currentDir.getDirectoryItemList().iterator();
                        while (iterator.hasNext()) {
                            DirectoryItem directoryItem = iterator.next();
                            if (directoryItem.getFileName().equals(".") || directoryItem.getFileName().equals("..")) {
                                continue;
                            }
                            DiskInode diskInode = this.diskInodeMap.get("" + directoryItem.getInodeNo());
                            // 完整信息格式”：文件或目录 文件权限 用户id 用户组id 文件大小 最近更新时间 硬链接数 文件名
                            String fileInfo = diskInode.getType() == FileType.DIR ? FileType.DIR_MARK : FileType.FILE_MARK;
                            fileInfo += (diskInode.getMode() & Mode.USER_READ) != 0 ? "r" : "-";
                            fileInfo += (diskInode.getMode() & Mode.USER_WRITE) != 0 ? "w" : "-";
                            fileInfo += (diskInode.getMode() & Mode.USER_EXEC) != 0 ? "x" : "-";
                            fileInfo += (diskInode.getMode() & Mode.GROUP_READ) != 0 ? "r" : "-";
                            fileInfo += (diskInode.getMode() & Mode.GROUP_WRITE) != 0 ? "w" : "-";
                            fileInfo += (diskInode.getMode() & Mode.GROUP_EXEC) != 0 ? "x" : "-";
                            fileInfo += (diskInode.getMode() & Mode.OTHER_READ) != 0 ? "r" : "-";
                            fileInfo += (diskInode.getMode() & Mode.OTHER_WRITE) != 0 ? "w" : "-";
                            fileInfo += (diskInode.getMode() & Mode.OTHER_EXEC) != 0 ? "x" : "-";
                            fileInfo += "  ";
                            fileInfo += diskInode.getUserId() == 0 ? "root  " : diskInode.getUserId() + "     ";

                            fileInfo += diskInode.getGroupId() == 0 ? "root" : diskInode.getGroupId();
                            fileInfo += "\t";
                            fileInfo += diskInode.getLastUpdateTime();
                            fileInfo += "\t";
                            fileInfo += diskInode.getFileSize();
                            fileInfo += "\t";
                            fileInfo += diskInode.getHardLinkNum();
                            fileInfo += "\t";
                            fileInfo += directoryItem.getFileName();

                            this.manager.getDashboard().fileSystemCommander.cmd.append(fileInfo + "\n");
                        }
                    }
                } else {
                    // 不带参数，仅显示文件名
                    Iterator<DirectoryItem> iterator = this.currentDir.getDirectoryItemList().iterator();
                    while (iterator.hasNext()) {
                        DirectoryItem directoryItem = iterator.next();
                        if (directoryItem.getFileName().equals(".") || directoryItem.getFileName().equals("..")) {
                            continue;
                        }
                        this.manager.getDashboard().fileSystemCommander.cmd.append(directoryItem.getFileName() + "        ");
                    }
                    this.manager.getDashboard().fileSystemCommander.cmd.append("\n");
                }
                break;
            }
            // cd dir
            case "cd": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    try {
                        DiskInode diskInode = this.getDiskInodeByPath(this.formatPath(cmdElements[1]));
                        if (diskInode == null) {
                            this.manager.getDashboard().fileSystemCommander.cmd.append("不存在目录 " + cmdElements[1] + "\n");
                            return;
                        }
                        if (diskInode.getType() != FileType.DIR) {
                            this.manager.getDashboard().fileSystemCommander.cmd.append("该路径不是目录！\n");
                        } else {
                            this.currentDir = diskInode;
                            this.currentDirPath = this.getAbsolutePathByDiskInode(diskInode);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            // mkdir dir [-p]
            case "mkdir": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    if (cmdElements[1].startsWith("/") || cmdElements[1].startsWith("../")) {
                        this.manager.getDashboard().fileSystemCommander.cmd.append("仅允许在当前目录下操作！\n");
                        return;
                    }
                    if (cmdElements.length > 2 && cmdElements[2].equals("-p")) {
                        // 递归创建
                        this.createDir(cmdElements[1], -1, true);
                    } else {
                        // 非递归创建
                        this.createDir(cmdElements[1], -1, false);
                    }
                }
                break;
            }
            // touch file/dir
            case "touch": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    if (cmdElements[1].startsWith("/") || cmdElements[1].startsWith("../")) {
                        this.manager.getDashboard().fileSystemCommander.cmd.append("仅允许在当前目录下操作！\n");
                        return;
                    }
                    this.createFile(cmdElements[1], -1, false);
                }
                break;
            }
            // rmdir dir [-p]
            case "rmdir": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    if (cmdElements[1].startsWith("/") || cmdElements[1].startsWith("../")) {
                        this.manager.getDashboard().fileSystemCommander.cmd.append("仅允许在当前目录下操作！\n");
                        return;
                    }
                    try {
                        DiskInode diskInode = this.getDiskInodeByPath(this.formatPath(cmdElements[1]));
                        if (diskInode == null) {
                            this.manager.getDashboard().fileSystemCommander.cmd.append("指定文件或目录不存在！\n");
                            return;
                        }
                        if (diskInode.getType() != FileType.DIR) {
                            this.manager.getDashboard().fileSystemCommander.cmd.append("该路径不是目录！\n");
                            return;
                        }
                        if (cmdElements.length > 2 && cmdElements[2].equals("-p")) {
                            // 一并删除空的父目录
                            DiskInode parentInode = this.diskInodeMap.get("" + diskInode.getParentDir().getInodeNo());
                            this.unlink(cmdElements[1]);
                            this.unlink(this.getAbsolutePathByDiskInode(parentInode));
                        } else {
                            // 普通的删除目录
                            this.unlink(cmdElements[1]);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
                break;
            }
            // rm file/dir [-r]
            case "rm": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    if (cmdElements[1].startsWith("/") || cmdElements[1].startsWith("../")) {
                        this.manager.getDashboard().fileSystemCommander.cmd.append("仅允许在当前目录下操作！\n");
                        return;
                    }
                    try {
                        DiskInode diskInode = this.getDiskInodeByPath(this.formatPath(cmdElements[1]));
                        if (diskInode == null) {
                            this.manager.getDashboard().fileSystemCommander.cmd.append("指定文件或目录不存在！\n");
                            return;
                        }
                        if (diskInode.getType() == FileType.FILE) {
                            // 如果是文件，则删除
                            this.unlink(cmdElements[1]);
                        } else if (diskInode.getType() == FileType.DIR){
                            // 如果是目录，必须递归删除
                            if (cmdElements.length > 2 && cmdElements[2].equals("-r")) {
                                Vector<String> names = new Vector<>();
                                for (int i = 2; i < diskInode.getDirectoryItemList().size(); i++) {
                                    names.add(diskInode.getDirectoryItemList().get(i).getFileName());
                                }
                                Iterator<String> iterator = names.iterator();
                                while (iterator.hasNext()) {
                                    String path = cmdElements[1];
                                    if (!path.endsWith("/")) {
                                        path += "/";
                                    }
                                    path += iterator.next();
                                    this.manager.getDashboard().fileSystemCommander.cmd.append(cmdElements[0] + " " + path + " " + cmdElements[2] + "\n");
                                    this.cmd(cmdElements[0] + " " + path + " " + cmdElements[2]);
                                }
                                this.unlink(cmdElements[1]);
                            } else {
                                this.manager.getDashboard().fileSystemCommander.cmd.append("不可直接删除目录！\n");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
                break;
            }
            // close file/dir
            case "close": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    try {
                        DiskInode diskInode = this.getDiskInodeByPath(this.formatPath(cmdElements[1]));
                        if (diskInode == null) {
                            this.manager.getDashboard().fileSystemCommander.cmd.append("指定文件或目录不存在！\n");
                            return;
                        }
                        Vector<UserFileItem> userOpenFileTable = this.isRoot ? this.rootOpenFileTable : this.userOperatePCB.getUserOpenFileTable();
                        for (int i = 0; i < userOpenFileTable.size(); i++) {
                            if ( userOpenFileTable.get(i).getFp().getInode().getInodeNo() == diskInode.getCurrentDir().getInodeNo()) {
                                this.close(userOpenFileTable.get(i).getFd());
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            // cat file
            case "cat": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    int fd;
                    int bufferNo = this.manager.getDeviceManage().allocateBuffer();
                    int buf = (bufferNo + InternalMem.BUFFER_AREA_START_PAGE_NO) * InternalMem.PAGE_SIZE;
                    fd = this.open(cmdElements[1], Flag.FILE_READ);
                    if (fd != -1) {
                        SystemFileItem systemFileItem = this.isRoot ?
                                this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp() :
                                this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
                        DiskInode diskInode = this.diskInodeMap.get("" + systemFileItem.getInode().getInodeNo());
                        if (diskInode.getType() != FileType.FILE) {
                            this.manager.getDashboard().fileSystemCommander.cmd.append("不可读取目录！\n");
                            this.manager.getDeviceManage().releaseBuffer(bufferNo);
                            return;
                        }
                        int hasReadSize = 0;
                        int readTime = (int) Math.ceil((double) diskInode.getFileSize() / InternalMem.PAGE_SIZE);
                        for (int i = 0; i < readTime; i++) {
                            int count = diskInode.getFileSize() - hasReadSize <= InternalMem.PAGE_SIZE ?
                                    diskInode.getFileSize() - hasReadSize : InternalMem.PAGE_SIZE;
                            hasReadSize += this.read(fd, buf, count);
                            byte[] bytes = new byte[count];
                            for (int j = 0; j < count; j++) {
                                bytes[j] = this.manager.getInMem().getMemery()[buf + j];
                            }
                            this.manager.getDashboard().fileSystemCommander.cmd.append(new String(bytes));
                        }
                        this.manager.getDashboard().fileSystemCommander.cmd.append("\n");

                        this.close(fd);
                    }
                    this.manager.getDeviceManage().releaseBuffer(bufferNo);
                }
                break;
            }
            // vim file
            case "vim": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    int fd;
                    int bufferNo = this.manager.getDeviceManage().allocateBuffer();
                    int buf = (bufferNo + InternalMem.BUFFER_AREA_START_PAGE_NO) * InternalMem.PAGE_SIZE;
                    fd = this.open(cmdElements[1], Flag.FILE_READ | Flag.FILE_WRITE);
                    if (fd != -1) {
                        SystemFileItem systemFileItem = this.isRoot ?
                                this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp() :
                                this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
                        DiskInode diskInode = this.diskInodeMap.get("" + systemFileItem.getInode().getInodeNo());
                        if (diskInode.getType() != FileType.FILE) {
                            this.manager.getDashboard().fileSystemCommander.cmd.append("不可编辑目录！\n");
                            this.manager.getDeviceManage().releaseBuffer(bufferNo);
                            return;
                        }
                        int hasReadSize = 0;
                        int readTime = (int) Math.ceil((double) diskInode.getFileSize() / InternalMem.PAGE_SIZE);
                        for (int i = 0; i < readTime; i++) {
                            int count = diskInode.getFileSize() - hasReadSize <= InternalMem.PAGE_SIZE ?
                                    diskInode.getFileSize() - hasReadSize : InternalMem.PAGE_SIZE;
                            hasReadSize += this.read(fd, buf, count);
                            byte[] bytes = new byte[count];
                            for (int j = 0; j < count; j++) {
                                bytes[j] = this.manager.getInMem().getMemery()[buf + j];
                            }
                            this.manager.getDashboard().fileSystemCommander.vim.append(new String(bytes));
                        }
                        this.close(fd);
                        this.manager.getDashboard().fileSystemCommander.vimIn();
                    }
                    this.manager.getDeviceManage().releaseBuffer(bufferNo);

                }
                break;
            }
            // link old new
            case "link": {
                if (cmdElements.length == 1 || cmdElements.length == 2) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    this.link(cmdElements[1], cmdElements[2]);
                }
                break;
            }
            // chmod mode path [-r]
            case "chmod": {
                if (cmdElements.length == 1) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少权限与路径！\n");
                } else if (cmdElements.length == 2) {
                    this.manager.getDashboard().fileSystemCommander.cmd.append("缺少路径！\n");
                } else {
                    String modeString = cmdElements[1];
                    if (modeString.length() != 3) {
                        this.manager.getDashboard().fileSystemCommander.cmd.append("权限无效！\n");
                    } else {
                        try {
                            int mode = 0;
                            int userMode = Integer.parseInt(modeString.substring(0,1));
                            int groupMode = Integer.parseInt(modeString.substring(1,2));
                            int otherMode = Integer.parseInt(modeString.substring(2,3));
                            mode |= (userMode  & Mode.OTHER_READ)  != 0 ? Mode.USER_READ    : 0;
                            mode |= (userMode  & Mode.OTHER_WRITE) != 0 ? Mode.USER_WRITE   : 0;
                            mode |= (userMode  & Mode.OTHER_EXEC)  != 0 ? Mode.USER_EXEC    : 0;
                            mode |= (groupMode & Mode.OTHER_READ)  != 0 ? Mode.GROUP_READ   : 0;
                            mode |= (groupMode & Mode.OTHER_WRITE) != 0 ? Mode.GROUP_WRITE  : 0;
                            mode |= (groupMode & Mode.OTHER_EXEC)  != 0 ? Mode.GROUP_EXEC   : 0;
                            mode |= (otherMode & Mode.OTHER_READ)  != 0 ? Mode.OTHER_READ   : 0;
                            mode |= (otherMode & Mode.OTHER_WRITE) != 0 ? Mode.OTHER_WRITE  : 0;
                            mode |= (otherMode & Mode.OTHER_EXEC)  != 0 ? Mode.OTHER_EXEC   : 0;
                            if (cmdElements.length > 3 && cmdElements[3].equals("-r")) {
                                // 递归修改
                                DiskInode diskInode = this.getDiskInodeByPath(this.formatPath(cmdElements[2]));
                                if (diskInode == null) {
                                    this.manager.getDashboard().fileSystemCommander.cmd.append("目标文件不存在！\n");
                                    return;
                                }
                                Vector<String> names = new Vector<>();
                                for (int i = 2; i < diskInode.getDirectoryItemList().size(); i++) {
                                    names.add(diskInode.getDirectoryItemList().get(i).getFileName());
                                }
                                Iterator<String> iterator = names.iterator();
                                while (iterator.hasNext()) {
                                    String path = cmdElements[2];
                                    if (!path.endsWith("/")) {
                                        path += "/";
                                    }
                                    path += iterator.next();
                                    this.manager.getDashboard().fileSystemCommander.cmd.append(cmdElements[0] + " " + path + " " + cmdElements[2] + "\n");
                                    this.cmd(cmdElements[0] + " " + cmdElements[1] + " " + path + " " + cmdElements[3]);
                                }
                            }
                            this.chmod(cmdElements[2], mode);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }
            case "explorer": {
                this.manager.getDashboard().fileSystemCommander.cmd.append("打开资源管理器!\n");
                // 刷新目录
                this.cmd("cd /");
                this.manager.getDashboard().fileExplorer.refresh();
                this.manager.getDashboard().fileExplorer.setVisible(true);
                break;
            }
            default: {
                this.manager.getDashboard().fileSystemCommander.cmd.append("无效指令!\n");
                break;
            }
        }
        this.manager.getCpu().switchToUserState();
    }

    /**
     * vim写文件
     * @param cmd 命令行
     * @param data 写入数据
     */
    public void vimWrite(String cmd, String data) {
        // 命令规格化
        String formatCmd = cmd.replaceAll("[ \t]+", " ").replaceAll("- +", "-");
        String[] cmdElements = formatCmd.split(" ");
        
        int fd;
        int bufferNo = this.manager.getDeviceManage().allocateBuffer();
        int buf = (bufferNo + InternalMem.BUFFER_AREA_START_PAGE_NO) * InternalMem.PAGE_SIZE;
        byte[] bytes = data.getBytes();
        // 写方式打开
        fd = this.open(cmdElements[1], Flag.FILE_READ | Flag.FILE_WRITE);
        if (fd != -1) {
            SystemFileItem systemFileItem = this.isRoot ?
                    this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp() :
                    this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
            DiskInode diskInode = this.diskInodeMap.get("" + systemFileItem.getInode().getInodeNo());
            
            int hasWrittenSize = 0;

            int writeTime = (int) Math.ceil((double) data.length() / InternalMem.PAGE_SIZE);
            for (int i = 0; i < writeTime; i++) {
                int count = data.length() - hasWrittenSize <= InternalMem.PAGE_SIZE ?
                        data.length() - hasWrittenSize : InternalMem.PAGE_SIZE;

                this.manager.getDeviceManage().setBufferContent(bufferNo, Arrays.copyOfRange(bytes, hasWrittenSize, hasWrittenSize + InternalMem.PAGE_SIZE));
                hasWrittenSize += this.write(fd, buf, count);
            }
            // 调整文件大小
            if (data.length() < diskInode.getFileSize()) {
                diskInode.setFileSize(data.length());
                // 删除多余的存储块
                while (writeTime < diskInode.getStoreBlockNoList().size()) {
                    Integer integer = diskInode.getStoreBlockNoList().get(diskInode.getStoreBlockNoList().size() - 1);
                    diskInode.getStoreBlockNoList().remove(integer);
                    this.superBlock.releaseStoreBlock(integer.intValue() - ExternalMem.SWAP_AREA_START_BLOCK_NO);
                }
            }

            this.close(fd);
        }
        this.manager.getDeviceManage().releaseBuffer(bufferNo);
    }

    /**
     * 获取文件数据
     * @param filePath 文件路径
     * @return 文件数据
     */
    public String getFileData(String filePath) {
        String fileData = "";

        int fd;
        int bufferNo = this.manager.getDeviceManage().allocateBuffer();
        int buf = (bufferNo + InternalMem.BUFFER_AREA_START_PAGE_NO) * InternalMem.PAGE_SIZE;
        fd = this.open(filePath, Flag.FILE_READ | Flag.FILE_WRITE);
        if (fd != -1) {
            SystemFileItem systemFileItem = this.isRoot ?
                    this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp() :
                    this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
            DiskInode diskInode = this.diskInodeMap.get("" + systemFileItem.getInode().getInodeNo());

            int hasReadSize = 0;
            int readTime = (int) Math.ceil((double) diskInode.getFileSize() / InternalMem.PAGE_SIZE);
            for (int i = 0; i < readTime; i++) {
                int count = diskInode.getFileSize() - hasReadSize <= InternalMem.PAGE_SIZE ?
                        diskInode.getFileSize() - hasReadSize : InternalMem.PAGE_SIZE;
                hasReadSize += this.read(fd, buf, count);
                byte[] bytes = new byte[count];
                for (int j = 0; j < count; j++) {
                    bytes[j] = this.manager.getInMem().getMemery()[buf + j];
                }
                fileData += new String(bytes);
            }
            this.close(fd);
        }
        this.manager.getDeviceManage().releaseBuffer(bufferNo);

        return fileData;
    }

    /**
     * 写入文件数据
     * @param filePath 文件路径
     * @param data 写入数据
     */
    public void writeFileData(String filePath, String data) {
        int fd;
        int bufferNo = this.manager.getDeviceManage().allocateBuffer();
        int buf = (bufferNo + InternalMem.BUFFER_AREA_START_PAGE_NO) * InternalMem.PAGE_SIZE;
        byte[] bytes = data.getBytes();
        // 写方式打开
        fd = this.open(filePath, Flag.FILE_READ | Flag.FILE_WRITE);
        if (fd != -1) {
            SystemFileItem systemFileItem = this.isRoot ?
                    this.getUserFileItemByFd(this.rootOpenFileTable, fd).getFp() :
                    this.getUserFileItemByFd(this.userOperatePCB.getUserOpenFileTable(), fd).getFp();
            DiskInode diskInode = this.diskInodeMap.get("" + systemFileItem.getInode().getInodeNo());

            int hasWrittenSize = 0;

            int writeTime = (int) Math.ceil((double) data.length() / InternalMem.PAGE_SIZE);
            for (int i = 0; i < writeTime; i++) {
                int count = data.length() - hasWrittenSize <= InternalMem.PAGE_SIZE ?
                        data.length() - hasWrittenSize : InternalMem.PAGE_SIZE;

                this.manager.getDeviceManage().setBufferContent(bufferNo, Arrays.copyOfRange(bytes, hasWrittenSize, hasWrittenSize + InternalMem.PAGE_SIZE));
                hasWrittenSize += this.write(fd, buf, count);
            }
            // 调整文件大小
            if (data.length() < diskInode.getFileSize()) {
                diskInode.setFileSize(data.length());
                // 删除多余的存储块
                while (writeTime < diskInode.getStoreBlockNoList().size()) {
                    Integer integer = diskInode.getStoreBlockNoList().get(diskInode.getStoreBlockNoList().size() - 1);
                    diskInode.getStoreBlockNoList().remove(integer);
                    this.superBlock.releaseStoreBlock(integer.intValue() - ExternalMem.SWAP_AREA_START_BLOCK_NO);
                }
            }

            this.close(fd);
        }
        this.manager.getDeviceManage().releaseBuffer(bufferNo);
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public Vector<ActivityInode> getActivityInodeList() {
        return activityInodeList;
    }

    public void setActivityInodeList(Vector<ActivityInode> activityInodeList) {
        this.activityInodeList = activityInodeList;
    }

    public HashMap<String, DiskInode> getDiskInodeMap() {
        return diskInodeMap;
    }

    public void setDiskInodeMap(HashMap<String, DiskInode> diskInodeMap) {
        this.diskInodeMap = diskInodeMap;
    }

    public Vector<SystemFileItem> getSystemOpenFileTable() {
        return systemOpenFileTable;
    }

    public void setSystemOpenFileTable(Vector<SystemFileItem> systemOpenFileTable) {
        this.systemOpenFileTable = systemOpenFileTable;
    }

    public SuperBlock getSuperBlock() {
        return superBlock;
    }

    public void setSuperBlock(SuperBlock superBlock) {
        this.superBlock = superBlock;
    }

    public DiskInode getRootDir() {
        return rootDir;
    }

    public void setRootDir(DiskInode rootDir) {
        this.rootDir = rootDir;
    }

    public DiskInode getCurrentDir() {
        return currentDir;
    }

    public void setCurrentDir(DiskInode currentDir) {
        this.currentDir = currentDir;
    }

    public String getCurrentDirPath() {
        return currentDirPath;
    }

    public void setCurrentDirPath(String currentDirPath) {
        this.currentDirPath = currentDirPath;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public void setRoot(boolean root) {
        isRoot = root;
    }

    public Vector<UserFileItem> getRootOpenFileTable() {
        return rootOpenFileTable;
    }

    public void setRootOpenFileTable(Vector<UserFileItem> rootOpenFileTable) {
        this.rootOpenFileTable = rootOpenFileTable;
    }
    public int getOpenFileCount() {
        return openFileCount;
    }

    public void setOpenFileCount(int openFileCount) {
        this.openFileCount = openFileCount;
    }

    public PCB getUserOperatePCB() {
        return userOperatePCB;
    }

    public void setUserOperatePCB(PCB userOperatePCB) {
        this.userOperatePCB = userOperatePCB;
    }
}
