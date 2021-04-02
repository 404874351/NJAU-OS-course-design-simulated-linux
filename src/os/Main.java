package os;


import java.util.Arrays;

/**
 * main函数
 *
 * 主程序入口
 *
 * @author ZJC
 */
public class Main {
    public static void main(String[] args) {
        // 创建系统管理器
        Manager manager = new Manager();
        // 系统启动完毕，开始运行
        manager.start();
    }

}
