package com.jagex.core.stringtools.general;

import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

public final class Base37 {

    private static final int MAX_LENGTH = 12;

    // The 2011 client's own literals (night run 2026-10-12). The refactor computed these with encode(), which reads
    // MAX_PENULTIMATE_ENCODED while it was still 0 during class initialisation: encode() stopped after the first character,
    // MAX_ENCODED became encode("9") = 36 and decode() returned null for every real name - the friends chat channel name
    // among them, so the Friends Chat tab said "Not in chat" after every join and Leave chat could never be clicked.

    /** encode("a"). */
    private static final long MIN_ENCODED = 1L;

    /** encode("999999999999") = 37^12 - 1. */
    private static final long MAX_ENCODED = 6582952005840035280L;

    /** encode("99999999999") = 37^11 - 1: one more symbol would pass twelve. */
    private static final long MAX_PENULTIMATE_ENCODED = 177917621779460412L;

    @OriginalMember(owner = "client!kj", name = "c", descriptor = "[C")
    public static final char[] TABLE = {
        /* 00 */ '_',
        /* 01 */ 'a',
        /* 02 */ 'b',
        /* 03 */ 'c',
        /* 04 */ 'd',
        /* 05 */ 'e',
        /* 06 */ 'f',
        /* 07 */ 'g',
        /* 08 */ 'h',
        /* 09 */ 'i',
        /* 10 */ 'j',
        /* 11 */ 'k',
        /* 12 */ 'l',
        /* 13 */ 'm',
        /* 14 */ 'n',
        /* 15 */ 'o',
        /* 16 */ 'p',
        /* 17 */ 'q',
        /* 18 */ 'r',
        /* 19 */ 's',
        /* 20 */ 't',
        /* 21 */ 'u',
        /* 22 */ 'v',
        /* 23 */ 'w',
        /* 24 */ 'x',
        /* 25 */ 'y',
        /* 26 */ 'z',
        /* 27 */ '0',
        /* 28 */ '1',
        /* 29 */ '2',
        /* 30 */ '3',
        /* 31 */ '4',
        /* 32 */ '5',
        /* 33 */ '6',
        /* 34 */ '7',
        /* 35 */ '8',
        /* 36 */ '9',
    };

    @OriginalMember(owner = "client!jn", name = "a", descriptor = "(Ljava/lang/String;I)J")
    public static long encode(@OriginalArg(0) String string) {
        @Pc(12) long encoded = 0L;
        @Pc(15) int length = string.length();

        for (@Pc(17) int i = 0; i < length; i++) {
            encoded *= 37L;

            @Pc(26) char c = string.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                encoded += c + 1 - 'A';
            } else if (c >= 'a' && c <= 'z') {
                encoded += c + 1 - 'a';
            } else if (c >= '0' && c <= '9') {
                encoded += c + 27 - '0';
            }

            if (encoded > MAX_PENULTIMATE_ENCODED) {
                break;
            }
        }

        while (encoded % 37L == 0L && encoded != 0L) {
            encoded /= 37L;
        }

        return encoded;
    }

    @OriginalMember(owner = "client!jaa", name = "a", descriptor = "(JI)Ljava/lang/String;")
    public static String decode(@OriginalArg(0) long value) {
        if (value < MIN_ENCODED || value > MAX_ENCODED) {
            return null;
        } else if (value % 37L == 0L) {
            return null;
        } else {
            @Pc(39) int length = 0;
            @Pc(41) long v = value;

            while (v != 0L) {
                v /= 37L;
                length++;
            }

            @Pc(56) StringBuffer buffer = new StringBuffer(length);
            while (value != 0L) {
                @Pc(59) long t = value;
                value /= 37L;
                buffer.append(TABLE[(int) (t - (value * 37L))]);
            }

            return buffer.reverse().toString();
        }
    }

    @OriginalMember(owner = "client!dba", name = "a", descriptor = "(BJ)Ljava/lang/String;")
    public static String decodeName(@OriginalArg(1) long value) {
        if (value < MIN_ENCODED || value > MAX_ENCODED) {
            return null;
        } else if (value % 37L == 0L) {
            return null;
        } else {
            @Pc(29) int length = 0;
            @Pc(31) long v = value;

            while (v != 0L) {
                v /= 37L;
                length++;
            }

            @Pc(56) StringBuffer buffer = new StringBuffer(length);
            while (value != 0L) {
                @Pc(59) long t = value;
                value /= 37L;
                @Pc(73) char c = TABLE[(int) (t - (value * 37L))];

                if (c == '_') {
                    @Pc(83) int last = buffer.length() - 1;
                    buffer.setCharAt(last, Character.toUpperCase(buffer.charAt(last)));
                    c = ' ';
                }

                buffer.append(c);
            }

            buffer.reverse();
            buffer.setCharAt(0, Character.toUpperCase(buffer.charAt(0)));
            return buffer.toString();
        }
    }

    private Base37() {
        /* empty */
    }
}
