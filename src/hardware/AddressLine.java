package hardware;

/**
 * 地址线
 *
 * 用于地址访问存储单元的操作
 *
 * @author ZJC
 */
public class AddressLine {
    private short address;

    public AddressLine() {
        this.address = 0;
    }

    public AddressLine(short address) {
        this.address = address;
    }

    public synchronized short getAddress() {
        return address;
    }

    public synchronized void setAddress(short address) {
        this.address = address;
    }
}
