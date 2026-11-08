package io.github.tidefox.warband.recap;

import io.github.tidefox.warband.core.Chat;
import io.github.tidefox.warband.core.Fmt;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

/** Builds the recap chat component (docs/05 §2.3). Literal text only. */
public final class RecapFormatter {

    private static final int LEADER_WIDTH = 18;

    private RecapFormatter() {}

    /**
     * @param owner null for the private copy; the dead player's name for the shared copy
     */
    public static ITextComponent build(RecapSummary s, RecapStore.Death d, int windowSec, String owner,
            boolean shared, boolean showTimeline, boolean shareButton) {
        ITextComponent rich = build(s, d, windowSec, owner, shared, showTimeline, shareButton, true);
        return Chat.fit(rich, build(s, d, windowSec, owner, shared, false, shareButton, false));
    }

    private static ITextComponent build(RecapSummary s, RecapStore.Death d, int windowSec, String owner,
            boolean shared, boolean showTimeline, boolean shareButton, boolean hovers) {
        TextComponentString root = new TextComponentString("");
        String title = shared ? "☠ " + owner + "'s death recap" : "☠ Death recap";
        root.appendSibling(Chat.text(title, TextFormatting.RED));
        if (s.killingBlow == null) {
            root.appendSibling(Chat.text(" · no damage recorded in the last " + windowSec + " s · cause: "
                    + d.cause, TextFormatting.GRAY));
            return root;
        }
        StringBuilder head = new StringBuilder();
        head.append(" · ").append(Fmt.num(s.totalTaken)).append(" taken in ")
                .append(Fmt.num(s.spanTicks / 20.0)).append(" s");
        if (s.totalMitigated >= 0.05f) {
            head.append(" · armor blocked ").append(Fmt.num(s.totalMitigated));
        }
        if (s.totalAbsorbed >= 0.05f) {
            head.append(" · absorption ").append(Fmt.num(s.totalAbsorbed));
        }
        if (s.totalHealed >= 0.05f) {
            head.append(" · healed ").append(Fmt.num(s.totalHealed));
        }
        root.appendSibling(Chat.text(head.toString(), TextFormatting.GRAY));

        for (RecapSummary.Source src : s.sources) {
            root.appendSibling(new TextComponentString("\n  "));
            String label = (src.killingBlow ? "✖ " : "") + src.name + (src.count > 1 ? " ×" + src.count : "");
            TextFormatting color = src.killingBlow ? TextFormatting.RED : TextFormatting.WHITE;
            root.appendSibling(hovers ? Chat.entity(label, src.entityNbt, color) : Chat.text(label, color));
            int width = label.length();
            if (src.weaponName != null) {
                root.appendSibling(new TextComponentString(" "));
                root.appendSibling(Chat.item(src.weaponName, hovers ? src.weaponNbt : null, TextFormatting.AQUA));
                width += src.weaponName.length() + 3;
            }
            root.appendSibling(Chat.text(Fmt.leader(width, LEADER_WIDTH), TextFormatting.DARK_GRAY));
            root.appendSibling(Chat.text(Fmt.num(src.taken), TextFormatting.YELLOW));
            StringBuilder notes = new StringBuilder();
            if (src.allBypass) {
                notes.append("   bypass");
            } else if (src.mitigated >= 0.05f) {
                notes.append("   (armor −").append(Fmt.num(src.mitigated)).append(')');
            }
            if (src.killingBlow) {
                notes.append("   killing blow");
                float over = s.killingBlow.overkill();
                if (over >= 0.05f) {
                    notes.append(", overkill ").append(Fmt.num(over));
                }
                if (s.killingBlow.is(Hit.ESTIMATED)) {
                    notes.append(" (est.)");
                }
            }
            if (notes.length() > 0) {
                root.appendSibling(Chat.text(notes.toString(), TextFormatting.GRAY));
            }
        }
        if (s.hiddenSources > 0) {
            String label = "+" + s.hiddenSources + " more";
            root.appendSibling(new TextComponentString("\n  "));
            root.appendSibling(Chat.text(label + Fmt.leader(label, LEADER_WIDTH), TextFormatting.DARK_GRAY));
            root.appendSibling(Chat.text(Fmt.num(s.hiddenTaken), TextFormatting.YELLOW));
        }
        boolean timeline = showTimeline && hovers;
        if (timeline || shareButton) {
            root.appendSibling(new TextComponentString("\n  "));
        }
        if (timeline) {
            root.appendSibling(Chat.hoverText("[timeline]", timeline(s, d.tick), TextFormatting.GREEN));
        }
        if (shareButton) {
            if (timeline) {
                root.appendSibling(new TextComponentString(" "));
            }
            TextComponentString share = Chat.text("[share]", TextFormatting.GOLD);
            share.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/recap share"));
            share.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponentString("Post this recap to everyone")));
            root.appendSibling(share);
        }
        return root;
    }

    /** One line per hit: {@code -6.8s Wyvern 9.5→6.1 (HP 31.4)}. */
    static String timeline(RecapSummary s, long deathTick) {
        StringBuilder sb = new StringBuilder();
        for (Hit h : s.hits) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append('-').append(Fmt.num((deathTick - h.tick) / 20.0)).append("s ");
            if (h.isHeal()) {
                sb.append("+").append(Fmt.num(h.raw)).append(" heal");
            } else {
                sb.append(h.source);
                if (h.weaponName != null) {
                    sb.append(" [").append(h.weaponName).append(']');
                }
                sb.append(' ').append(Fmt.num(h.raw)).append('→').append(Fmt.num(h.taken()));
                if (h.absorbed >= 0.05f) {
                    sb.append(" (abs ").append(Fmt.num(h.absorbed)).append(')');
                }
            }
            sb.append(" (HP ").append(Fmt.num(h.healthAfter())).append(')');
        }
        return sb.toString();
    }
}
