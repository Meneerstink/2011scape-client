package com.jagex.js5;

public final class Js5Archive {

	public static final int ANIMS = 0;
	public static final int SPRITES = 8;
	public static final int BASES = 1;
	public static final int CONFIG = 2;
	public static final int INTERFACES = 3;
	public static final int SYNTH_SOUNDS = 4;
	public static final int MAPS = 5;
	public static final int MIDI_SONGS = 6;
	public static final int MODELS = 7;
	public static final int TEXTURES = 9;
	public static final int BINARY = 10;
	public static final int MIDI_JINGLES = 11;
	public static final int CLIENTSCRIPTS = 12;
	public static final int FONTMETRICS = 13;
	public static final int VORBIS = 14;
	public static final int CONFIG_LOC = 16;
	public static final int CONFIG_ENUM = 17;
	public static final int CONFIG_NPC = 18;
	public static final int CONFIG_OBJ = 19;
	public static final int CONFIG_SEQ = 20;
	public static final int CONFIG_SPOT = 21;
	public static final int CONFIG_STRUCT = 22;
	public static final int WORLDMAPDATA = 23;
	public static final int QUICKCHAT = 24;
	public static final int QUICKCHAT_GLOBAL = 25;
	public static final int MATERIALS = 26;
	public static final int CONFIG_PARTICLE = 27;
	public static final int DEFAULTS = 28;
	public static final int CONFIG_BILLBOARD = 29;
	public static final int DLLS = 30;
	public static final int SHADERS = 31;
	public static final int LOADING_SPRITES = 32;
	public static final int LOADING_SPRITES_RAW = 34;
	public static final int LOADING_SCREENS = 33;
	public static final int CUTSCENES = 35;
	public static final int VIDEOS = 36;
	/**
	 * 78legacy (N462, 2026-11-06): model ids 65536..131071. Archive 7 is full (one group per id, 65536 groups; the disk
	 * sector header and the JS5 request carry a 16-bit group), so a model id M >= 65536 lives in this archive as group
	 * M - 65536. {@link js5#overflow} routes every MODELS lookup of such an id here, so callers keep passing the plain id.
	 */
	public static final int MODELS_EXT = 37;
	public static final int MODELS_EXT_BASE = 65536;

	public static final int ARCHIVESET = 255;

	public static final int ID_LIMIT = 38;

	private Js5Archive() {
		/* empty */
	}
}
