package os;

import gui.Dashboard;
import hardware.*;
import kernel.Deadlock;
import kernel.DeviceManage;
import kernel.FileSystem;
import kernel.Schedule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * 管理器
 *
 * 管理仿真系统的一切操作，包括功能执行和用户交互等
 *
 * @author ZJC
 */
public class Manager {
    /**
     * 系统时钟
     */
    private Clock clock;
    /**
     * CPU 中央处理器
     */
    private CPU cpu;
    /**
     * MMU 内存管理单元
     */
    private MMU mmu;
    /**
     * 数据线
     */
    private DataLine dataLine;
    /**
     * 地址线
     */
    private AddressLine addressLine;
    /**
     * 内存
     */
    private InternalMem inMem;
    /**
     * 外存
     */
    private ExternalMem exMem;
    /**
     * 文件系统
     */
    private FileSystem fileSystem;
    /**
     * 进程调度模块
     */
    private Schedule schedule;
    /**
     * 设备管理模块
     */
    private DeviceManage deviceManage;
    /**
     * 死锁处理模块
     */
    private Deadlock deadlock;
    /**
     * 图像化界面
     */
    private Dashboard dashboard;

    public Manager() {
        // 将控制台内容重定向到ProcessResult.txt文件
        File ProcessResults = new File("./ProcessResults.txt");
        try {
            // 如果文件不存在，则新建
            if (!ProcessResults.exists()) {
                ProcessResults.createNewFile();
            }
            // 改变系统输出流
            System.setOut(new PrintStream(new FileOutputStream(ProcessResults)));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 优先启动图形化界面
        this.dashboard      = new Dashboard(this);
        // 问候语
        this.dashboard.consoleLog("仿真Linux系统启动中...");

        this.clock          = new Clock(this);
        this.cpu            = new CPU(this);
        this.mmu            = new MMU(this);
        this.dataLine       = new DataLine();
        this.addressLine    = new AddressLine();
        this.inMem          = new InternalMem(this);
        this.deviceManage   = new DeviceManage(this);
        this.exMem          = new ExternalMem(this);
        this.fileSystem     = new FileSystem(this);
        this.schedule       = new Schedule(this);
        this.deadlock       = new Deadlock(this);

        // 激活操作按钮
        this.dashboard.enableAllButton();
        // 提示启动完成
        this.dashboard.consoleSuccess("\n系统启动完毕，可以开始运行");
    }

    /**
     * 开始运行
     */
    public void start() {
        // 调度线程启动
        this.schedule.start();
        // 时钟线程启动
        this.clock.start();
        // 死锁线程启动
        this.deadlock.start();
    }

    public Clock getClock() {
        return clock;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public CPU getCpu() {
        return cpu;
    }

    public void setCpu(CPU cpu) {
        this.cpu = cpu;
    }

    public MMU getMmu() {
        return mmu;
    }

    public void setMmu(MMU mmu) {
        this.mmu = mmu;
    }

    public DataLine getDataLine() {
        return dataLine;
    }

    public void setDataLine(DataLine dataLine) {
        this.dataLine = dataLine;
    }

    public AddressLine getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(AddressLine addressLine) {
        this.addressLine = addressLine;
    }

    public InternalMem getInMem() {
        return inMem;
    }

    public void setInMem(InternalMem inMem) {
        this.inMem = inMem;
    }

    public ExternalMem getExMem() {
        return exMem;
    }

    public void setExMem(ExternalMem exMem) {
        this.exMem = exMem;
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public DeviceManage getDeviceManage() {
        return deviceManage;
    }

    public void setDeviceManage(DeviceManage deviceManage) {
        this.deviceManage = deviceManage;
    }

    public Deadlock getDeadlock() {
        return deadlock;
    }

    public void setDeadlock(Deadlock deadlock) {
        this.deadlock = deadlock;
    }

    public Dashboard getDashboard() {
        return dashboard;
    }

    public void setDashboard(Dashboard dashboard) {
        this.dashboard = dashboard;
    }
}
