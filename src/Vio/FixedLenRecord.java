package Vio;

public class FixedLenRecord{
    byte[] key;
    int key_len;
    byte[] value;
    short value_len;
    ComparatorFactory.KeyComparator kc;
    ComparatorFactory.ValueComparator vc;
    SearcherFactory.KeySearcher ks;
}
