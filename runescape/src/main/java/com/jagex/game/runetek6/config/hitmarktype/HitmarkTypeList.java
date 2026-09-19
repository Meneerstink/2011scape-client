package com.jagex.game.runetek6.config.hitmarktype;

import com.jagex.core.constants.ModeGame;
import com.jagex.core.datastruct.ref.ReferenceCache;
import com.jagex.core.io.Packet;
import com.jagex.game.runetek6.config.Js5ConfigGroup;
import com.jagex.js5.js5;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

@OriginalClass("client!ld")
public final class HitmarkTypeList {

    private static final int DEFAULT_CACHE_SIZE = 64;

    /** The cache's poison hitmark type (server HitType.POISON). */
    public static final int POISON_ID = 6;

    /** Server HitType.VENOM: the OSRS venom splat on the poison type's data (VenomHitmarkType). */
    public static final int VENOM_ID = 14;

    /** The cache's regular damage hitmark type (server HitType.REGULAR_HIT). */
    public static final int REGULAR_ID = 3;

    /** Server HitType.BURN (owner 2026-09-19): the OSRS burn splat on the regular damage type's data; past the cache's 0-27. */
    public static final int BURN_ID = 28;

    @OriginalMember(owner = "client!rla", name = "e", descriptor = "Lclient!ld;")
    public static HitmarkTypeList instance;

    @OriginalMember(owner = "client!ld", name = "n", descriptor = "Lclient!dla;")
    public final ReferenceCache hitmarkCache = new ReferenceCache(20);

    @OriginalMember(owner = "client!ld", name = "d", descriptor = "Lclient!dla;")
    public final ReferenceCache recentUse = new ReferenceCache(DEFAULT_CACHE_SIZE);

    private final ModeGame game;

    private final int languageId;

    @OriginalMember(owner = "client!ld", name = "l", descriptor = "Lclient!sb;")
    public final js5 configClient;

    @OriginalMember(owner = "client!ld", name = "g", descriptor = "Lclient!sb;")
    public final js5 sprites;

    private final int num;

    @OriginalMember(owner = "client!ld", name = "<init>", descriptor = "(Lclient!ul;ILclient!sb;Lclient!sb;)V")
    public HitmarkTypeList(@OriginalArg(0) ModeGame game, @OriginalArg(1) int languageId, @OriginalArg(2) js5 configClient, @OriginalArg(3) js5 sprites) {
        this.game = game;
        this.languageId = languageId;
        this.configClient = configClient;
        this.sprites = sprites;
        this.num = this.configClient.fileLimit(Js5ConfigGroup.HITMARKTYPE);
    }

    @OriginalMember(owner = "client!ld", name = "a", descriptor = "(I)V")
    public void cacheReset() {
        @Pc(2) ReferenceCache local2 = this.recentUse;
        synchronized (this.recentUse) {
            this.recentUse.reset();
        }
        local2 = this.hitmarkCache;
        synchronized (this.hitmarkCache) {
            this.hitmarkCache.reset();
        }
    }

    @OriginalMember(owner = "client!ld", name = "b", descriptor = "(I)V")
    public void cacheRemoveSoftReferences() {
        @Pc(2) ReferenceCache local2 = this.recentUse;
        synchronized (this.recentUse) {
            this.recentUse.removeSoftReferences();
        }
        local2 = this.hitmarkCache;
        synchronized (this.hitmarkCache) {
            this.hitmarkCache.removeSoftReferences();
        }
    }

    @OriginalMember(owner = "client!ld", name = "a", descriptor = "(II)V")
    public void cacheClean(@OriginalArg(1) int maxAge) {
        @Pc(6) ReferenceCache local6 = this.recentUse;
        synchronized (this.recentUse) {
            this.recentUse.clean(maxAge);
        }
        local6 = this.hitmarkCache;
        synchronized (this.hitmarkCache) {
            this.hitmarkCache.clean(maxAge);
        }
    }

    @OriginalMember(owner = "client!ld", name = "b", descriptor = "(II)Lclient!pb;")
    public HitmarkType list(@OriginalArg(0) int id) {
        @Pc(6) ReferenceCache local6 = this.recentUse;
        @Pc(16) HitmarkType type;
        synchronized (this.recentUse) {
            type = (HitmarkType) this.recentUse.get(id);
        }
        if (type != null) {
            return type;
        }

        @Pc(30) js5 local30 = this.configClient;
        @Pc(39) byte[] data;
        synchronized (this.configClient) {
            data = this.configClient.getfile(id == VENOM_ID ? POISON_ID : id == BURN_ID ? REGULAR_ID : id, Js5ConfigGroup.HITMARKTYPE);
        }

        // RSPS venom / burn (owner 2026-09-18/19, exactly like OSRS): this 2011 cache predates both, so each is a cache base type
        // (poison / regular damage) drawn with the real OSRS splat sprite (VenomHitmarkType); the server sends VENOM_ID / BURN_ID.
        type = id == VENOM_ID ? new VenomHitmarkType() : id == BURN_ID ? new VenomHitmarkType("burn", false) : new HitmarkType();
        type.myList = this;
        if (data != null) {
            type.decode(new Packet(data));
        }

        @Pc(66) ReferenceCache local66 = this.recentUse;
        synchronized (this.recentUse) {
            this.recentUse.put(type, id);
            return type;
        }
    }
}
