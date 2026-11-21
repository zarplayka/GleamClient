package gleam.diagnostics;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

public final class MinecraftProfiler {
    public static void premain(String destination) {
        if (destination == null || destination.isBlank()) return;
        Thread worker = new Thread(() -> {
            try {
                new Sampler(Path.of(destination)).run();
            } catch (RuntimeException | LinkageError ignored) {
                // A runtime without JFR can still launch the game and report process totals.
            }
        }, "Gleam Minecraft profiler");
        worker.setDaemon(true);
        worker.start();
    }

    private record Allocation(String type, String method) {}
    private record HotThread(long id, String name, long cpuMillis) {}

    private static final class Sampler {
        private final Path destination;
        private final Path staging;
        private final java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        private final Map<Long, Map<String, Integer>> methods = new HashMap<>();
        private final Map<Allocation, Long> allocations = new HashMap<>();
        private final Map<Long, String> names = new HashMap<>();
        private Map<Long, Long> previous;
        private long last = System.nanoTime();

        Sampler(Path destination) {
            this.destination = destination;
            staging = destination.resolveSibling("pending.tsv");
            previous = threadTimes();
        }

        void run() {
            try (var stream = new RecordingStream()) {
                stream.setMaxAge(Duration.ofSeconds(20));
                stream.setMaxSize(8 * 1024 * 1024);
                stream.setReuse(true);
                stream.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20)).withStackTrace();
                stream.enable("jdk.ObjectAllocationSample").with("throttle", "50/s").withStackTrace();
                stream.onEvent("jdk.ExecutionSample", this::sampleMethod);
                stream.onEvent("jdk.ObjectAllocationSample", this::sampleAllocation);
                stream.onFlush(this::publish);
                stream.start();
            }
        }

        private void sampleMethod(RecordedEvent event) {
            RecordedThread thread = event.getThread("sampledThread");
            if (thread == null || isProfiler(thread)) return;
            String site = site(event);
            if (site.isEmpty()) return;
            long id = thread.getJavaThreadId();
            names.putIfAbsent(id, thread.getJavaName());
            methods.computeIfAbsent(id, ignored -> new HashMap<>()).merge(site, 1, Integer::sum);
        }

        private void sampleAllocation(RecordedEvent event) {
            RecordedThread thread = event.getThread();
            if (thread != null && isProfiler(thread)) return;
            var type = event.getClass("objectClass");
            String site = site(event);
            if (type != null && !site.isEmpty()) {
                allocations.merge(new Allocation(type.getName(), site), event.getLong("weight"), Long::sum);
            }
        }

        private static boolean isProfiler(RecordedThread thread) {
            return Thread.currentThread().threadId() == thread.getJavaThreadId();
        }

        private static String site(RecordedEvent event) {
            var trace = event.getStackTrace();
            if (trace == null) return "";
            String top = "";
            for (RecordedFrame frame : trace.getFrames()) {
                var method = frame.getMethod();
                if (!frame.isJavaFrame() || method == null) continue;
                String owner = method.getType().getName();
                if (owner.startsWith("gleam.diagnostics.")) return "";
                String name = owner + "." + method.getName();
                if (top.isEmpty()) top = name;
                if (!owner.startsWith("java.") && !owner.startsWith("jdk.") && !owner.startsWith("sun.")) {
                    // Retain the executing method and its application caller when the top frame is in the JDK.
                    return name.equals(top) ? top : top + " ← " + name;
                }
            }
            return top;
        }

        private Map<Long, Long> threadTimes() {
            Map<Long, Long> times = new HashMap<>();
            if (!threads.isThreadCpuTimeSupported() || !threads.isThreadCpuTimeEnabled()) return times;
            for (long id : threads.getAllThreadIds()) {
                if (id == Thread.currentThread().threadId()) continue;
                long cpu = threads.getThreadCpuTime(id);
                if (cpu >= 0) times.put(id, cpu);
            }
            return times;
        }

        private HotThread hottest(Map<Long, Long> current) {
            long bestId = 0;
            long bestCpu = 0;
            for (var entry : current.entrySet()) {
                Long before = previous.get(entry.getKey());
                if (before == null) continue;
                long cpu = entry.getValue() - before;
                if (cpu > bestCpu) {
                    bestId = entry.getKey();
                    bestCpu = cpu;
                }
            }
            if (bestId == 0) return new HotThread(0, "", 0);
            var info = threads.getThreadInfo(bestId);
            String name = info != null ? info.getThreadName() : names.getOrDefault(bestId, "");
            return new HotThread(bestId, name, bestCpu / 1_000_000);
        }

        private void publish() {
            long now = System.nanoTime();
            long window = (now - last) / 1_000_000;
            if (window < 5_000) return;
            var current = threadTimes();
            HotThread hot = hottest(current);
            var hotMethod = methods.getOrDefault(hot.id(), Map.of()).entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);
            var allocation = allocations.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
            var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            String snapshot = String.join("\t", "gleam-profile-1",
                Long.toString(ProcessHandle.current().pid()), Long.toString(System.currentTimeMillis()),
                Long.toString(window), Long.toString(heap.getUsed()), Long.toString(heap.getMax()),
                Long.toString(hot.id()), clean(hot.name()), Long.toString(hot.cpuMillis()),
                hotMethod == null ? "" : clean(hotMethod.getKey()),
                hotMethod == null ? "0" : hotMethod.getValue().toString(),
                allocation == null ? "" : clean(allocation.getKey().type()),
                allocation == null ? "" : clean(allocation.getKey().method()),
                allocation == null ? "0" : allocation.getValue().toString());
            try {
                Files.writeString(staging, snapshot);
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // A subsequent window retries if the snapshot is temporarily unavailable.
            }
            last = now;
            previous = current;
            methods.clear();
            allocations.clear();
            names.clear();
        }

        private static String clean(String text) {
            if (text == null) return "";
            return text.codePoints().filter(code -> !Character.isISOControl(code)).limit(240)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        }
    }
}
