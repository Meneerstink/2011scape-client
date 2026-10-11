import com.jagex.graphics.Toolkit;

/**
 * Deadman Mode map boundary overlay. Vertices are copied from the server GuardedZones source
 * so gameplay and the worldmap use the same OSRS Deadman tile boundaries.
 */
final class DeadmanWorldMapOverlay {
    /** See-through green over the safe area (owner 2026-09-19: "the green is perfect"). */
    private static final int SAFE_FILL = 0x4020E060;
    private static final int SAFE_LINE = 0xFF20E060;
    /** A lighter see-through red over every dangerous tile, so the map stays readable underneath. */
    private static final int DANGEROUS_FILL = 0x30E02828;
    private static final int SAFE_SWATCH = 0xB020E060;
    private static final int DANGEROUS_SWATCH = 0xB0E02828;
    private static final int DANGEROUS_EDGE = 0xFFE04040;
    private static final int LEGEND_BACKGROUND = 0xD0181410;
    private static final int LEGEND_BORDER = 0xFF8C7340;
    private static final int LEGEND_TITLE = 0xFFB040;

    private static final int[][] ZONES = {
        // Varrock - synced 2026-10-09 with server GuardedZones.kt (game/plugins/.../mechanics/pvp/GuardedZones.kt):
        // GE north edge straight on z 3518 (2026-09-20), GE south wall/gatehouse edge (2026-09-23), GE north-west
        // corner on the wall body 3143,3518 -> 3138,3513 (2026-10-08).
        new int[] { 3182, 3382, 3242, 3381, 3242, 3380, 3245, 3380, 3245, 3382, 3253, 3382, 3253, 3380, 3265, 3380, 3265, 3376, 3287, 3376, 3290, 3379, 3290, 3384, 3289, 3385, 3289, 3390, 3288, 3391, 3288, 3407, 3287, 3408, 3277, 3408, 3274, 3411, 3274, 3420, 3274, 3437, 3271, 3437, 3271, 3464, 3263, 3472, 3263, 3492, 3262, 3493, 3255, 3493, 3252, 3496, 3252, 3502, 3235, 3502, 3229, 3508, 3200, 3508, 3190, 3518, 3143, 3518, 3138, 3513, 3138, 3494, 3141, 3491, 3141, 3486, 3138, 3483, 3138, 3472, 3142, 3467, 3149, 3467, 3151, 3465, 3159, 3465, 3159, 3462, 3171, 3462, 3171, 3465, 3179, 3465, 3181, 3467, 3187, 3467, 3187, 3464, 3185, 3462, 3185, 3458, 3186, 3457, 3190, 3457, 3198, 3448, 3180, 3448, 3174, 3448, 3174, 3399, 3182, 3399 },
        // Falador
        new int[] { 2936, 3320, 2936, 3354, 2937, 3354, 2937, 3357, 2936, 3357, 2936, 3388, 2941, 3393, 2943, 3393, 2945, 3395, 2949, 3395, 2950, 3394, 2956, 3394, 2957, 3395, 2985, 3395, 2987, 3393, 2996, 3393, 2997, 3394, 3002, 3394, 3003, 3395, 3008, 3395, 3011, 3392, 3020, 3392, 3022, 3390, 3040, 3390, 3041, 3389, 3047, 3389, 3048, 3390, 3061, 3390, 3066, 3385, 3066, 3369, 3060, 3363, 3060, 3331, 3061, 3330, 3061, 3329, 3060, 3328, 3059, 3328, 3058, 3329, 3025, 3329, 3023, 3327, 3016, 3327, 3012, 3323, 3008, 3323, 3008, 3324, 3005, 3324, 3005, 3323, 3002, 3323, 2995, 3316, 2992, 3316, 2985, 3309, 2967, 3309, 2966, 3310, 2957, 3310, 2956, 3311, 2942, 3311, 2940, 3313, 2940, 3320 },
        // Lumbridge
        new int[] { 3233, 3264, 3213, 3264, 3213, 3257, 3201, 3257, 3201, 3219, 3200, 3219, 3200, 3218, 3201, 3218, 3201, 3204, 3204, 3201, 3213, 3201, 3215, 3203, 3221, 3203, 3226, 3208, 3228, 3206, 3227, 3205, 3227, 3202, 3228, 3201, 3230, 3201, 3230, 3195, 3238, 3195, 3238, 3191, 3247, 3191, 3247, 3190, 3255, 3190, 3257, 3194, 3258, 3200, 3259, 3204, 3258, 3208, 3256, 3214, 3252, 3216, 3251, 3219, 3245, 3219, 3243, 3221, 3243, 3227, 3239, 3230, 3238, 3233, 3238, 3235, 3237, 3236, 3237, 3239, 3236, 3241, 3236, 3243, 3236, 3247, 3235, 3249, 3235, 3257, 3233, 3257 },
        // Catherby bank
        new int[] { 2806, 3438, 2806, 3446, 2813, 3446, 2813, 3438 },
        // Seers' Village bank
        new int[] { 2721, 3498, 2721, 3497, 2719, 3497, 2719, 3494, 2721, 3494, 2721, 3490, 2724, 3490, 2724, 3487, 2728, 3487, 2728, 3490, 2731, 3490, 2731, 3498 },
        // East Ardougne
        new int[] { 2559, 3265, 2559, 3328, 2560, 3328, 2560, 3338, 2564, 3342, 2615, 3342, 2617, 3340, 2668, 3340, 2674, 3334, 2682, 3334, 2688, 3328, 2688, 3314, 2687, 3313, 2687, 3312, 2688, 3311, 2688, 3306, 2688, 3299, 2686, 3297, 2686, 3290, 2688, 3288, 2688, 3283, 2688, 3274, 2685, 3274, 2685, 3266, 2683, 3266, 2683, 3274, 2676, 3274, 2676, 3271, 2675, 3270, 2675, 3269, 2672, 3266, 2671, 3266, 2670, 3265, 2667, 3265, 2662, 3267, 2659, 3268, 2658, 3269, 2655, 3269, 2653, 3266, 2650, 3265, 2648, 3264, 2638, 3264, 2637, 3263, 2626, 3263, 2625, 3264, 2612, 3264, 2611, 3263, 2611, 3258, 2610, 3257, 2603, 3257, 2602, 3258, 2602, 3264, 2586, 3264, 2586, 3263, 2583, 3262, 2581, 3259, 2573, 3259, 2571, 3262, 2568, 3262, 2568, 3265 },
        // Rellekka
        new int[] { 2603, 3655, 2607, 3654, 2611, 3653, 2616, 3653, 2628, 3646, 2635, 3646, 2638, 3645, 2645, 3645, 2648, 3644, 2652, 3645, 2657, 3644, 2659, 3645, 2667, 3645, 2670, 3645, 2672, 3644, 2676, 3645, 2685, 3645, 2692, 3649, 2693, 3653, 2689, 3661, 2689, 3664, 2691, 3667, 2691, 3670, 2690, 3672, 2690, 3677, 2693, 3684, 2693, 3690, 2690, 3698, 2693, 3703, 2693, 3706, 2691, 3710, 2691, 3712, 2656, 3713, 2655, 3712, 2655, 3711, 2652, 3711, 2652, 3709, 2655, 3709, 2655, 3708, 2654, 3707, 2654, 3706, 2655, 3705, 2655, 3704, 2654, 3703, 2654, 3702, 2652, 3700, 2652, 3694, 2650, 3693, 2649, 3692, 2647, 3692, 2646, 3691, 2646, 3690, 2644, 3688, 2642, 3688, 2642, 3692, 2640, 3692, 2640, 3688, 2639, 3688, 2637, 3686, 2637, 3684, 2636, 3684, 2635, 3683, 2634, 3683, 2633, 3684, 2633, 3686, 2632, 3686, 2632, 3685, 2629, 3685, 2629, 3684, 2628, 3683, 2628, 3682, 2627, 3681, 2626, 3681, 2625, 3680, 2622, 3680, 2622, 3682, 2620, 3682, 2620, 3679, 2619, 3678, 2617, 3678, 2615, 3676, 2613, 3676, 2611, 3674, 2611, 3673, 2609, 3672, 2609, 3671, 2606, 3671, 2606, 3667, 2600, 3661, 2600, 3659, 2603, 3656 },
        // Tree Gnome Stronghold
        new int[] { 2459, 3384, 2464, 3384, 2466, 3386, 2466, 3389, 2468, 3391, 2471, 3391, 2472, 3390, 2476, 3390, 2477, 3389, 2481, 3389, 2482, 3390, 2485, 3390, 2486, 3389, 2488, 3389, 2488, 3390, 2489, 3391, 2493, 3391, 2494, 3390, 2501, 3390, 2502, 3391, 2505, 3391, 2506, 3392, 2506, 3395, 2505, 3396, 2502, 3396, 2494, 3404, 2494, 3408, 2495, 3409, 2495, 3412, 2496, 3413, 2496, 3416, 2498, 3418, 2498, 3429, 2494, 3433, 2494, 3438, 2496, 3440, 2496, 3456, 2492, 3456, 2491, 3457, 2491, 3459, 2490, 3461, 2490, 3469, 2488, 3471, 2488, 3474, 2487, 3474, 2487, 3480, 2488, 3482, 2490, 3484, 2493, 3484, 2493, 3494, 2491, 3496, 2491, 3497, 2493, 3499, 2493, 3505, 2488, 3509, 2488, 3517, 2479, 3517, 2475, 3513, 2474, 3513, 2472, 3511, 2470, 3511, 2469, 3510, 2465, 3510, 2464, 3511, 2461, 3511, 2459, 3513, 2457, 3513, 2453, 3517, 2448, 3517, 2448, 3520, 2432, 3520, 2429, 3523, 2428, 3523, 2424, 3521, 2415, 3521, 2413, 3522, 2407, 3522, 2402, 3525, 2400, 3525, 2399, 3524, 2394, 3524, 2393, 3523, 2385, 3523, 2384, 3522, 2383, 3522, 2382, 3521, 2381, 3521, 2380, 3520, 2380, 3509, 2377, 3506, 2377, 3500, 2375, 3498, 2375, 3487, 2377, 3485, 2377, 3479, 2380, 3476, 2381, 3473, 2381, 3464, 2375, 3458, 2375, 3447, 2371, 3443, 2369, 3432, 2369, 3423, 2375, 3417, 2375, 3414, 2376, 3412, 2378, 3410, 2381, 3408, 2384, 3407, 2390, 3407, 2392, 3407, 2396, 3410, 2398, 3410, 2400, 3405, 2401, 3404, 2405, 3404, 2407, 3407, 2407, 3410, 2403, 3412, 2410, 3412, 2414, 3401, 2416, 3399, 2416, 3398, 2421, 3393, 2427, 3393, 2429, 3391, 2435, 3388, 2439, 3388, 2440, 3389, 2441, 3389, 2443, 3391, 2446, 3391, 2447, 3390, 2449, 3390, 2450, 3391, 2455, 3391, 2457, 3389, 2457, 3386 },
        // Yanille
        new int[] { 2543, 3109, 2541, 3107, 2539, 3107, 2539, 3077, 2541, 3077, 2543, 3075, 2582, 3075, 2584, 3073, 2589, 3073, 2591, 3075, 2619, 3075, 2620, 3076, 2620, 3097, 2608, 3109 },
        // Jatizso
        new int[] { 2407, 3797, 2421, 3797, 2423, 3799, 2423, 3821, 2424, 3822, 2424, 3825, 2423, 3826, 2420, 3826, 2418, 3824, 2413, 3824, 2413, 3825, 2412, 3826, 2409, 3826, 2408, 3825, 2407, 3824, 2406, 3823, 2406, 3819, 2403, 3819, 2402, 3818, 2402, 3809, 2389, 3809, 2388, 3808, 2388, 3807, 2387, 3807, 2386, 3806, 2386, 3803, 2387, 3802, 2388, 3802, 2388, 3796, 2387, 3796, 2386, 3795, 2386, 3792, 2386, 3791, 2390, 3791, 2391, 3792, 2398, 3792, 2399, 3793, 2406, 3793, 2407, 3794 },
        // Neitiznot
        new int[] { 2329, 3799, 2329, 3812, 2333, 3812, 2335, 3814, 2335, 3815, 2338, 3815, 2339, 3816, 2339, 3817, 2347, 3817, 2347, 3813, 2349, 3811, 2350, 3811, 2351, 3810, 2355, 3809, 2357, 3806, 2358, 3806, 2360, 3808, 2363, 3808, 2366, 3804, 2367, 3802, 2367, 3798, 2366, 3797, 2366, 3796, 2364, 3794, 2364, 3792, 2363, 3791, 2361, 3791, 2357, 3787, 2354, 3786, 2353, 3786, 2351, 3788, 2351, 3796, 2344, 3796, 2343, 3795, 2331, 3795, 2331, 3797 },
        // Port Phasmatys
        new int[] { 3661, 3509, 3655, 3509, 3652, 3506, 3652, 3474, 3649, 3471, 3649, 3458, 3651, 3456, 3659, 3456, 3661, 3454, 3664, 3454, 3665, 3453, 3674, 3453, 3677, 3455, 3692, 3455, 3694, 3453, 3701, 3453, 3703, 3454, 3708, 3454, 3711, 3457, 3711, 3461, 3709, 3463, 3709, 3466, 3702, 3473, 3702, 3477, 3700, 3477, 3700, 3481, 3699, 3481, 3699, 3482, 3698, 3482, 3698, 3480, 3690, 3484, 3690, 3486, 3691, 3487, 3691, 3493, 3690, 3493, 3690, 3494, 3689, 3494, 3689, 3491, 3688, 3491, 3687, 3492, 3687, 3493, 3686, 3494, 3686, 3500, 3685, 3501, 3685, 3502, 3687, 3502, 3687, 3504, 3684, 3504, 3683, 3505, 3683, 3506, 3682, 3507, 3681, 3507, 3681, 3518, 3677, 3518, 3668, 3509 },
        // Sophanem
        new int[] { 3273, 2810, 3324, 2811, 3324, 2747, 3273, 2748 },
        // Tutorial Island
        new int[] { 3154, 3136, 3054, 3136, 3054, 3057, 3085, 3040, 3085, 3004, 3125, 3004, 3125, 3037, 3154, 3064 },
        // Void Knights' Outpost
        new int[] { 2624, 2560, 2624, 2696, 2690, 2696, 2690, 2684, 2712, 2684, 2712, 2560 },
    };

    /**
     * Main world map (every map area, surface and dungeons alike): guarded zones are tinted
     * see-through green with a green outline, everything else on the map image is tinted
     * see-through red - OSRS Wiki: "No dungeons ... are considered safezones", so a dungeon or
     * cellar map is red all over. A legend explains the colours. Called after the map and its
     * icons are drawn, inside the map viewport clip.
     */
    static void draw(Toolkit toolkit) {
        int sourceWidth = WorldMap.anInt5647 - WorldMap.anInt5652;
        int sourceHeight = WorldMap.anInt5645 - WorldMap.anInt5654;
        if (sourceWidth <= 0 || sourceHeight <= 0 || WorldMap.areaWidth <= 0 || WorldMap.areaHeight <= 0) return;
        int left = WorldMap.anInt5649;
        int top = WorldMap.anInt5653;
        int right = WorldMap.anInt5651;
        int bottom = WorldMap.anInt5646;
        double scaleX = (right - left) / (double) sourceWidth;
        double scaleZ = (bottom - top) / (double) sourceHeight;
        double originX = left - (WorldMap.areaX + WorldMap.anInt5652) * scaleX;
        double originZ = bottom + (WorldMap.areaZ + WorldMap.anInt5654) * scaleZ;
        renderZones(toolkit, originX, scaleX, originZ, scaleZ, left, top, right, bottom, 2);
        drawLegend(toolkit, left + 10, bottom - 74);
    }

    /** Overview (mini) map: the same tints, 1-pixel outline. */
    static void drawOverview(Toolkit toolkit, int x, int y, int width, int height) {
        if (WorldMap.areaWidth <= 0 || WorldMap.areaHeight <= 0) return;
        double scaleX = width / (double) WorldMap.areaWidth;
        double scaleZ = height / (double) WorldMap.areaHeight;
        renderZones(toolkit, x - WorldMap.areaX * scaleX, scaleX, y + height + WorldMap.areaZ * scaleZ, scaleZ, x, y, x + width, y + height, 1);
    }

    /** Screen x = originX + tileX * scaleX; screen y = originZ - tileZ * scaleZ. */
    private static void renderZones(Toolkit toolkit, double originX, double scaleX, double originZ, double scaleZ, int left, int top, int right, int bottom, int outline) {
        // Only the map image itself is tinted, never the black border around a small map area.
        int l = Math.max(left, (int) Math.round(originX + WorldMap.areaX * scaleX));
        int r = Math.min(right, (int) Math.round(originX + (WorldMap.areaX + WorldMap.areaWidth) * scaleX));
        int t = Math.max(top, (int) Math.round(originZ - (WorldMap.areaZ + WorldMap.areaHeight) * scaleZ));
        int b = Math.min(bottom, (int) Math.round(originZ - WorldMap.areaZ * scaleZ));
        if (r <= l || b <= t) return;

        double[][] xs = new double[ZONES.length][];
        double[][] ys = new double[ZONES.length][];
        double[] minY = new double[ZONES.length];
        double[] maxY = new double[ZONES.length];
        int vertices = 0;
        for (int z = 0; z < ZONES.length; z++) {
            int n = ZONES[z].length / 2;
            xs[z] = new double[n];
            ys[z] = new double[n];
            minY[z] = Double.MAX_VALUE;
            maxY[z] = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                xs[z][i] = originX + ZONES[z][i * 2] * scaleX;
                ys[z][i] = originZ - ZONES[z][i * 2 + 1] * scaleZ;
                minY[z] = Math.min(minY[z], ys[z][i]);
                maxY[z] = Math.max(maxY[z], ys[z][i]);
            }
            vertices += n;
        }

        double[] crossings = new double[vertices];
        double[] rowCrossings = new double[vertices];
        int plainStart = -1; // first of a run of rows that contain no zone edge (one red rectangle)
        for (int row = t; row < b; row++) {
            double scanY = row + 0.5D;
            int count = 0;
            for (int z = 0; z < ZONES.length; z++) {
                if (scanY < minY[z] || scanY > maxY[z]) continue;
                int c = crossingsAt(xs[z], ys[z], scanY, rowCrossings);
                System.arraycopy(rowCrossings, 0, crossings, count, c);
                count += c;
            }
            if (count == 0) {
                if (plainStart < 0) plainStart = row;
                continue;
            }
            if (plainStart >= 0) {
                toolkit.aa(l, plainStart, r - l, row - plainStart, DANGEROUS_FILL, 1);
                plainStart = -1;
            }
            java.util.Arrays.sort(crossings, 0, count);
            int from = l;
            boolean safe = false;
            for (int k = 0; k < count; k++) {
                int to = Math.max(l, Math.min(r, (int) Math.round(crossings[k])));
                if (to > from) toolkit.U(from, row, to - from, safe ? SAFE_FILL : DANGEROUS_FILL, 1);
                from = Math.max(from, to);
                safe = !safe;
            }
            if (r > from) toolkit.U(from, row, r - from, DANGEROUS_FILL, 1);
        }
        if (plainStart >= 0) toolkit.aa(l, plainStart, r - l, b - plainStart, DANGEROUS_FILL, 1);

        // Green outline just inside every safe zone.
        for (int z = 0; z < ZONES.length; z++) {
            double inward = signedArea2(xs[z], ys[z]) > 0 ? -1.0D : 1.0D;
            for (int i = 0; i < xs[z].length; i++) {
                int j = (i + 1) % xs[z].length;
                double dx = xs[z][j] - xs[z][i];
                double dy = ys[z][j] - ys[z][i];
                double length = Math.sqrt(dx * dx + dy * dy);
                if (length == 0) continue;
                for (int w = 0; w < outline; w++) {
                    double ox = inward * dy / length * w;
                    double oy = inward * -dx / length * w;
                    toolkit.line((int) Math.round(xs[z][i] + ox), (int) Math.round(ys[z][i] + oy),
                        (int) Math.round(xs[z][j] + ox), (int) Math.round(ys[z][j] + oy), SAFE_LINE, 1);
                }
            }
        }
    }

    private static void drawLegend(Toolkit toolkit, int x, int y) {
        int width = 178;
        int height = 64;
        toolkit.aa(x, y, width, height, LEGEND_BACKGROUND, 1);
        toolkit.outlineRect(x, y, width, height, LEGEND_BORDER, 1);
        toolkit.outlineRect(x + 1, y + 1, width - 2, height - 2, 0x60000000, 1);
        WorldMapFont font = WorldMap.font12;
        if (font != null) font.render(toolkit, "Deadman Mode zones", null, x + 9, y + 5, LEGEND_TITLE, true);
        toolkit.aa(x + 8, y + 3 + 18, width - 16, 1, LEGEND_BORDER, 1);
        swatch(toolkit, x + 10, y + 27, SAFE_SWATCH, SAFE_LINE);
        swatch(toolkit, x + 10, y + 45, DANGEROUS_SWATCH, DANGEROUS_EDGE);
        if (font != null) {
            font.render(toolkit, "Safe - guarded", null, x + 32, y + 26, 0xFFFFFF, true);
            font.render(toolkit, "Dangerous - PvP", null, x + 32, y + 44, 0xFFFFFF, true);
        }
    }

    private static void swatch(Toolkit toolkit, int x, int y, int fill, int edge) {
        toolkit.aa(x, y, 16, 12, fill, 1);
        toolkit.outlineRect(x, y, 16, 12, edge, 1);
    }

    /** Sorted x crossings of the polygon's edges with the horizontal line y = [scanY]; returns the count. */
    static int crossingsAt(double[] xs, double[] ys, double scanY, double[] out) {
        int count = 0;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((ys[i] > scanY) != (ys[j] > scanY)) {
                out[count++] = xs[i] + (scanY - ys[i]) * (xs[j] - xs[i]) / (ys[j] - ys[i]);
            }
        }
        java.util.Arrays.sort(out, 0, count);
        return count;
    }

    /** Twice the signed area in screen coordinates (y down): positive means (dy, -dx) points outward. */
    static double signedArea2(double[] xs, double[] ys) {
        double sum = 0;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            sum += xs[j] * ys[i] - xs[i] * ys[j];
        }
        return sum;
    }

    // WorldMap.method5062 stores its source bounds relative to areaX/areaZ; the vertices are
    // absolute world tiles, so draw() converts them through areaX/areaZ exactly like these contracts.

    /** Pure projection contract used by the headless regression test. */
    static int projectX(int tileX, int areaX, int sourceMin, int sourceMax, int screenMin, int screenMax) {
        int relativeX = tileX - areaX;
        return screenMin + (screenMax - screenMin) * (relativeX - sourceMin) / (sourceMax - sourceMin);
    }

    /** Pure projection contract used by the headless regression test. */
    static int projectZ(int tileZ, int areaZ, int sourceMin, int sourceMax, int screenMin, int screenMax) {
        int relativeZ = tileZ - areaZ;
        return screenMax - (screenMax - screenMin) * (relativeZ - sourceMin) / (sourceMax - sourceMin);
    }

    private DeadmanWorldMapOverlay() {
    }
}
