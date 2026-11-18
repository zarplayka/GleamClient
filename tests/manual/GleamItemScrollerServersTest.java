package ru.orion.client.effect;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

public final class GleamItemScrollerServersTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (String host : new String[]{"mc.funtime.su", "MC.HOLYWORLD.RU:25565", "mc.aresmine.ru.",
                "mc.reallyworld.ru", "mc.virtusmine.fun", "mc.lunamine.ru", "play2.funtime.su",
                "tcp.funtime.sh", "neoprotect.funtime.me", "mc.reallyworld.online", "mc.playrw.ru"}) {
            check(GleamItemScrollerServers.allowedHost(host), "allowed " + host);
        }
        for (String host : new String[]{"example.org", "evilfuntime.su", "mc.funtime.su.evil.org",
                "funtime.su@evil.org", "mc.funtime.su:99999", "mc.funtime.su:0", "mc.funtime.su:x",
                "mc.funtime.su:25565:1", "http://mc.funtime.su", "[::1]:25565", "", "localhost"}) {
            check(!GleamItemScrollerServers.allowedHost(host), "denied " + host);
        }
        check(!GleamItemScrollerServers.allowed("mc.funtime.su", null), "disconnected");
        check(!GleamItemScrollerServers.allowed("mc.funtime.su",
                InetSocketAddress.createUnresolved("mc.funtime.su", 25565)), "unresolved");
        InetSocketAddress ip = new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 17}), 25565);
        check(GleamItemScrollerServers.allowed("mc.funtime.su", ip), "connected domain");
        // Проверяем снимок DNS без подключений к игровым серверам.
        check(GleamItemScrollerServers.allowed("192.0.2.17", ip, Set.of(ip)), "direct IPv4");
        check(!GleamItemScrollerServers.allowed("unrelated.example", ip, Set.of(ip)), "other virtual host on shared proxy IP");
        check(!GleamItemScrollerServers.allowed("192.0.2.18", ip, Set.of(ip)), "literal must match connected address");
        check(!GleamItemScrollerServers.allowed("192.0.2.17:25566", ip, Set.of(ip)), "literal port must match connected port");
        check(!GleamItemScrollerServers.allowed("192.0.2.17:25566",
                new InetSocketAddress(ip.getAddress(), 25566), Set.of(ip)), "different server port");
        check(!GleamItemScrollerServers.allowed("192.0.2.18",
                new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 18}), 25565), Set.of(ip)), "unlisted IP");
        InetSocketAddress ipv6 = new InetSocketAddress(InetAddress.getByName("2001:db8::17"), 25570);
        check(GleamItemScrollerServers.allowed("[2001:db8::17]:25570", ipv6, Set.of(ipv6)), "IPv6 SRV endpoint");
        check(!GleamItemScrollerServers.allowed("192.0.2.17", ip, Set.of()), "failed DNS denies IP");
        System.out.println("Passed " + checks + " server policy checks");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
