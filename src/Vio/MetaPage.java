package Vio;

public class MetaPage {
    public static int[] buf;

    public static int pagenum_let; //从1开始
    public static int pagenum_gar; //从1开始
    public static int nextpageid_let;
    public static int nextpageid_gar;
    public static int rootpageid;
    public static int first_leaf_id;

    MetaPage(byte[] buffer){
        buf = new int[Page.PAGE_SIZE / 4];
        Basic.turnPageofBytesIntoInts(buffer, buf);
        pagenum_let = buf[1];
        pagenum_gar = buf[2];
        nextpageid_let = buf[3];
        nextpageid_gar = buf[4];
        rootpageid = buf[5];
        first_leaf_id = buf[6];
    }
}
