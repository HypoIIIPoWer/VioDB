package Vio;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public class Basic {
    public static final VarHandle BYTE = MethodHandles.byteArrayViewVarHandle(byte[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    public static short sew2BytesIntoShort(byte b1, byte b2){//把两个byte缝合成一个short，b1在前，b2在后
        return (short)((b1 << 8) | (b2 & 0xFF));
    }

    public static byte get1PartofSewedShort(short i){//返回缝合short的前半部分byte
        return (byte)(i >>> 8);
    }

    public static byte get2PartofSewedShort(short i){//返回缝合short的后半部分byte
        return (byte)i;
    }

    public static int sew2ShortsIntoInt(short s1, short s2){//把两个short缝合成一个int，s1在前，s2在后
        return ((s1 << 16) | (s2 & 0xFFFF));
    }

    public static short get1PartofSewedInt(int i){//返回缝合int的前半部分short
        return (short)(i >>> 16);
    }

    public static short get2PartofSewedInt(int i){//返回缝合int的后半部分short
        return (short)i;
    }

    public static void turnPageofBytesIntoInts(byte[] bytes, int[] ints){//把一页byte变成int
        for (int i = 0; i < Page.PAGE_SIZE; i++) {
            ints[i] = (int) INT.get(bytes, i * 4);
        }
    }
    
    public static void turnPageofIntsIntoBytes(int[] ints, byte[] bytes){//把一页int变成byte

        for (int i = 0; i < Page.PAGE_SIZE; i++) {
            INT.set(bytes, i * 4, ints[i]);
        }
    }
}
