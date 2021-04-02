package hardware;

/**
 * 数据线
 *
 * 用以独写存储单元数据的过渡
 *
 * @author ZJC
 */
public class DataLine {
    private short data;

    public DataLine() {
        this.data = 0;
    }

    public DataLine(short data) {
        this.data = data;
    }

    public synchronized short getData() {
        return data;
    }

    public synchronized void setData(short data) {
        this.data = data;
    }
}
