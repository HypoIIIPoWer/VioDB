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
    private final int INSERT_FACTOR = MetaPage.INSERT_FACTOR;  //在一开始只装90%防止后续一直要分裂，通过 * 57 再 >> 6
    private String filename;
    private BufferPool bp;
    private ComparatorFactory.KeyComparator kc;
    private int rootid;
    private final int ORDER = 0; // TODO:think about a way to measure ORDER of the tree!!!

    Btree(ComparatorFactory.KeyComparator keycomp, int index_len, String name) throws IOException {
        INDEX_LEN = index_len;
        filename = name;
        bp = new BufferPool(filename);
        rootid = MetaPage.buf[4];
        kc = keycomp;

    }

    private enum NodeType{
        ROOT(0),
        INNER(1),
        LEAF(2);

        public final int type;
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

        public int getRightSibling(){
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

        public void writeBack(boolean if_dirty){
            bp.unpin(pageid, if_dirty);
        }

        public boolean let_is_totally_full(){
            return (getKeyNum() == ORDER - 1);
        }

        public boolean let_is_almost_full(){
            return (getKeyNum() >= ((ORDER - 1) * INSERT_FACTOR) >> 6);
        }

        public boolean let_is_half_full(){
            return (getKeyNum() * 2 >= (ORDER - 1));
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


    private boolean tree_is_empty(){
        return (MetaPage.leaf_num == 0);
    }

    private void makeRootNode() throws IOException{
        Node rootnode = new Node(bp.allocatePage(true).buf);
        rootnode.pageid = 1;
        BYTE.set(rootnode.buf, 0, 0);
        BYTE.set(rootnode.buf, 1, 1);
        BYTE.set(rootnode.buf, 2, 1);
        BYTE.set(rootnode.buf, 3, 0);
        INT.set(rootnode.buf, 1, 0);
        INT.set(rootnode.buf, 2, 0);
        INT.set(rootnode.buf, 3, rootnode.pageid);
        INT.set(rootnode.buf, 4, 0);
        rootid = 1;
    }

    public void insert(FixedLenRecord record) throws IOException{

        Node rootnode;
        if(tree_is_empty()){
            makeRootNode();
        }
        rootnode = new Node(bp.getPage(rootid).buf);
        Deque<Integer> node_stack = new ArrayDeque<>();
        node_stack.push(rootid);
        int dest_let_page = btreeInsert(rootnode, record, node_stack);
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

    private void updateFatherNodes(Deque<Integer> node_stack){
        //TODO:recursively update father nodes!!!
    }

    private int makeLetPage(FixedLenRecord record, int rsibling) throws IOException{
        Node node = new Node(bp.getPage(MetaPage.nextpageid_let).buf);
        node.pageid = MetaPage.nextpageid_let - 1;
        BYTE.set(node.buf, 0, 0);//等待进一步初始化
        BYTE.set(node.buf, 1, 0);//等待进一步初始化
        BYTE.set(node.buf, 2, 1);
        BYTE.set(node.buf, 3, 0);
        INT.set(node.buf, 1, rsibling);
        INT.set(node.buf, 2, 0);
        INT.set(node.buf, 3, node.pageid);
        INT.set(node.buf, 4, record.key_len);
        return node.pageid;
    }

    private int giveGarPage(FixedLenRecord record) throws IOException{
        Node node = new Node(bp.getPage(MetaPage.pagenum_gar).buf);
        if(!node.gar_is_full()){
            return node.pageid;
        }else {
            node.writeBack(false);
            node = new Node(bp.allocatePage(false ).buf);
            node.pageid = MetaPage.nextpageid_gar + 1;
            BYTE.set(node.buf, 0, -1);
            BYTE.set(node.buf, 1, -1);
            BYTE.set(node.buf, 2, 0);
            BYTE.set(node.buf, 3, 0);
            INT.set(node.buf, 1, -1);
            INT.set(node.buf, 2, 0);
            INT.set(node.buf, 3, node.pageid);
            INT.set(node.buf, 4, record.value_len);
            return node.pageid;
        }
    }

    private void leafPageWriteIn(Node node, FixedLenRecord record, int pos) throws IOException{ //定长
        int start = Page.HEAD_SIZE + (record.key_len + 4) * (pos + 1);
        System.arraycopy(record.key, 0, node.buf, start, record.key_len);
        start += record.key_len;
        int sewedGarPageid = garPageWriteIn(record);
        INT.set(node.buf, start, sewedGarPageid);
        node.writeBack(true);
    }

    private int garPageWriteIn(FixedLenRecord record) throws IOException{ //定长
        int pageid = giveGarPage(record);
        Node node = new Node(bp.getPage(pageid).buf);
        int pos = node.getValueNum() * record.value_len + Page.HEAD_SIZE;
        System.arraycopy(node.buf, pos, record.value, 0, record.value_len);
        int sewedGarPageid = Basic.sew2ShortsIntoInt((short)pageid, (short)node.getValueNum());
        node.selfPlusValueNum();
        node.writeBack(true);
        return sewedGarPageid;
    }

}
