package interrupt;

import os.Manager;

/**
 * 作业请求中断
 *
 * 用以添加一条作业请求
 *
 *
 * @author ZJC
 */
public class JobRequestInterrupt extends Thread {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;

    public JobRequestInterrupt(Manager manager) {
        super("JobRequest");
        this.manager = manager;
    }

    @Override
    public void run() {
        // 调用作业管理 -> 添加作业
        this.manager.getSchedule().getJobManage().addJob();
    }
}
