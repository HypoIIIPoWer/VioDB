package Vio;


import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

@SuppressWarnings ("unused")
public class Btree {
    //use BIG_ENDIAN

    private static final VarHandle BYTE = MethodHandles.byteArrayViewVarHandle(byte[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    private final int INDEX_LEN;
    private String filename;
    private BufferPool bp;
    private ComparatorFactory.KeyComparator kc;
    private final byte[] metaPage;
    private int rootid;
//    private final int ORDER =

    Btree(ComparatorFactory.KeyComparator keycomp, int index_len, String name) throws IOException {
        filename = name;
        BufferPool bf = new BufferPool(filename);
        metaPage = bf.metaPage;
        rootid = (int)INT.get(metaPage, 4);
        kc = keycomp;
        INDEX_LEN = index_len;
    }


    private class Node{
        byte[] buf;

        Node(int pageid) throws IOException{
            buf = bp.getPage(pageid).buf;
        }

    }


    private class metaNode{
        int first_leaf_id;

        metaNode(){
            first_leaf_id = (int)INT.get(metaPage, 5);
        }
    }


    private class Record{
        Record(){

        }

        ComparatorFactory.KeyComparator kc;
        int Key_len;
    }

    public void insert(Record record){

    }

}
