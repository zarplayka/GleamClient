package ru.orion.client.hud;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.util.HashMap;
import java.util.Map;

/** Windows GPU engine counters. Used only on the diagnostics worker. */
final class OrionGpuCounters implements AutoCloseable {
    private static final int MORE_DATA = 0x800007D2;
    private static final int DOUBLE_FORMAT = 0x00000200;
    record Load(double game, double system, double encode) {
        static final Load UNAVAILABLE = new Load(-1, -1, -1);
    }

    public interface Pdh extends StdCallLibrary {
        int PdhOpenQueryW(WString dataSource, Pointer userData, PointerByReference query);
        int PdhAddEnglishCounterW(Pointer query, WString path, Pointer userData, PointerByReference counter);
        int PdhCollectQueryData(Pointer query);
        int PdhGetFormattedCounterArrayW(Pointer counter, int format, IntByReference bytes, IntByReference count, Pointer data);
        int PdhCloseQuery(Pointer query);
    }

    // The value in PDH_FMT_COUNTERVALUE is a union. PDH_FMT_DOUBLE selects its double member.
    @Structure.FieldOrder({"status", "number"})
    public static final class Value extends Structure {
        public int status;
        public double number;
        int statusOffset() { return fieldOffset("status"); }
        int numberOffset() { return fieldOffset("number"); }
    }

    @Structure.FieldOrder({"name", "value"})
    public static final class Item extends Structure {
        public Pointer name;
        public Value value = new Value();
        int valueOffset() { return fieldOffset("value"); }
    }

    private final Pdh pdh;
    private final Pointer query, counter;
    private final String process = "pid_" + ProcessHandle.current().pid() + "_";
    private final IntByReference bytes = new IntByReference(), count = new IntByReference();
    private final Map<String, Double> engines = new HashMap<>();
    private final int itemSize, statusOffset, numberOffset;
    private Memory data;

    static OrionGpuCounters open() {
        if (!Platform.isWindows()) return null;
        try {
            Pdh pdh = Native.load("pdh", Pdh.class);
            var handle = new PointerByReference();
            if (pdh.PdhOpenQueryW(null, null, handle) != 0) return null;
            Pointer query = handle.getValue();
            boolean ready = false;
            try {
                var counter = new PointerByReference();
                if (pdh.PdhAddEnglishCounterW(query, new WString("\\GPU Engine(*)\\Utilization Percentage"), null, counter) != 0 ||
                        pdh.PdhCollectQueryData(query) != 0) return null;
                var result = new OrionGpuCounters(pdh, query, counter.getValue());
                ready = true;
                return result;
            } finally {
                if (!ready) pdh.PdhCloseQuery(query);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    private OrionGpuCounters(Pdh pdh, Pointer query, Pointer counter) {
        this.pdh = pdh;
        this.query = query;
        this.counter = counter;
        var layout = new Item();
        itemSize = layout.size();
        statusOffset = layout.valueOffset() + layout.value.statusOffset();
        numberOffset = layout.valueOffset() + layout.value.numberOffset();
    }

    Load read() {
        if (pdh.PdhCollectQueryData(query) != 0) return Load.UNAVAILABLE;
        bytes.setValue(data == null ? 0 : (int) data.size());
        int result = pdh.PdhGetFormattedCounterArrayW(counter, DOUBLE_FORMAT, bytes, count, data);
        if (result == MORE_DATA && bytes.getValue() > 0 && bytes.getValue() <= 2 * 1024 * 1024) {
            if (data != null) data.close();
            data = new Memory(bytes.getValue());
            result = pdh.PdhGetFormattedCounterArrayW(counter, DOUBLE_FORMAT, bytes, count, data);
        }
        if (result != 0 || data == null || count.getValue() < 1 || (long) count.getValue() * itemSize > data.size())
            return Load.UNAVAILABLE;

        engines.clear();
        double game = 0, system = 0, encode = -1;
        for (int i = 0; i < count.getValue(); i++) {
            long offset = (long) i * itemSize;
            int status = data.getInt(offset + statusOffset);
            if (status != 0 && status != 1) continue;
            double load = data.getDouble(offset + numberOffset);
            Pointer pointer = data.getPointer(offset);
            if (pointer == null || !Double.isFinite(load) || load < 0) continue;
            String name = pointer.getWideString(0);
            int adapter = name.indexOf("_luid_");
            if (adapter < 0) continue;
            String engine = name.substring(adapter);
            engines.merge(engine, load, Double::sum);
            if (name.startsWith(process)) game = Math.max(game, load);
        }
        for (var entry : engines.entrySet()) {
            system = Math.max(system, entry.getValue());
            if (entry.getKey().endsWith("_engtype_VideoEncode")) encode = Math.max(encode, entry.getValue());
        }
        return engines.isEmpty() ? Load.UNAVAILABLE : new Load(Math.min(game, 100), Math.min(system, 100), Math.min(encode, 100));
    }

    @Override public void close() {
        pdh.PdhCloseQuery(query);
        if (data != null) data.close();
    }
}
