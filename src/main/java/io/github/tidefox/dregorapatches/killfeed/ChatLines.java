package io.github.tidefox.dregorapatches.killfeed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * Recent system chat lines as plain text, each usable by one kill (docs/03 §2.5). Pure, unit-tested;
 * {@link ChatTap} fills it from the server's chat sends.
 */
public final class ChatLines {

    static final class Line {
        final long tick;
        final String text, lower;
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

    /** A line sent to several players one by one counts once per tick. */
    public synchronized void add(long tick, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        for (int i = lines.size() - 1; i >= 0 && lines.get(i).tick == tick; i--) {
            if (lines.get(i).text.equals(text)) {
                return;
            }
        }
        if (lines.size() >= MAX_LINES) {
            lines.remove(0);
        }
        lines.add(new Line(tick, text));
    }

    /**
     * Claims the oldest unused line sent at or after {@code sinceTick} that contains one of
     * {@code names} (case-insensitive). Returns its text, or null.
     */
    @Nullable
    public synchronized String claim(long sinceTick, Collection<String> names) {
        for (Line l : lines) {
            if (l.used || l.tick < sinceTick) {
                continue;
            }
            for (String n : names) {
                if (n != null && n.length() >= MIN_NAME && l.lower.contains(n.toLowerCase(Locale.ROOT))) {
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
