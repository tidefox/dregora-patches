package io.github.tidefox.dregorapatches.killfeed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Recent system chat lines as plain text, each usable by one kill (docs/03 §2.5). Pure, unit-tested;
 * {@link ChatTap} fills it from the server's chat sends.
 */
public final class ChatLines {

    static final class Line {
        final long tick;
        final String text, lower;
        /** Players this line went to one by one; a second copy to one of them is a second line. */
        final Set<Object> receivers = new HashSet<>();
        boolean used;

        Line(long tick, String text) {
            this.tick = tick;
            this.text = text;
            this.lower = text.toLowerCase(Locale.ROOT);
        }
    }

    /** More than this per tick is spam (a command dumping lines); older lines go first. */
    static final int MAX_LINES = 256;

    /** Names shorter than this match too much ("Ox" in "Oxford"). */
    static final int MIN_NAME = 3;

    private final List<Line> lines = new ArrayList<>();

    /**
     * A line sent to several players one by one ({@code receiver} = that player) counts once per tick,
     * but two same-text kills in one tick (two Blight slimes) stay two lines: the second copy to the
     * same player starts a new one. {@code receiver == null} (a broadcast) is always a new line.
     */
    public synchronized void add(long tick, String text, @Nullable Object receiver) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (receiver != null) {
            for (int i = lines.size() - 1; i >= 0 && lines.get(i).tick == tick; i--) {
                Line l = lines.get(i);
                if (l.text.equals(text) && !l.receivers.isEmpty() && l.receivers.add(receiver)) {
                    return;
                }
            }
        }
        if (lines.size() >= MAX_LINES) {
            lines.remove(0);
        }
        Line l = new Line(tick, text);
        if (receiver != null) {
            l.receivers.add(receiver);
        }
        lines.add(l);
    }

    /**
     * Claims the oldest unused line sent at or after {@code sinceTick} that contains one of
     * {@code names} (case-insensitive). Returns its text, or null. Scaling Health's
     * {@code Blight <name> …} line is left for a Blight: a split slime shares its parent's name.
     */
    @Nullable
    public synchronized String claim(long sinceTick, Collection<String> names, boolean blight) {
        for (Line l : lines) {
            if (l.used || l.tick < sinceTick) {
                continue;
            }
            for (String n : names) {
                if (n == null || n.length() < MIN_NAME) {
                    continue;
                }
                String name = n.toLowerCase(Locale.ROOT);
                if (l.lower.contains(name) && (blight || !l.lower.contains("blight " + name))) {
                    l.used = true;
                    return l.text;
                }
            }
        }
        return null;
    }

    /** Drops lines sent before {@code tick}. */
    public synchronized void prune(long tick) {
        for (Iterator<Line> it = lines.iterator(); it.hasNext(); ) {
            if (it.next().tick < tick) {
                it.remove();
            }
        }
    }

    public synchronized void clear() {
        lines.clear();
    }

    public synchronized int size() {
        return lines.size();
    }
}
