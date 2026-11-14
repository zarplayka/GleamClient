package ru.orion.client.effect;

/** Keyframes in model pixels and degrees, sampled with a continuous cyclic curve. */
final class OrionEmoteClips {
    static final int SHIFT = 0, RISE = 1, BODY = 2, HEAD = 5, RIGHT_ARM = 8, LEFT_ARM = 11,
            RIGHT_LEG = 14, LEFT_LEG = 16;
    private static final int CHANNELS = 18;

    private record Clip(float duration, float[][] frames) {
        private float time(int frame) {
            return frames[Math.floorMod(frame, frames.length)][0] + Math.floorDiv(frame, frames.length) * duration;
        }

        private float[] sample(double seconds) {
            float time = (float) (seconds - Math.floor(seconds / duration) * duration);
            int left = frames.length - 1;
            while (left > 0 && frames[left][0] > time) left--;
            float[] a = frames[left], b = frames[(left + 1) % frames.length];
            float[] before = frames[Math.floorMod(left - 1, frames.length)], after = frames[(left + 2) % frames.length];
            float interval = time(left + 1) - time(left);
            float previous = time(left) - time(left - 1), next = time(left + 2) - time(left + 1);
            float t = (time - a[0]) / interval, t2 = t * t, t3 = t2 * t;
            float[] pose = new float[CHANNELS];
            for (int k = 1; k <= CHANNELS; k++) {
                float slope = (b[k] - a[k]) / interval;
                float m0 = tangent((a[k] - before[k]) / previous, slope);
                float m1 = tangent(slope, (after[k] - b[k]) / next);
                pose[k - 1] = (2 * t3 - 3 * t2 + 1) * a[k] + (t3 - 2 * t2 + t) * interval * m0
                        + (3 * t2 - 2 * t3) * b[k] + (t3 - t2) * interval * m1;
            }
            return pose;
        }
    }

    private static float tangent(float a, float b) {
        return a * b <= 0F ? 0F : 2F * a * b / (a + b);
    }

    // Time | hip sway, rise | torso XYZ | head XYZ | right arm XYZ | left arm XYZ | right leg pitch, lift | left leg pitch, lift
    private static final Clip[] CLIPS = {
        // Shuffle: toe pickup, supported slide, cross-step, and transfer to the other foot.
        new Clip(1.36F, new float[][] {
            {0,     -.9F,.30F,   11, -8, -5,    -7,  4,  3,    -28,-10, 12,    15, 12,-10,    -33,1.8F,   13,0},
            {.16F,  -.8F,.60F,    7,-10, -6,    -5,  6,  3,     -5, -5, 18,     8, 10,-15,    -18,1.0F,    6,0},
            {.34F,  -.5F,0,      14,  1, -3,    -9, -3,  2,     18, -7, 12,   -24, 15, -9,      6,0,    -21,.25F},
            {.50F,   .25F,-.15F,  9, 10,  2,    -7, -4, -2,     12, -9, 10,   -32, 12,-14,     11,0,    -12,.6F},
            {.68F,   .9F,.30F,   11,  8,  5,    -7, -4, -3,     15,-12, 10,   -28, 10,-12,     13,0,    -33,1.8F},
            {.84F,   .8F,.60F,    7, 10,  6,    -5, -6, -3,      8,-10, 15,    -5,  5,-18,      6,0,    -18,1.0F},
            {1.02F,  .5F,0,      14, -1,  3,    -9,  3, -2,    -24,-15,  9,    18,  7,-12,    -21,.25F,  6,0},
            {1.18F, -.25F,-.15F,  9,-10, -2,    -7,  4,  2,    -32,-12, 14,    12,  9,-10,    -12,.6F,  11,0},
        }),
        // Robot: short locked transitions and intentional holds, with the head moving before the body.
        new Clip(2.00F, new float[][] {
            {0,     -.35F,0,  0,-18,-3,    4,-8,  4,   -85,  8, 18,     -8, 10,-61,   -6,0,   4,0},
            {.24F,  -.35F,0,  0,-18,-3,    4,-8,  4,   -85,  8, 18,     -8, 10,-61,   -6,0,   4,0},
            {.34F,  -.35F,0,  0,-18,-3,   -7,18,  2,   -85,  8, 18,     -8, 10,-61,   -6,0,   4,0},
            {.48F,   .35F,0,  4, 18, 3,   -7, 4, -2,   -10,-10, 61,    -87, -8,-18,    4,0,  -6,0},
            {.79F,   .35F,0,  4, 18, 3,   -7, 4, -2,   -10,-10, 61,    -87, -8,-18,    4,0,  -6,0},
            {.90F,   .10F,-.3F,8, 0, 0,   11, 0,  0,   -64,-17, 24,    -63, 17,-24,    6,0,   7,0},
            {1.18F,  .10F,-.3F,8, 0, 0,   11, 0,  0,   -64,-17, 24,    -63, 17,-24,    6,0,   7,0},
            {1.30F,  .10F,0, -3, 0, 0,   -9, 0,  0,  -105,-17, 26,   -107, 17,-26,   -4,0,  -5,0},
            {1.57F,  .10F,0, -3, 0, 0,   -9, 0,  0,  -105,-17, 26,   -107, 17,-26,   -4,0,  -5,0},
            {1.75F, -.35F,0,  0,-18,-3,    4,-8,  4,   -85,  8, 18,     -8, 10,-61,   -6,0,   4,0},
        }),
        // Disco: alternate diagonal points, with a low passing pose and a weight shift.
        new Clip(1.92F, new float[][] {
            {0,     -.85F,.10F,   -6,-20,-12,    -9, 13, 6,   -153,-19, 35,      5, 12,-17,   -10,0,    6,0},
            {.22F,  -.72F,0,     -4,-14,-10,    -7, 10, 5,   -152,-11, 39,     -8, 15,-21,   -10,0,    5,.15F},
            {.48F,  -.10F,-.6F,   8, -1, -3,    11,  0, 1,    -47,-15,  8,    -28, 19,-17,     9,0,   13,.4F},
            {.72F,   .45F,-.2F,   3, 14,  6,    -3,  8,-3,     -8,-10, 21,    -93, 10,-25,     5,.3F, -8,0},
            {.96F,   .85F,.10F,  -6, 20, 12,    -9,-13,-6,      5,-12, 17,   -153, 19,-35,     6,0,  -10,0},
            {1.18F,  .72F,0,     -4, 14, 10,    -7,-10,-5,     -8,-15, 21,   -152, 11,-39,     5,.15F,-10,0},
            {1.44F,  .10F,-.6F,   8,  1,  3,    11,  0,-1,    -28,-19, 17,    -47, 15, -8,    13,.4F,  9,0},
            {1.68F, -.45F,-.2F,   3,-14, -6,    -3, -8, 3,    -93,-10, 25,     -8, 10,-21,    -8,0,    5,.3F},
        }),
        // Floss: two front-facing sweeps connected through opposite sides of the hips.
        new Clip(1.48F, new float[][] {
            {0,      .9F,0,      4, 18, 6,  -3,-12,-4,   -32, 15,-48,   -38, -8,-49,   -6,0,  6,0},
            {.15F,   .75F,-.2F,  5, 12, 5,  -3,-10,-3,   -24, 19,-39,     2,-13,-55,   -6,0,  7,0},
            {.36F,  -.36F,-.5F,  9,-15,-3,   3, 12, 2,   -19, 17, 34,    30,-14, 28,    6,0, -6,0},
            {.55F,  -.9F,0,      3,-20,-6,  -2, 13, 4,   -36, 10, 50,   -28,-14, 49,    7,0, -7,0},
            {.74F,  -.86F,0,     4,-17,-5,  -3, 12, 4,   -33,  8, 48,   -31,-15, 47,    6,0, -6,0},
            {.89F,  -.70F,-.2F,  5,-12,-5,  -3, 10, 3,     2, 13, 55,   -24,-19, 39,    7,0, -6,0},
            {1.10F,  .36F,-.5F,  9, 15, 3,   3,-12,-2,    30, 14,-28,   -19,-17,-34,   -6,0,  6,0},
            {1.29F,  .9F,0,      3, 20, 6,  -2,-13,-4,   -28, 14,-49,   -36,-10,-50,   -7,0,  7,0},
        }),
        // Wave: one arm rises, shoulder rolls across, then the other arm carries the motion.
        new Clip(2.40F, new float[][] {
            {0,     -.55F,0,     0, -5,-5,    2,  8, 4,   -7, -8, 92,    -6,  5,-22,   -5,0,  4,0},
            {.28F,  -.66F,.10F, -1,-12,-8,   -7,  9, 5,  -15, -8,127,   -13,  2,-42,   -5,0,  4,0},
            {.58F,  -.27F,.34F, -7, -6,-4,   -4,  4, 2,  -18, -3,106,   -19,  3,-70,   -2,0,  3,0},
            {.88F,   .27F,.34F, -7,  6, 4,   -4, -4,-2,  -19, -3, 70,   -18,  3,-106,   3,0, -2,0},
            {1.18F,  .66F,.10F, -1, 12, 8,   -7, -9,-5,  -13, -2, 42,   -15,  8,-127,   4,0, -5,0},
            {1.50F,  .55F,0,     0,  5, 5,    2, -8,-4,   -6, -5, 22,    -7,  8,-92,    4,0, -5,0},
            {1.80F,  .24F,-.20F, 6,  0, 3,    6, -5,-2,  -12,-13, 16,   -11, 13,-50,    3,0, -3,0},
            {2.08F, -.13F,-.12F, 3, -3,-2,    4,  3, 2,  -10, -9, 49,   -10, 10,-20,   -2,0,  3,0},
        }),
        // Celebration: anticipation, two jumps at different heights, then a grounded landing.
        new Clip(2.00F, new float[][] {
            {0,      0,   0,      2,  0, 0,    2,  0, 0,    -18,-12, 16,    -22,  8,-18,    4,0,    -3,0},
            {.24F,  -.15F,-1.3F, 16, -7,-2,   11,  2, 1,     28, -8, 12,     23,  9,-10,   17,0,    21,0},
            {.46F,  -.35F,2.1F,  -8,-10,-4,  -10,  5, 4,   -143, -8, 42,   -157,  7,-35,  -18,2.4F, 18,2.1F},
            {.66F,  -.20F,-.8F,  12, -4,-1,    6,  2, 1,    -72,-18, 37,    -83, 12,-28,   12,0,    15,0},
            {.82F,   .12F,-.55F, 10,  5, 2,    1, -2,-2,     -8,-13, 23,     -2, 12,-20,   12,0,     8,0},
            {1.03F,  .29F,2.7F,  -9, 13, 5,  -12, -5,-4,   -169, -9, 28,   -142, 10,-46,   21,2.4F,-22,2.7F},
            {1.26F,  .18F,-.9F,  13,  7, 2,    7, -5,-1,    -73,-12, 31,    -64, 12,-33,   18,0,    12,0},
            {1.53F,  .08F,.10F,  -2,  2, 1,   -6,  0, 0,    -51, -7, 22,    -49,  9,-24,   -3,0,     4,0},
        }),
    };

    static float[] sample(int id, double age) {
        return CLIPS[id - 1].sample(age);
    }

    private OrionEmoteClips() { }
}
