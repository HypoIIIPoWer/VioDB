package Vio;


import java.nio.ByteBuffer;

public class Page {

    public static final int PAGE_SIZE = 4096;
    public int frameid;
    public int pageid;

    public boolean is_dirty = false;
    public int pin_count = 0;
    public byte[] buf;
    public ByteBuffer view;

    Page(){
        buf = new byte[PAGE_SIZE];
        view = ByteBuffer.wrap(buf, 0 , PAGE_SIZE);
    }

}
