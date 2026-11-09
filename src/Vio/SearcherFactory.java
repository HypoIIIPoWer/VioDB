package Vio;


public class SearcherFactory {

    public interface KeySearcher{
        int keySearch(byte[] key, int key_len, byte[] destkey, ComparatorFactory.KeyComparator kc);
    }

    public class FixedLenSeqSearcher implements KeySearcher{

        @Override
        public int keySearch(byte[] buf, int key_len, byte[] destkey, ComparatorFactory.KeyComparator kc) { //返回应该在键序列里的第几个位置，对应页码在前一个int里
            int keyn = (int)Btree.INT.get(buf, 2);
            int start = key_len + Page.HEAD_SIZE + 4; //页头长度 + 高键长度 + 跳过第一个pageid
            int pos = 0;
            for(int i = 0; i < keyn; i++){
                int comp_result = kc.keycomp(buf, start, key_len, destkey, 0, key_len);
                if(comp_result > 0){
                    pos ++;
                    start += key_len + 4;
                }else{
                    break;
                }
            }
            return pos;
        }

    }

}
