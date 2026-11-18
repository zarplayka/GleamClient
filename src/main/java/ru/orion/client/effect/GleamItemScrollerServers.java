package ru.orion.client.effect;

import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Разрешённые сети; DNS обновляется в фоне, без работы с сетью в рендере/вводе. */
public final class GleamItemScrollerServers {
    private static final Set<String> DOMAINS = Set.of(
            "funtime.su", "funtime.me", "funtime.sh", "holyworld.ru", "aresmine.ru",
            "reallyworld.ru", "reallyworld.me", "reallyworld.org", "reallyworld.online", "playrw.ru",
            "virtusmine.fun", "lunamine.ru");
    private static final Set<String> HOSTS = Set.of(
            "mc.funtime.su", "play.funtime.su", "play2.funtime.su", "connect.funtime.su",
            "tcp.funtime.sh", "neo.funtime.sh", "test-neo.funtime.sh", "test-tcp.funtime.sh",
            "tcpshield.funtime.me", "neoprotect.funtime.me", "neoprotect.funtime.su",
            "tcpshield.funtime.su", "tcpshield-ovh.funtime.su",
            "mc.holyworld.ru", "mc.aresmine.ru", "mc.reallyworld.ru", "mc.reallyworld.me",
            "mc.reallyworld.org", "mc.reallyworld.online", "mc.playrw.ru",
            "mc.virtusmine.fun", "mc.lunamine.ru");
    private static volatile Set<InetSocketAddress> endpoints = Set.of();
    private static volatile Decision lastDecision;
    private record Decision(String address, SocketAddress remote, Set<InetSocketAddress> snapshot, boolean allowed) {}

    static {
        var worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Gleam-ItemScroller-DNS");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(GleamItemScrollerServers::refresh, 0, 5, TimeUnit.MINUTES);
    }

    private GleamItemScrollerServers() {}

    public static void warmUp() {
        // Инициализация класса запускает только фоновый поток.
    }

    public static boolean allowed(String address, SocketAddress remote) {
        Set<InetSocketAddress> snapshot = endpoints;
        Decision cached = lastDecision;
        if (cached != null && cached.snapshot == snapshot && Objects.equals(cached.address, address)
                && Objects.equals(cached.remote, remote)) return cached.allowed;
        boolean result = allowed(address, remote, snapshot);
        lastDecision = new Decision(address, remote, snapshot, result);
        return result;
    }

    static boolean allowed(String address, SocketAddress remote, Set<InetSocketAddress> resolved) {
        // Наличие реального удалённого подключения проверяется вызывающим кодом.
        if (!(remote instanceof InetSocketAddress socket) || socket.isUnresolved()) return false;
        return allowedHost(address) || resolved.contains(socket) && directAddress(address, socket);
    }

    private static boolean directAddress(String address, InetSocketAddress remote) {
        if (address == null) return false;
        String host = address.strip();
        int port = 25565;
        try {
            if (host.startsWith("[")) {
                int end = host.indexOf(']');
                if (end < 0) return false;
                String suffix = host.substring(end + 1);
                if (!suffix.isEmpty()) {
                    if (!suffix.startsWith(":")) return false;
                    port = Integer.parseInt(suffix.substring(1));
                }
                host = host.substring(1, end);
            } else if (host.indexOf(':') >= 0 && host.indexOf(':') == host.lastIndexOf(':')) {
                int colon = host.indexOf(':');
                port = Integer.parseInt(host.substring(colon + 1));
                host = host.substring(0, colon);
            }
            // ofLiteral не делает DNS-запросов. Чужой домен на общем proxy-IP не разрешаем.
            return port == remote.getPort() && parseLiteral(host).equals(remote.getAddress());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static InetAddress parseLiteral(String host) {
        // Разрешаем только числовой синтаксис до getByName: домен никогда не попадает в DNS.
        boolean ipv6 = host.indexOf(':') >= 0 && host.matches("[0-9a-fA-F:.]+");
        boolean ipv4 = host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        if (!ipv4 && !ipv6) throw new IllegalArgumentException("Not a numeric address");
        if (ipv4) for (String part : host.split("\\."))
            if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("Invalid IPv4");
        try { return InetAddress.getByName(host); }
        catch (java.net.UnknownHostException error) { throw new IllegalArgumentException(error); }
    }

    static boolean allowedHost(String address) {
        if (address == null) return false;
        String host = address.strip().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) return false;
        int colon = host.indexOf(':');
        if (colon >= 0) {
            if (!host.substring(colon + 1).matches("[0-9]{1,5}")) return false;
            int port = Integer.parseInt(host.substring(colon + 1));
            if (port < 1 || port > 65535) return false;
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (!host.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)*")) return false;
        for (String domain : DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    private static void refresh() {
        boolean initial = endpoints.isEmpty();
        Set<InetSocketAddress> next = new HashSet<>();
        for (String host : HOSTS) {
            resolve(next, host, 25565);
            var properties = new Hashtable<String, String>();
            properties.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            properties.put("com.sun.jndi.dns.timeout.initial", "1000");
            properties.put("com.sun.jndi.dns.timeout.retries", "1");
            InitialDirContext dns = null;
            try {
                dns = new InitialDirContext(properties);
                var records = dns.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"}).get("SRV");
                if (records != null) for (int i = 0; i < records.size(); i++) {
                    String[] fields = records.get(i).toString().strip().split("\\s+");
                    if (fields.length == 4) resolve(next, fields[3], Integer.parseInt(fields[2]));
                }
            } catch (Exception ignored) {
                // Недоступная запись не разрешает посторонний сервер.
            } finally {
                if (dns != null) try { dns.close(); } catch (Exception ignored) {}
            }
            // Публикуем постепенно: прямой IP не ждёт проверки всех остальных сетей.
            if (initial) endpoints = Set.copyOf(next);
        }
        endpoints = Set.copyOf(next);
    }

    private static void resolve(Set<InetSocketAddress> found, String host, int port) {
        if (port < 1 || port > 65535) return;
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                found.add(new InetSocketAddress(address, port));
            }
        } catch (Exception ignored) {}
    }
}
