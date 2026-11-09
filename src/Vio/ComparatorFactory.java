package Vio;

public class ComparatorFactory {

    public interface KeyComparator{
        int keycomp(byte[] p1, int o1, int l1, byte[] p2, int o2, int l2);
    }

    public interface ValueComparator{

    }

    public static final class BigEndianIntCom implements KeyComparator{
        @Override
        public int keycomp(byte[] p1, int o1, int l1, byte[] p2, int o2, int l2) { //信任上游，不用长度，偏移为4的倍数
            for (int i = 0; i < 4; i++) {
                int a = p1[o1 + i] & 0xFF, b = p2[o2 + i] & 0xFF;
                if (a != b) return a - b;
            }
            return 0;
        }
    }
}
