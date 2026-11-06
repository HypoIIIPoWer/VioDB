package Vio;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class Test {
    public static void testbufferpool() throws Exception {
        // ---- 0. 清理旧文件（避免上次测试残留的 meta page 干扰）----
        URL loc = Main.class.getProtectionDomain().getCodeSource().getLocation();
        Path root = Path.of(loc.toURI());
        Files.deleteIfExists(root.resolve("test_table.let"));
        Files.deleteIfExists(root.resolve("test_table.gar"));

        pagemanager pm = new pagemanager();
        pagemanager.bufferpool bp = pm.new bufferpool("test_table");

        // ---- 1. 分配一个 let 页，写数据，unpin ----
        int pid1 = bp.allocatePage(true);
        Page p1 = bp.getPage(pid1);          // pin = 1
        p1.buf[0] = (byte) 0xAB;
        p1.buf[1] = (byte) 0xCD;
        bp.unpin(pid1, true);                // pin = 0, dirty = true
        System.out.println("[OK] allocated let page " + pid1 + " and wrote");

        // ---- 2. 分配 10 个 gar 页，超过 8 帧，触发 CLOCK 淘汰 ----
        int[] garPids = new int[10];
        for (int i = 0; i < 10; i++) {
            int pid = bp.allocatePage(false);
            garPids[i] = pid;
            Page p = bp.getPage(pid);        // pin = 1
            p.buf[0] = (byte) i;
            bp.unpin(pid, true);             // pin = 0, dirty = true
            System.out.println("[OK] allocated gar page " + pid + " value=" + i);
        }

        // ---- 3. 重新读 pid1：可能还在池中，也可能已被淘汰并从磁盘读回 ----
        Page p1b = bp.getPage(pid1);
        if (p1b.buf[0] != (byte) 0xAB || p1b.buf[1] != (byte) 0xCD)
            throw new AssertionError("page " + pid1 + " corrupted");
        bp.unpin(pid1, false);
        System.out.println("[OK] read back page " + pid1 + " correctly");

        // ---- 4. 逐个重读 10 个 gar 页，验证持久化 ----
        for (int i = 0; i < 10; i++) {
            Page p = bp.getPage(garPids[i]);
            if (p.buf[0] != (byte) i)
                throw new AssertionError("gar page " + garPids[i] + " corrupted");
            bp.unpin(garPids[i], false);
        }
        System.out.println("[OK] all 10 gar pages verified");

        // ---- 5. 关闭，meta page 落盘 ----
        bp.closeBufferPool();
        System.out.println("[OK] buffer pool closed, meta page persisted");
    }
}
