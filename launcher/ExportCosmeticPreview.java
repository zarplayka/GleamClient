import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import ru.orion.client.effect.OrionWingMesh;

/** Exports the actual in-game wing geometry and the Disco clip used in ClickGUI. */
public class ExportCosmeticPreview {
    public static void main(String[] args) throws Exception {
        try (var output = new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))) {
            output.print("{\"angel\":");
            wings(output, 1);
            output.print(",\"dragon\":");
            wings(output, 2);
            output.print(",\"dance\":[");
            var clips = Class.forName("ru.orion.client.effect.OrionEmoteClips");
            var sample = clips.getDeclaredMethod("sample", int.class, double.class);
            sample.setAccessible(true);
            // The in-game preview loops Disco (1.92 seconds).
            for (int frame = 0; frame < 96; frame++) {
                if (frame > 0) output.print(',');
                output.print('[');
                float[] pose = (float[]) sample.invoke(null, 3, frame * 1.92D / 96);
                for (int i = 0; i < pose.length; i++) {
                    if (i > 0) output.print(',');
                    output.printf(Locale.ROOT, "%.4f", pose[i]);
                }
                output.print(']');
            }
            output.println("]}");
            if (output.checkError()) throw new IllegalStateException("Failed to write cosmetic preview geometry");
        }
    }

    private static void wings(PrintWriter output, int style) {
        output.print('[');
        boolean first = true;
        for (var face : OrionWingMesh.forStyle(style, true)) {
            if (!first) output.print(',');
            first = false;
            output.print('[');
            output.print(face.color() & 0xFFFFFF);
            for (var point : new OrionWingMesh.Point[]{face.a(), face.b(), face.c(), face.d()}) {
                output.printf(Locale.ROOT, ",%.5f,%.5f,%.5f", point.x(), point.y(), point.z());
            }
            output.print(']');
        }
        output.print(']');
    }
}
