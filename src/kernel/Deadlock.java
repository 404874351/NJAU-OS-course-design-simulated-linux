package kernel;

import os.Manager;

import java.util.*;

/**
 * 死锁处理
 *
 * 负责系统资源的PV操作，死锁检测等
 *
 * @author ZJC
 */
public class Deadlock extends Thread {
    /**
     * 系统管理器，用以获取系统资源
     */
    private Manager manager;
    /**
     * 死锁检测周期
     */
    public static final int DETECT_CYCLE = 3;
    /**
     * 资源种类数 3
     */
    public static final int RESOURCE_TYPE_NUM = 3;
    /**
     * 资源 A
     */
    public static final int RESOURCE_A = 0;
    /**
     * 资源 A 数目
     */
    public static final int RESOURCE_A_NUM = 1;
    /**
     * 资源 B
     */
    public static final int RESOURCE_B = 1;
    /**
     * 资源 B 数目
     */
    public static final int RESOURCE_B_NUM = 1;
    /**
     * 资源 C
     */
    public static final int RESOURCE_C = 2;
    /**
     * 资源 C 数目
     */
    public static final int RESOURCE_C_NUM = 2;

    /**
     * 资源已分配向量表，存储信息为 {进程 id，资源 id}
     */
    private Vector<int[]> allocation;
    /**
     * 资源在申请向量表，存储信息为 {进程 id，资源 id}
     */
    private Vector<int[]> request;
    /**
     * 可分配的资源数
     */
    private int[] available;
    /**
     * 信号量
     */
    private int[] mutexes;


    public Deadlock(Manager manager) {
        super("Deadlock");
        this.manager = manager;
        this.allocation = new Vector<>();
        this.request = new Vector<>();
        this.available = new int[]{RESOURCE_A_NUM, RESOURCE_B_NUM, RESOURCE_C_NUM};
        this.mutexes = new int[]{RESOURCE_A_NUM, RESOURCE_B_NUM, RESOURCE_C_NUM};

        this.manager.getDashboard().consoleSuccess("死锁检测模块初始化完成");
    }

    @Override
    public void run() {
        while (true) {
            // 每 3 个时钟周期检测一次死锁，并进行处理
            if (this.manager.getClock().getCurrentTime() % DETECT_CYCLE == 0) {
                ResourceEdges[] deadlockProcesses = this.detectDeadlock();
                // 检测到死锁发生，则解除死锁
                if (deadlockProcesses != null) {
                    this.manager.getDashboard().consoleError("检测到死锁，尝试解除死锁");
                    this.removeDeadlock(deadlockProcesses);
                }
            }
        }
    }

    /**
     * P操作
     * @param resourceType 资源类型
     */
    private void P(int resourceType) {
        --this.mutexes[resourceType];
    }

    /**
     * V操作
     * @param resourceType 资源类型
     */
    private void V(int resourceType) {
        ++this.mutexes[resourceType];
    }

    /**
     * 添加资源请求
     * @param processId 进程id
     * @param resourceType 资源类型
     */
    public void addRequest(int processId, int resourceType) {
        this.request.add(new int[]{processId, resourceType});
    }

    /**
     * 删除资源请求
     * @param processId 进程id
     * @param resourceType 资源类型
     */
    public void removeRequest(int processId, int resourceType) {
        for (int i = 0; i < this.request.size(); ++i) {
            if (this.request.get(i)[0] == processId && this.request.get(i)[1] == resourceType) {
                this.request.remove(i);
                break;
            }
        }
    }

    /**
     * 添加分配信息
     * @param processId 进程id
     * @param resourceType 资源类型
     */
    public void addAllocation(int processId, int resourceType) {
        this.allocation.add(new int[]{processId, resourceType});
        // 空闲资源数 -1
        --this.available[resourceType];
    }

    /**
     * 删除分配信息
     * @param processId 进程id
     * @param resourceType 资源类型
     */
    public void removeAllocation(int processId, int resourceType) {
        for (int i = 0; i < this.allocation.size(); ++i) {
            if (this.allocation.get(i)[0] == processId && this.allocation.get(i)[1] == resourceType) {
                this.allocation.remove(i);
                break;
            }
        }
        // 空闲资源数 +1
        ++this.available[resourceType];
    }

    /**
     * 检索分配信息
     * @param processId 进程id
     * @param resourceType 资源类型
     */
    public synchronized int searchAllocation(int processId, int resourceType) {
        for (int i = 0; i < this.allocation.size(); ++i) {
            if (this.allocation.get(i)[0] == processId && this.allocation.get(i)[1] == resourceType) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 分配资源
     * @param pcb 进程PCB
     * @param resourceType 资源类型
     */
    public synchronized void applyResource(PCB pcb, int resourceType) {
        this.P(resourceType);
        // 添加在申请向量
        this.addRequest(pcb.getId(), resourceType);
    }

    /**
     * 释放资源
     * @param pcb 进程PCB
     * @param resourceType 资源类型
     */
    public synchronized void releaseResource(PCB pcb, int resourceType) {
        this.V(resourceType);
        // 删除已分配向量
        this.removeAllocation(pcb.getId(), resourceType);
        this.manager.getDashboard().consoleInfo("进程 " + pcb.getId() +
                " 释放资源 " + resourceType + " 成功" +
                " 当前信号量 " + Arrays.toString(this.mutexes));
    }

    /**
     * 尝试分配资源
     * @param pcb 进程PCB
     * @param resourceType 资源类型
     * @return 分配结果
     */
    public synchronized int tryAllocateResource(PCB pcb, int resourceType) {
        if (this.mutexes[resourceType] < 0) {
            // 信号量为负数，则申请失败
            // 返回阻塞标志
            return -1;
        } else {
            // 信号量为非负数，则申请成功
            // 删除在申请向量
            this.removeRequest(pcb.getId(), resourceType);
            // 添加已分配向量
            this.addAllocation(pcb.getId(), resourceType);
            this.manager.getDashboard().consoleSuccess("进程 " + pcb.getId() +
                    " 申请资源 " + resourceType + " 成功" +
                    " 当前信号量 " + Arrays.toString(this.mutexes));
            // 返回成功标志
            return 0;
        }
    }
    /**
     * 尝试重分配资源
     * @param pcb 进程PCB
     * @param resourceType 资源类型
     * @return 分配结果
     */
    public synchronized int tryReallocateResource(PCB pcb, int resourceType) {
        if (this.mutexes[resourceType] > 0) {
            // 信号量为正数，则系统中没有进程在申请该资源
            // 返回成功标志
            return 0;
        } else {
            // 信号量为非正数，则系统中还有进程在申请该资源
            // 删除在申请向量
            this.removeRequest(pcb.getId(), resourceType);
            // 添加已分配向量
            this.addAllocation(pcb.getId(), resourceType);
            this.manager.getDashboard().consoleSuccess("进程 " + pcb.getId() +
                    " 重分配资源 " + resourceType + " 成功" +
                    " 当前信号量 " + Arrays.toString(this.mutexes));
            // 返回唤醒标志
            return -1;
        }
    }

    /**
     * 检测死锁
     * @return 资源请求与分配边数组
     */
    public synchronized ResourceEdges[] detectDeadlock() {
        // 以进程为单位，整理资源已分配和在申请情况
        HashMap<String, ResourceEdges> processResourceInfo = new HashMap<>();
        for (int i = 0; i < this.request.size(); ++i) {
            int[] requestInfo = this.request.get(i);
            if (!processResourceInfo.containsKey("" + requestInfo[0])) {
                ResourceEdges resourceEdges = new ResourceEdges(requestInfo[0]);
                resourceEdges.addRequestEdge(requestInfo[1]);
                processResourceInfo.put("" + requestInfo[0], resourceEdges);
            } else {
                processResourceInfo.get("" + requestInfo[0]).addRequestEdge(requestInfo[1]);
            }
        }
        for (int i = 0; i < this.allocation.size(); ++i) {
            int[] allocationInfo = this.allocation.get(i);
            if (!processResourceInfo.containsKey("" + allocationInfo[0])) {
                ResourceEdges resourceEdges = new ResourceEdges(allocationInfo[0]);
                resourceEdges.addAllocationEdge(allocationInfo[1]);
                processResourceInfo.put("" + allocationInfo[0], resourceEdges);
            } else {
                processResourceInfo.get("" + allocationInfo[0]).addAllocationEdge(allocationInfo[1]);
            }
        }
        // 获取当前系统中空闲资源数
        int[] currentAvailable = Arrays.copyOf(this.available, this.available.length);

        // 开始死锁检测，采用资源分配图算法
        while (processResourceInfo.size() != 0) {
            boolean canSimplify = false;
            Iterator<Map.Entry<String, ResourceEdges>> iterator = processResourceInfo.entrySet().iterator();

            // 遍历所有结点
            while (iterator.hasNext()) {
                Map.Entry<String, ResourceEdges> entry = iterator.next();
                // 仅有请求边的，不做考虑
                if (entry.getValue().isOnlyRequest()) {
                    iterator.remove();
                    canSimplify = true;
                    continue;
                }
                // 仅有分配边的，释放所有资源（孤立）
                if (entry.getValue().isOnlyAllocation()) {
                    entry.getValue().releaseAllAllocationEdge(currentAvailable);
                    iterator.remove();
                    canSimplify = true;
                    continue;
                }
                // 既有请求边又有分配边的，尝试将刚刚释放的资源分配给这些结点
                for (int i = 0; i < entry.getValue().getRequestEdges().length; ++i) {
                    // 结点需要某资源，且该资源有空闲，则分配
                    if (entry.getValue().getRequestEdges()[i] != 0 && currentAvailable[i] != 0) {
                        --currentAvailable[i];
                        entry.getValue().removeRequestEdge(i);
                        entry.getValue().addAllocationEdge(i);
                        canSimplify = true;
                    }
                }
            }
            // 如果资源分配图不可完全简化，则返回死锁结点
            if (!canSimplify) {
                ResourceEdges[] deadlockNodes = new ResourceEdges[processResourceInfo.size()];
                iterator = processResourceInfo.entrySet().iterator();
                int i = 0;
                while (iterator.hasNext()) {
                    deadlockNodes[i++] = iterator.next().getValue();
                }
                return deadlockNodes;
            }
        }
        // 如果最后能够消除Map内的所有结点（资源分配图可完全简化），则返回无死锁标志
        return null;
    }

    /**
     * 解除死锁
     * @param deadlockNodes 死锁节点数据
     */
    public synchronized void removeDeadlock(ResourceEdges[] deadlockNodes) {
        ResourceEdges robNode = deadlockNodes[0];
        PCB robPCB = null;
        PCB allocatePCB = null;
        for (int i = 0; i < robNode.getAllocationEdges().length; ++i) {
            // 剥夺该死锁进程的所有已分配资源
            if(robNode.getAllocationEdges()[i] != 0) {
                for (int j = 1; j < deadlockNodes.length; ++i) {
                    // 如果存在其他死锁进程申请这个资源，则进行重分配
                    if (deadlockNodes[j].getRequestEdges()[i] != 0) {
                        // 剥夺已分配资源
                        this.removeAllocation(robNode.getProcessId(), i);
                        this.addRequest(robNode.getProcessId(), i);
                        ++robNode.getRequestEdges()[i];
                        --robNode.getAllocationEdges()[i];
                        // 分配已剥夺资源
                        this.addAllocation(deadlockNodes[j].getProcessId(), i);
                        this.removeRequest(deadlockNodes[j].getProcessId(), i);
                        ++deadlockNodes[j].getRequestEdges()[i];
                        --deadlockNodes[j].getAllocationEdges()[i];
                        this.manager.getDashboard().consoleInfo("剥夺资源 " + i +
                                " 进程 " + robNode.getProcessId() +
                                " -> 进程 " + deadlockNodes[j].getProcessId());
                        synchronized (this.manager.getSchedule()) {
                            Vector<PCB> allPCBQueue = this.manager.getSchedule().getAllPCBQueue();
                            Vector<PCB> selectedBlockQueue = this.manager.getSchedule().getResourceBlockQueues()[i];
                            for (int k = 0; k < allPCBQueue.size(); ++k) {
                                if (robPCB == null && allPCBQueue.get(k).getId() == robNode.getProcessId()) {
                                    robPCB = allPCBQueue.get(k);
                                }
                                if (allocatePCB == null && allPCBQueue.get(k).getId() == deadlockNodes[j].getProcessId()) {
                                    allocatePCB = allPCBQueue.get(k);
                                }
                                if (robPCB != null && allocatePCB != null) {
                                    break;
                                }
                            }
                            allocatePCB.wakeUp(selectedBlockQueue);
                            selectedBlockQueue.add(0, robPCB);

                            allocatePCB = null;
                        }
                        break;
                    }
                }

            }
        }
        this.manager.getDashboard().consoleSuccess("死锁已解除");
    }

    /**
     * 资源请求分配边 结构
     */
    private class ResourceEdges {
        private int processId;
        private int[] requestEdges;
        private int[] allocationEdges;

        public ResourceEdges(int processId) {
            this.processId = processId;
            this.requestEdges = new int[RESOURCE_TYPE_NUM];
            this.allocationEdges = new int[RESOURCE_TYPE_NUM];
        }

        /**
         * 添加请求边
         * @param resourceType 资源类型
         */
        public void addRequestEdge(int resourceType) {
            ++this.requestEdges[resourceType];
        }

        /**
         * 删除请求边
         * @param resourceType 资源类型
         */
        public void removeRequestEdge(int resourceType) {
            --this.requestEdges[resourceType];
        }

        /**
         * 添加分配边
         * @param resourceType 资源类型
         */
        public void addAllocationEdge(int resourceType) {
            ++this.allocationEdges[resourceType];
        }

        /**
         * 删除分配边
         * @param resourceType 资源类型
         */
        public void removeAllocationEdge(int resourceType) {
            --this.allocationEdges[resourceType];
        }

        /**
         * 释放所有分配边
         * @param currentAvailable 当前可分配资源数组
         */
        public void releaseAllAllocationEdge(int[] currentAvailable) {
            for (int i = 0; i < this.allocationEdges.length; ++i) {
                currentAvailable[i] += this.allocationEdges[i];
            }
        }

        /**
         * 是否只有请求边
         * @return 是否
         */
        public boolean isOnlyRequest() {
            int requestSum = 0;
            int allocationSum = 0;
            for (int i = 0; i < this.requestEdges.length; ++i) {
                requestSum += this.requestEdges[i];
            }
            for (int i = 0; i < this.allocationEdges.length; ++i) {
                allocationSum += this.allocationEdges[i];
            }
            return requestSum != 0 && allocationSum == 0;
        }

        /**
         * 是否只有分配边
         * @return 是否
         */
        public boolean isOnlyAllocation() {
            int requestSum = 0;
            int allocationSum = 0;
            for (int i = 0; i < this.requestEdges.length; ++i) {
                requestSum += this.requestEdges[i];
            }
            for (int i = 0; i < this.allocationEdges.length; ++i) {
                allocationSum += this.allocationEdges[i];
            }
            return requestSum == 0 && allocationSum != 0;
        }

        public int getProcessId() {
            return processId;
        }

        public void setProcessId(int processId) {
            this.processId = processId;
        }

        public int[] getRequestEdges() {
            return requestEdges;
        }

        public void setRequestEdges(int[] requestEdges) {
            this.requestEdges = requestEdges;
        }

        public int[] getAllocationEdges() {
            return allocationEdges;
        }

        public void setAllocationEdges(int[] allocationEdges) {
            this.allocationEdges = allocationEdges;
        }
    }

}
