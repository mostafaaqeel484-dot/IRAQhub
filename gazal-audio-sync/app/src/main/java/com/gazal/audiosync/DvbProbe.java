package com.gazal.audiosync;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class DvbProbe {
    private static String read(String path) {
        try (FileInputStream in = new FileInputStream(path)) {
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            return n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8) : "";
        } catch (Exception e) { return "غير قابل للقراءة: " + e.getClass().getSimpleName(); }
    }
    static String snapshot() {
        StringBuilder s = new StringBuilder();
        s.append("DVB demux6: ").append(new File("/dev/dvb0.demux6").exists() ? "FOUND" : "MISSING");
        s.append("\nDVB dvr6: ").append(new File("/dev/dvb0.dvr6").exists() ? "FOUND" : "MISSING");
        s.append("\n\n/sys/class/dmx/dump_av_level:\n").append(read("/sys/class/dmx/dump_av_level"));
        s.append("\nLocal ADB 127.0.0.1:5556: ").append(tcp("127.0.0.1", 5556) ? "OPEN" : "CLOSED");
        s.append("\nLocal ADB 127.0.0.1:5555: ").append(tcp("127.0.0.1", 5555) ? "OPEN" : "CLOSED");
        s.append("\n\nهذا فحص قراءة فقط؛ اتصال ADB لا يعني وجود تفويض للتحكم بخدمات DVB.");
        return s.toString();
    }
    private static boolean tcp(String host, int port) {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), 600);
            return true;
        } catch (Exception e) { return false; }
    }
}
