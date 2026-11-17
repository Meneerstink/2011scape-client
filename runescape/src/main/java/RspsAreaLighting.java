/**
 * N615 (eigenaar 14-11 20:0x: 'alleen in toa dan iets donkerder maken'; 16-11 23:0x ToA volledig OSRS).
 * The ToA map squares carry their own environment trailer (code 0, flag 0x2 = global ambient). Environment.method8386 only honours
 * those values at lightDetail 1, the default player profile (RspsGraphicsProfile) is 0 -> ToA drew with the default ambient and the
 * floors measured 1.2-1.6x lighter than OSRS (Kaoz Entry guide 720p, work/claude-scripts/grafisch/floorv.py, 17-11).
 * Only for the ToA squares (raid copies + lobby) the trailer is honoured at every light detail, the rest of the game stays as it was.
 */
public final class RspsAreaLighting {

    /** True while the environment trailer of a ToA map square is being decoded (set by the static + dynamic map decoders). */
    public static boolean forced;

    /** Cache map squares of the ToA raid (ToaMap.CACHE_REGIONS: OSRS square +9,+24) and the lobby (13454). */
    private static final int[] TOA_SQUARES = {
        16488, 16490, 16492, 17000, 17002, 17004, 17512, 17514, 17516, 18024, 18026, 18028, 13454
    };

    private RspsAreaLighting() {
    }

    public static boolean isToa(int mapSquare) {
        for (int square : TOA_SQUARES) {
            if (square == mapSquare) {
                return true;
            }
        }
        return false;
    }
}
