package kernel;

import hardware.ExternalMem;
import hardware.InternalMem;

import java.io.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

/**
 * 作业管理
 *
 * 负责作业的相关操作
 *
 * @author ZJC
 */
public class JobManage {
    /**
     * 调度模块
     */
    private Schedule schedule;
    /**
     * 作业总数（系统已读取）
     */
    private int totalJobNum;
    /**
     * 作业请求文件的行数
     */
    private int inputFileLineNum;

    public JobManage(Schedule schedule) {
        this.schedule = schedule;
        this.totalJobNum = 0;
        this.inputFileLineNum = 0;

        // 初始化作业请求文件（这一步是为了兼容默认存在的8个作业请求）
        File jobsInputFile = new File("./19318220-jobs-input.txt");
        try {
            String defaultContent = "";
            String lineData = "";
            // 获取作业请求文件行数
            BufferedReader jobReader = new BufferedReader(new FileReader(jobsInputFile));
            while ((lineData = jobReader.readLine()) != null) {
                ++this.inputFileLineNum;
                if (this.inputFileLineNum <= 9) {
                    defaultContent += lineData + "\n";
                }
            }
            // 去除最后的\n
            defaultContent = defaultContent.substring(0, defaultContent.length() - 1);
            jobReader.close();
            // 如果发现多于8个的作业请求，则只保留前8个
            if (this.inputFileLineNum > 9) {
                BufferedWriter jobWriter = new BufferedWriter(new FileWriter(jobsInputFile));
                jobWriter.write(defaultContent);
                jobWriter.close();
                for (int i = 9; i < this.inputFileLineNum; i++) {
                    File instructionSetFile = new File("./" + i + ".txt");
                    instructionSetFile.delete();
                }
                this.inputFileLineNum = 9;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 添加作业请求
     *
     * 随机生成新的作业请求，追加到xxx-job-input.txt文件中
     */
    public synchronized void addJob() {
        File jobsInputFile = new File("./19318220-jobs-input.txt");
        try {
            // 设置追加写入txt
            BufferedWriter appendJob = new BufferedWriter(new FileWriter(jobsInputFile,true));
            // 随机生成新作业信息
            int jobId = this.inputFileLineNum;
            int priority = new Random().nextInt(5) + 1;
            int inTime = this.schedule.getManager().getClock().getCurrentTime();
            int instructionNum = new Random().nextInt(31) + 30;
            // 随机分配页面：PCB 1; code 1; stack 1; data 2-10;
            int pcbPageNum = 1;
            int codeSegmentPageNum = (instructionNum / 64) + 1;
            int stackSegmentPageNum = 1;
            int dataSegmentPageNum = new Random().nextInt(9) + 2;
            int needPageNum =  pcbPageNum + codeSegmentPageNum + dataSegmentPageNum + stackSegmentPageNum;
            // 追加新行
            appendJob.newLine();
            appendJob.write(jobId + "," + priority + "," + inTime + "," + instructionNum + "," + needPageNum);
            // 关闭文件输出流，否则无法写入
            appendJob.close();
            // 生成对应的指令集文件
            this.addInstructionSet(jobId, instructionNum, dataSegmentPageNum);
            // 文件行数 +1
            ++this.inputFileLineNum;

            this.schedule.getManager().getDashboard().consoleLog( "添加作业 " + jobId + "," + priority + "," + inTime + "," + instructionNum + "," + needPageNum);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 添加指令集文件
     *
     * 指令设计如下：
     * 0 system     系统调用，本系统中仿真为输入、输出、创建文件、关闭文件
     * 1 calculate  计算指令，CPU不需要调用任何额外资源，可直接运行
     * 2 load       读取指令，对内存数据进行读取
     * 3 store      写入指令，对内存数据进行写入
     * 4 switch     进程切换，直接进行调度
     * 5 jump       跳转指令，跳过代码段执行
     * 6 apply      资源申请，申请一个系统资源
     * 7 release    资源释放，释放一个系统资源
     *
     * @param jobId 作业id
     * @param instructionNum 作业指令数
     * @param dataSegmentPageNum 数据段页数，访存指令只可以访问数据段
     */
    public void addInstructionSet(int jobId, int instructionNum, int dataSegmentPageNum) {
        File instructions = new File("./" + jobId + ".txt");
        Instruction[] allInstructions = new Instruction[instructionNum];
        try {
            int id;
            int state;
            int argument;
            String extra;
            for (int i = 0; i < instructionNum; ++i) {
                /**
                 * 启动时，提前创建文件系统框架
                 */
                id = i + 1;
                // 随机指令类型 1-5，特殊类型的指令需要分别处理，资源类指令后续统一添加
                state = new Random().nextInt(5) + 1;
                argument = 0;
                extra = ".";
                if (state == 2 || state == 3) {
                    // 访存指令,参数设置为访问逻辑地址，仅偶数
                    argument = new Random().nextInt(dataSegmentPageNum * InternalMem.PAGE_SIZE);
                    if (argument % 2 == 1) {
                        --argument;
                    }
                    argument += 3 * InternalMem.PAGE_SIZE;

                } else if (state == 5) {
                    // 跳转指令，参数设置为下一跳指令序号，即跳过一条指令
                    argument = id + 1;
                }
                allInstructions[i] = new Instruction(id, state, argument, extra);
            }

            // 统一添加系统调用类指令
            // 0 创建文件、1 输入、2 输出、3 关闭文件
            int createNum = new Random().nextInt(3);
            int closeNum = createNum;
            int inputNum = createNum == 0 ? 0 : new Random().nextInt(3);
            int outputNum = createNum == 0 ? 0 : new Random().nextInt(3);
            // 获取可供替换的指令序号List
            Vector<Integer> systemInstructionIndexList = this.getSystemInstructionIndex(allInstructions, createNum + closeNum + inputNum + outputNum);
            Vector<String> openFileList = new Vector<>();
            // 随机替换成若干组系统调用类指令
            for (int i = 0; i < createNum; i++) {
                int createIndex = systemInstructionIndexList.get(0).intValue();
                systemInstructionIndexList.remove(0);
                allInstructions[createIndex].setState(0);
                allInstructions[createIndex].setArgument(0);
                allInstructions[createIndex].setExtra("/home/job_" + jobId + "/file_" + (createIndex + 1));
                // 保存文件名，为输入输出指令提供选择
                openFileList.add("/home/job_" + jobId + "/file_" + (createIndex + 1));
            }
            for (int i = 0; i < inputNum; i++) {
                int inputIndex = systemInstructionIndexList.get(0).intValue();
                systemInstructionIndexList.remove(0);
                allInstructions[inputIndex].setState(0);
                allInstructions[inputIndex].setArgument(1);
                allInstructions[inputIndex].setExtra(new Random().nextInt(dataSegmentPageNum) + " " + openFileList.get(new Random().nextInt(openFileList.size())));
            }
            for (int i = 0; i < outputNum; i++) {
                int outputIndex = systemInstructionIndexList.get(0).intValue();
                systemInstructionIndexList.remove(0);
                allInstructions[outputIndex].setState(0);
                allInstructions[outputIndex].setArgument(2);
                allInstructions[outputIndex].setExtra(new Random().nextInt(dataSegmentPageNum) + " " + openFileList.get(new Random().nextInt(openFileList.size())));
            }
            for (int i = 0; i < closeNum; i++) {
                int closeIndex = systemInstructionIndexList.get(0).intValue();
                systemInstructionIndexList.remove(0);
                allInstructions[closeIndex].setState(0);
                allInstructions[closeIndex].setArgument(3);
                allInstructions[closeIndex].setExtra(openFileList.get(i));
            }

            // 统一添加资源类指令
            int needResourceNum = new Random().nextInt(Deadlock.RESOURCE_TYPE_NUM + 1);
            int[] resouceTypes = new int[Deadlock.RESOURCE_TYPE_NUM];
            this.schedule.getManager().getDashboard().consoleLog("作业 " + jobId + " 共请求资源数 " + needResourceNum);
            // 获取可供替换的指令序号List
            Vector<Integer> resourceInstructionIndexList = this.getResourceInstructionIndex(allInstructions, 2 * needResourceNum);
            // 随机替换成若干组资源类指令
            while(resourceInstructionIndexList.size() > 0) {
                // 随机抽取两条作为申请和释放指令
                int tempIndex = new Random().nextInt(resourceInstructionIndexList.size());
                int applyIndex = resourceInstructionIndexList.get(tempIndex).intValue();
                resourceInstructionIndexList.remove(tempIndex);

                tempIndex = new Random().nextInt(resourceInstructionIndexList.size());
                int releaseIndex = resourceInstructionIndexList.get(tempIndex).intValue();
                resourceInstructionIndexList.remove(tempIndex);
                // 保证申请在释放之前
                if (applyIndex > releaseIndex) {
                    int temp = applyIndex;
                    applyIndex = releaseIndex;
                    releaseIndex = temp;
                }
                // 选择申请资源的类型
                int typeIndex = new Random().nextInt(Deadlock.RESOURCE_TYPE_NUM);
                // 如果该资源已经申请，则重新选择
                while (resouceTypes[typeIndex] != 0) {
                    typeIndex = new Random().nextInt(Deadlock.RESOURCE_TYPE_NUM);
                }
                resouceTypes[typeIndex] = 1;

                // 替换指令
                allInstructions[applyIndex].setState(6);
                allInstructions[applyIndex].setArgument(typeIndex);
                allInstructions[applyIndex].setExtra("apply " + typeIndex);
                allInstructions[releaseIndex].setState(7);
                allInstructions[releaseIndex].setArgument(typeIndex);
                allInstructions[releaseIndex].setExtra("release " + typeIndex);
            }

            // 写入指令集文件
            BufferedWriter addInstructions = new BufferedWriter(new FileWriter(instructions));
            addInstructions.write("id,state,argument,extra");
            for (int i = 0; i < instructionNum; ++i) {
                addInstructions.newLine();
                addInstructions.write(allInstructions[i].toString());
            }
            addInstructions.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 系统读取作业，进入后备队列
     */
    public synchronized void readJobs() {
        File jobsInputFile = new File("./19318220-jobs-input.txt");
        try {
            // 读取作业请求文件
            BufferedReader jobReader = new BufferedReader(new FileReader(jobsInputFile));
            // 跳过表头和已读取的job
            for (int i = 0; i < this.totalJobNum + 1; ++i) {
                jobReader.readLine();
            }
            String jobContent;
            String[] jobInfo;
            while ((jobContent = jobReader.readLine()) != null) {
                // 依次读取新增作业，默认作业的进入时间是递增的
                jobInfo = jobContent.split(",");
                short jobId = Short.parseShort(jobInfo[0]);
                short priority = Short.parseShort(jobInfo[1]);
                short inTime = Short.parseShort(jobInfo[2]);
                short instructionNum = Short.parseShort(jobInfo[3]);
                short needPageNum = Short.parseShort(jobInfo[4]);

                // 作业请求进入时间 > 当前时间，则不创建新作业（这一步是为了兼容默认存在的8个作业请求）
                if ((int)inTime > this.schedule.getManager().getClock().getCurrentTime()) {
                    break;
                }

                // 创建JCB用于存储
                JCB jcb = new JCB(jobId, priority, inTime, instructionNum, needPageNum);
                // 读取指令指令集文件
                Instruction[] instructions = this.readInstructionSet(jobId, instructionNum);
                jcb.setInstructions(instructions);
                // 将作业信息保存到外存
                this.saveJobToDisk(jcb);
                // 系统作业数 +1
                ++this.totalJobNum;

                this.schedule.getManager().getDashboard().consoleLog("读取作业 " + jobId);
            }
            jobReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取指令集
     * @param jobsId 作业id
     * @param instructionNum 指令数
     * @return 指令集数组
     */
    public Instruction[] readInstructionSet(int jobsId, int instructionNum) {
        File instructionFile = new File("./" + jobsId + ".txt");
        try {
            // 读取对应指令集文件
            BufferedReader instructionReader = new BufferedReader(new FileReader(instructionFile));
            // 跳过表头
            instructionReader.readLine();

            Instruction[] instructions = new Instruction[instructionNum];
            for (int i = 0; i < instructionNum; ++i) {
                String[] instructionInfo = instructionReader.readLine().split(",");
                int id = Integer.parseInt(instructionInfo[0]);
                int state = Integer.parseInt(instructionInfo[1]);
                int argument = Integer.parseInt(instructionInfo[2]);
                String extra = instructionInfo[3];
                instructions[i] = new Instruction(id, state, argument, extra);
            }
            instructionReader.close();
            return instructions;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 保存到外存
     * @param jcb 作业控制块
     */
    public void saveJobToDisk(JCB jcb) {
        // 获取外存JCB区所需块号
        int blockNo = ExternalMem.JCB_AREA_START_BLOCK_NO + this.totalJobNum;
        // 设置JCB所在外存地址和需求页数
        jcb.setExternalMemeryAddress(blockNo * ExternalMem.SECTOR_SIZE);

        // 存储JCB信息，每个JCB占一块（一个扇区）
        Page page = new Page();
        page.setExternalBlockNo(blockNo);
        page.getData()[0] = (byte)jcb.getId();
        page.getData()[1] = (byte)(jcb.getId() >> 8);
        page.getData()[2] = (byte)jcb.getPriority();
        page.getData()[3] = (byte)(jcb.getPriority() >> 8);
        page.getData()[4] = (byte)jcb.getInTime();
        page.getData()[5] = (byte)(jcb.getInTime() >> 8);
        page.getData()[6] = (byte)jcb.getInstructionNum();
        page.getData()[7] = (byte)(jcb.getInstructionNum() >> 8);
        page.getData()[8] = (byte)jcb.getExternalMemeryAddress();
        page.getData()[9] = (byte)(jcb.getExternalMemeryAddress() >> 8);
        page.getData()[10] = (byte)(jcb.getExternalMemeryAddress() >> 16);
        page.getData()[11] = (byte)(jcb.getExternalMemeryAddress() >> 24);
        page.getData()[12] = (byte)jcb.getNeedPageNum();
        page.getData()[13] = (byte)(jcb.getNeedPageNum() >> 8);
        page.getData()[14] = (byte)(jcb.getNeedPageNum() >> 16);
        page.getData()[15] = (byte)(jcb.getNeedPageNum() >> 24);
        // 缓冲区写入外存
        this.schedule.getManager().getDeviceManage().useBuffer(page, BufferHead.WRITE);

        // 添加JCB到后备队列
        this.schedule.getReserveQueue().add(jcb);
    }

    /**
     * 从后备队列中尝试寻找可行的作业，将其转化为进程
     */
    public synchronized void tryAddProcess() {
        for (Iterator<JCB> iterator = this.schedule.getReserveQueue().iterator(); iterator.hasNext(); ) {
            JCB tempJcb = iterator.next();
            // 并发进程数超出系统上限，则不创建新进程
            if (this.schedule.getManager().getInMem().getTotalPCBNum() >= Schedule.MAX_CONCURRENT_PROCESS_NUM) {
                break;
            }
            // 创建新进程
            PCB newPCB = new PCB(this.schedule);
            newPCB.create(tempJcb);
            // 后备队列中删除该作业
            iterator.remove();
        }
    }

    /**
     * 获取可设置资源类指令的指令集序号
     * @param allInstructions 指令集
     * @param needLength 所需资源类指令的个数
     * @return 指令集List
     */
    public Vector<Integer> getResourceInstructionIndex(Instruction[] allInstructions, int needLength) {
        // 构造指令集序号List
        Vector<Integer> allInstructionIndexList = new Vector<>();
        for (int i = 0; i < allInstructions.length; i++) {
            allInstructionIndexList.add(new Integer(i));
        }
        // 构造选中的指令序号ist
        Vector<Integer> needIndexList = new Vector<>();
        while (needIndexList.size() < needLength) {
            int index = new Random().nextInt(allInstructionIndexList.size());
            // 如果选中的指令是系统调用类指令，则重新选择
            if (allInstructions[index].getState() == 0) {
                continue;
            }
            // 添加合适的指令序号
            needIndexList.add(new Integer(index));
            allInstructionIndexList.remove(index);
        }
        // List排序
        Collections.sort(needIndexList);
        return needIndexList;
    }

    /**
     * 获取可设置系统调用类指令的指令集序号
     * @param allInstructions 指令集
     * @param needLength 所需系统调用类指令的个数
     * @return 指令集List
     */
    public Vector<Integer> getSystemInstructionIndex(Instruction[] allInstructions, int needLength) {
        // 构造指令集序号List
        Vector<Integer> allInstructionIndexList = new Vector<>();
        for (int i = 0; i < allInstructions.length; i++) {
            allInstructionIndexList.add(new Integer(i));
        }
        // 构造选中的指令序号ist
        Vector<Integer> needIndexList = new Vector<>();
        while (needIndexList.size() < needLength) {
            int index = new Random().nextInt(allInstructionIndexList.size());
            // 如果选中的指令是资源类指令，则重新选择
            if (allInstructions[index].getState() == 6 ||
                allInstructions[index].getState() == 7) {
                continue;
            }
            // 添加合适的指令序号
            needIndexList.add(new Integer(index));
            allInstructionIndexList.remove(index);
        }
        // List排序
        Collections.sort(needIndexList);
        return needIndexList;
    }
}
