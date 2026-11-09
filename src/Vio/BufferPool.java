package Vio;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/*
 *
 *   第0页为元页，存储在.let文件中(其实也可以存储在.gar文件中)
 *   正id页为索引页，存储索引在.let文件中(从1开始)
 *   负id页为记录页，存储记录在.gar文件中(从-1开始)
 *
 *
 *   interface:
 *   getPage(int pageid) -> Page                    获取id页,如果在缓冲池中就从缓冲池中获取，否则在文件中读取
 *   unpin(int pageid, boolean dirty) -> void       unpin第id页，同时将is_dirty标志位设置为dirty
 *   allocatePage(boolean is_let) -> Page           自动分配页，is_let为true即分配索引页，false分配记录页
 *
 */


@SuppressWarnings ("unused")
public class BufferPool {
    private static final Path ROOT_PATH = getRootPath();
    private static final int MAX_FRAME_NUM = 20;


    private static Path getRootPath(){
        URL location = Main.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            throw new IllegalStateException("cant get class location");
        }
        try {
            return Path.of(location.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cant parse rootpath of database:" + location, e);
        }
    }


    private final String tablename;
    private int clock_pointer = 0;
    private Map<Integer, Integer> pageframehash; //pageid -> frameid (a hashtable)
    private ArrayDeque<Integer> freeframes; //a deque restores free frames
    private byte[] clock; //restore bools that signs if a frame is used lately
    private Page[] pool; //restore pages
    FileChannel ch_let;
    FileChannel ch_gar;




    BufferPool(String name) throws IOException{
        tablename = name;

        Path file_let = ROOT_PATH.resolve(tablename + ".let");
        Path file_gar = ROOT_PATH.resolve(tablename + ".gar");
        ch_let = FileChannel.open(file_let,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        ch_gar = FileChannel.open(file_gar,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);

        freeframes = new ArrayDeque<>();
        pool = new Page[MAX_FRAME_NUM];
        clock = new byte[MAX_FRAME_NUM];
        for(int i = 0; i < MAX_FRAME_NUM; i++){
            clock[i] = 0;
            freeframes.add(i);
            pool[i] = new Page();
        }
        pageframehash = new HashMap<>();
    }


    private void closeMetaPage() throws IOException {
        MetaPage.buf[0] = MetaPage.pagenum_let;
        MetaPage.buf[1] = MetaPage.pagenum_gar;
        MetaPage.buf[2] = MetaPage.nextpageid_let;
        MetaPage.buf[3] = MetaPage.nextpageid_gar;
        ByteBuffer out = ByteBuffer.allocateDirect(Page.PAGE_SIZE);
        out.putInt(MetaPage.pagenum_let);
        out.putInt(MetaPage.pagenum_gar);
        out.putInt(MetaPage.nextpageid_let);
        out.putInt(MetaPage.nextpageid_gar);
        while (out.hasRemaining()) ch_let.write(out, out.position());
    }

    private boolean poolIsFull(){
        return (freeframes.isEmpty());
    }

    private int killFrame() throws IOException {   //return killed frame which is free now

        int timeout = 0;

        while(true){ //a loop that makes the pointer point at the frame we wanna kill by CLOCK
            if(pool[clock_pointer].pin_count > 0){
                clock_pointer = (clock_pointer + 1) % MAX_FRAME_NUM;
                if(timeout >= MAX_FRAME_NUM){
                    throw new DBException("every frame is pinned ,cant kill ,need more frames");
                }
                timeout ++;
                continue;
            }
            if(clock[clock_pointer] == 0){
                break;
            }else{
                clock[clock_pointer] = 0;
            }

            clock_pointer = (clock_pointer + 1) % MAX_FRAME_NUM;
        }


        int victim = clock_pointer;
        int killedpageid = pool[victim].pageid;
        if(pool[victim].is_dirty){
            updatePage(killedpageid);
        }
        pool[victim].is_dirty = false;
        pageframehash.remove(killedpageid);
        clock_pointer = (clock_pointer + 1) % MAX_FRAME_NUM;
        return victim;
    }

    public Page getPage(int pageid) throws IOException{
        if (pageid == 0) throw new DBException("page 0 is reserved for meta");//prevent user from getting metapage

        int newframeid;

        if(pageframehash.get(pageid) != null){  //if found in pool, quit and return
            newframeid = pageframehash.get(pageid);
            pool[newframeid].pin_count ++;
            clock[newframeid] = 1;
            return pool[newframeid];
        }


        if(poolIsFull()){   //else get it from file
            newframeid = killFrame();
        }else{
            newframeid = freeframes.removeFirst();
        }

        pool[newframeid].pageid = pageid;
        pool[newframeid].frameid = newframeid;
        pool[newframeid].pin_count ++;
        clock[newframeid] = 1;
        pageframehash.put(pageid, newframeid);



        if(pageid >= 0){
            pool[newframeid].view.clear();
            long pos = (long)pageid * Page.PAGE_SIZE;
            int totalRead = 0;
            while (pool[newframeid].view.hasRemaining()) {
                int n = ch_let.read(pool[newframeid].view, pos + totalRead);
                if (n <= 0) throw new IOException("EOF or fail to read: pos=" + (pos + totalRead));
                totalRead += n;
            }
        }else{
            pool[newframeid].view.clear();
            long pos = (long)(-pageid) * Page.PAGE_SIZE;
            int totalRead = 0;
            while (pool[newframeid].view.hasRemaining()) {
                int n = ch_gar.read(pool[newframeid].view, pos + totalRead);
                if (n <= 0) throw new IOException("EOF or fail to read: pos=" + (pos + totalRead));
                totalRead += n;
            }
        }


        return pool[newframeid];
    }

    public Page allocatePage(boolean is_let) throws IOException{
        if(is_let){
            Page newpage = new Page();
            int newpageid = MetaPage.nextpageid_let;
            int newframeid;
            if(poolIsFull()){
                newframeid = killFrame();
            }else{
                newframeid = freeframes.removeFirst();
            }
            pool[newframeid] = newpage;
            newpage.pageid = newpageid;
            newpage.frameid = newframeid;
            newpage.pin_count = 1;
            clock[newframeid] = 1;
            pageframehash.put(newpageid, newframeid);

            pool[newframeid].view.clear();
            long pos = (long)newpageid * Page.PAGE_SIZE;
            int totalRead = 0;
            while (pool[newframeid].view.hasRemaining()) {
                int n = ch_let.write(pool[newframeid].view, pos + totalRead);
                if (n <= 0) throw new IOException("EOF or fail to write: pos=" + (pos + totalRead));
                totalRead += n;
            }

            MetaPage.pagenum_let++;
            MetaPage.nextpageid_let++;
            return newpage;
        }else{
            Page newpage = new Page();
            int newpageid = MetaPage.nextpageid_gar;
            int newframeid;
            if(poolIsFull()){
                newframeid = killFrame();
            }else{
                newframeid = freeframes.removeFirst();
            }
            pool[newframeid] = newpage;
            newpage.pageid = newpageid;
            newpage.frameid = newframeid;
            newpage.pin_count = 1;
            clock[newframeid] = 1;
            pageframehash.put(newpageid, newframeid);

            pool[newframeid].view.clear();
            long pos = (long)(-newpageid) * Page.PAGE_SIZE;
            int totalRead = 0;
            while (pool[newframeid].view.hasRemaining()) {
                int n = ch_gar.write(pool[newframeid].view, pos + totalRead);
                if (n <= 0) throw new IOException("EOF or fail to write: pos=" + (pos + totalRead));
                totalRead += n;
            }

            MetaPage.pagenum_gar++;
            MetaPage.nextpageid_gar--;
            return newpage;
        }
    }

    public void unpin(int pageid, boolean dirty){
        Integer frameid = pageframehash.get(pageid);
        if (frameid == null) {
            throw new DBException("cant unpin a page isnt in pool");
        }
        pool[frameid].pin_count --;
        pool[frameid].is_dirty |= dirty;
    }

    private void updatePage(int pageid) throws IOException{
        int frameid = pageframehash.get(pageid);

        if(pageid >= 0){
            pool[frameid].view.clear();
            long pos = (long)pageid * Page.PAGE_SIZE;
            int totalRead = 0;
            while (pool[frameid].view.hasRemaining()) {
                int n = ch_let.write(pool[frameid].view, pos + totalRead);
                if (n <= 0) throw new IOException("EOF or fail to write: pos=" + (pos + totalRead));
                totalRead += n;
            }
        }else{
            pool[frameid].view.clear();
            long pos = (long)(-pageid) * Page.PAGE_SIZE;
            int totalRead = 0;
            while (pool[frameid].view.hasRemaining()) {
                int n = ch_gar.write(pool[frameid].view, pos + totalRead);
                if (n <= 0) throw new IOException("EOF or fail to write: pos=" + (pos + totalRead));
                totalRead += n;
            }
        }

        pool[frameid].is_dirty = false;
    }


    public void closeBufferPool() throws IOException{
        for (Integer frameid : pageframehash.values()) {    //flush all dirty pages
            if (pool[frameid].is_dirty) updatePage(pool[frameid].pageid);
        }
        closeMetaPage();
        ch_let.close();
        ch_gar.close();
    }
}