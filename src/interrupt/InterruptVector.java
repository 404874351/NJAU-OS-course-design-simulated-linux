package interrupt;

/**
 * 中断向量表
 *
 * 用以检索不同向量的类型，提供给CPU进行处理
 *
 * @author ZJC
 */
public interface InterruptVector {
    // 时钟中断向量
    int CLOCK_INTERRUPT = 0;
    // 缺页中断向量
    int MISS_PAGE_INTERRUPT = 1;
    // 资源申请向量
    int APPLY_RESOURCE_INTERRUPT = 2;
    // 资源释放向量
    int RELEASE_RESOURCE_INTERRUPT = 3;
    // 输入操作向量
    int INPUT_INTERRUPT = 4;
    // 输出操作向量
    int OUTPUT_INTERRUPT = 5;
    // 作业请求向量
    int JOB_REQUEST_INTERRUPT = 6;
    // 文件创建向量
    int CREATE_FILE_INTERRUPT = 7;
    // 文件关闭向量
    int CLOSE_FILE_INTERRUPT = 8;
}
