package Vio;

public class MetaPage {
    public static int[] buf;

    public static int pagenum_let; //从0开始
    public static int pagenum_gar; //从0开始
    public static int nextpageid_let; //从1开始
    public static int nextpageid_gar; //从1开始
    public static int rootpageid; //从0开始
    public static int first_leaf_id; //从0开始
    public static int leaf_num; //从0开始
    public static int INSERT_FACTOR; //目前设置为58

    MetaPage(byte[] buffer){ //由table调用，table通过配置文件初始化page 0，再传入table专用的buffer，再传给MetaPage初始化
        buf = new int[Page.PAGE_SIZE / 4];
        Basic.turnPageofBytesIntoInts(buffer, buf);
        pagenum_let = buf[1];
        pagenum_gar = buf[2];
        nextpageid_let = buf[3];
        nextpageid_gar = buf[4];
        rootpageid = buf[5];
        first_leaf_id = buf[6];
        leaf_num = buffer[7];
        INSERT_FACTOR = buffer[8];
    }
}
