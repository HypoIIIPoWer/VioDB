package Vio;


import java.nio.ByteBuffer;

public class Page {

    public static final int PAGE_SIZE = 4096;
    public static final int HEAD_SIZE = 16; //bytes
    public int frameid;
    public int pageid;

    public boolean is_dirty = false;
    public int pin_count = 0;
    public byte[] buf;
    public ByteBuffer view;

    public class PageHeader{
        byte level;
        byte is_leaf; // 1 -> true , 0 -> false
        byte empty_left1;
        byte empty_left2;
        int rsibling;
        int num;
        int pageid;
        int len;
    }

    Page(){
        buf = new byte[PAGE_SIZE];
        view = ByteBuffer.wrap(buf, 0 , PAGE_SIZE);
    }

}
