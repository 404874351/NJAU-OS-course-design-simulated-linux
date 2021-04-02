package hardware;

import interrupt.HardwareInterrupt;
import os.Manager;

import java.util.Timer;

/**
 * 时钟类，用于系统时钟
 *
 * 继承计时器类 {@link Timer} 用于间隔固定时间循环进行操作
 * 产生硬件中断（时钟中断）{@link interrupt.HardwareInterrupt} 操作完成后时钟累加
 *
 * @author ZJC
 */
public class Clock extends Timer {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 系统时间间隔，单位 ms
     */
    public static final int INTERVAL = 1000;
    /**
     * 当前系统时间
     */
    private volatile int currentTime;
    /**
     * 暂停标志
     */
    private volatile boolean pause;

    public Clock(Manager manager) {
        super("Clock");
        this.manager = manager;
        this.currentTime = -1;
        this.pause = true;

        this.manager.getDashboard().consoleSuccess("时钟模块初始化完成");
    }

    /**
     * 时间增加
     */
    public void addTime() {
        ++this.currentTime;
    }

    /**
     * 开始执行，并设置时钟中断
     */
    public void start() {
        this.schedule(new HardwareInterrupt(this.manager), 0, INTERVAL);
    }

    public int getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(int currentTime) {
        this.currentTime = currentTime;
    }

    public boolean isPause() {
        return pause;
    }

    public void setPause(boolean pause) {
        this.pause = pause;
    }
}
