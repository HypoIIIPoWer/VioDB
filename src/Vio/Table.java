package Vio;

import java.io.IOException;

public class Table {
    public static final int TKBYTE = 0;
    public static final int TKSHORT = 1;
    public static final int TKINT = 2;
    public static final int TKLONG = 3;

    public enum Token{
        bytetoken(0),
        shorttoken(1),
        inttoken(2),
        longtoken(3);

        int type;

        Token(int n){
            type = n;
        }

        int type(){
            return type;
        }
    }

    private String tablename;


    public class Column{

    }

    public class Index{
        int Index_len;
        ComparatorFactory.KeyComparator kc = new ComparatorFactory.BigEndianIntCom(); //暂时的
    }

    public class Tuple{
        Index indx;
        Column colm;
        Tuple(Column column, Index index){
               colm = column;
               indx = index;
        }
    }

    Table() throws IOException {
    }
}
