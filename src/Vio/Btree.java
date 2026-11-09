package Vio;


import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;

@SuppressWarnings ("unused")
public class Btree {
    //use BIG_ENDIAN

    public static final VarHandle BYTE = MethodHandles.byteArrayViewVarHandle(byte[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    private final int INDEX_LEN;
    private final int INSERT_FACTOR = 9;  //在一开始只装90%防止后续一直要分裂
    private String filename;
    private BufferPool bp;
    private ComparatorFactory.KeyComparator kc;
    private final int[] metaPage;
    private int rootid;
    private int leaf_num;
    private final int ORDER = 0; // TODO:think about a way to measure ORDER of the tree!!!

    Btree(ComparatorFactory.KeyComparator keycomp, int index_len, String name) throws IOException {
        INDEX_LEN = index_len;
        filename = name;
        bp = new BufferPool(filename);
        metaPage = bp.metaPage;
        rootid = (int)INT.get(metaPage, 4);
        kc = keycomp;

    }

    private enum NodeType{
        ROOT(0),
        INNER(1),
        LEAF(2);

        int type;
        NodeType(int tp){
            type = tp;
        }
    }

    private class Node{
        byte[] buf;
        int pageid;

        Node(byte[] buffer){
            buf = buffer;
            pageid = (int)INT.get(buf, 3);
        }

        public Node turnInto(int id) throws IOException{
            Node newnode = new Node(buf = bp.getPage(id).buf);
            newnode.pageid = id;
            return newnode;
        }

        public byte getLevel(){
            return buf[0];
        }

        public boolean is_leaf(){
            return (this.buf[1] == 1);
        }

        public boolean is_let(){
            return (this.buf[2] == 1);
        }

        public int getRightSibiling(){
            return (int)INT.get(buf, 1);
        }

        public int getKeyNum(){
            return (int)INT.get(buf, 2);
        }

        public void selfPlusKeyNum(){
            short sewed = Basic.sew2BytesIntoShort(buf[2], buf[3]);
            sewed++;
            buf[2] = Basic.get1PartofSewedShort(sewed);
            buf[3] = Basic.get2PartofSewedShort(sewed);
        }

        public int getValueNum(){
            return (int)INT.get(buf, 2);
        }

        public void selfPlusValueNum(){
            short sewed = Basic.sew2BytesIntoShort(buf[2], buf[3]);
            sewed++;
            buf[2] = Basic.get1PartofSewedShort(sewed);
            buf[3] = Basic.get2PartofSewedShort(sewed);
        }

        public int getKeyLen(){
            return (int)INT.get(buf, 4);
        }

        public int getValueLen(){
            return (int)INT.get(buf, 4);
        }

        public int getPageId(){
            return (int)INT.get(buf, 3);
        }

        public void writeBack(boolean dirty){
            bp.unpin(pageid, dirty);
        }

        public boolean is_full(){
            return is_let() ? let_is_full() : gar_is_full();
        }

        public boolean let_is_full(){
            return (getKeyNum() == ORDER - 1);
        }

        public boolean gar_is_full(){
            return ((getValueNum() + 1) * getValueLen() + Page.HEAD_SIZE >= Page.PAGE_SIZE);
        }

        public NodeType type(){
            byte level = getLevel();
            if(level == 0){
                return NodeType.ROOT;
            }else if(this.is_leaf()){
                return NodeType.LEAF;
            }else return NodeType.INNER;
        }

    }


    private class metaNode{     //叶子层的链表头
        int first_leaf_id;

        metaNode(){

        }
    }

    private boolean tree_is_empty(){
        return (leaf_num == 0);
    }

    public void insert(FixedLenRecord record) throws IOException{

        Node rootnode;
        if(tree_is_empty()){
            rootnode = new Node(bp.allocatePage(true).buf);
            //TODO:need to add more things!!!
        }else {
            rootnode = new Node(bp.getPage(rootid).buf);
        }
        Deque<Integer> node_stack = new ArrayDeque<>();
        node_stack.push(rootid);
        int dest_let_page = btreeInsert(rootnode, record, node_stack);
    }

    private int createNode(){
        //TODO:create a new node and return pageid!!!
        return 0;
    }

    private int btreeInsert(Node node, FixedLenRecord record, Deque<Integer> node_stack) throws IOException { //定长记录
        if(node.is_leaf()){ //如果是叶子节点
            int pos = record.ks.keySearch(node.buf, record.key_len, record.key, record.kc);
            leafPageWriteIn(node, record, pos);
            return node.pageid;
        }
        //如果不是叶子节点
        int pos = record.ks.keySearch(node.buf, record.key_len, record.key, record.kc);
        node_stack.push(node.pageid);
        byte[] sub = new byte[4];
        System.arraycopy(node.buf, Page.HEAD_SIZE + pos * (record.key_len + 4), sub, 0, 4);
        int nextpageid = (int)INT.get(sub);
        node_stack.push(node.pageid);
        return btreeInsert(node.turnInto(nextpageid), record, node_stack);
    }

    private void updateFatherNodes(){
        //TODO:recursively update father nodes!!!
    }

    private int makeLetPage(){
        //TODO:
        return 0;
    }

    private int makeGarPage(){
        //TODO:
        return 0;
    }

    private void leafPageWriteIn(Node node, FixedLenRecord record, int pos) throws IOException{ //定长
        int start = Page.HEAD_SIZE + (record.key_len + 4) * (pos + 1);
        for(int i = 0; i < record.key_len; i++){
            node.buf[start + i] = record.key[i];
        }
        start += record.key_len;
        int sewedGarPageid = garPageWriteIn(record);
        INT.set(node.buf, start, sewedGarPageid);
        node.writeBack(true);
    }

    private int garPageWriteIn(FixedLenRecord record) throws IOException{ //定长
        int pageid = -getGarNum();
        Node node = new Node(bp.getPage(pageid).buf);
        if(!node.gar_is_full()){
            int pos = node.getValueNum() * record.value_len + Page.HEAD_SIZE;
            System.arraycopy(node.buf, pos, record.value, 0, record.value_len);
            int sewedGarPageid = Basic.sew2ShortsIntoInt((short)pageid, (short)node.getValueNum());
            node.selfPlusValueNum();
            node.writeBack(true);
            return sewedGarPageid;
        }else {

        }
    }

}
