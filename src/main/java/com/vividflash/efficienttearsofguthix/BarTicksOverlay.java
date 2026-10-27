/*
 * Copyright (c) 2026, vividflash
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vividflash.efficienttearsofguthix;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

// The session ticks left, centred in the empty space above the time bar of the minigame panel. The
// setting and the ticks left are read once per game tick, the bar's bounds each frame, and the text
// is moved only when the bar moves.
class BarTicksOverlay extends Overlay
{
    static final int GAP = 4;

    private final Client client;
    private final StreamAdvisor advisor;

    // The advisor revision the widget and text were read at.
    private int builtRevision = -1;
    // The time bar, or null when there are no ticks left or the setting is off.
    private Widget widget;
    private String text;
    private int textWidth;
    // The bar bounds the point was placed at.
    private Rectangle bounds;
    private Point point;

    @Inject
    BarTicksOverlay(Client client, StreamAdvisor advisor)
    {
        this.client = client;
        this.advisor = advisor;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try
        {
            int revision = advisor.revision();
            if (revision != builtRevision)
            {
                builtRevision = revision;
                int ticksLeft = advisor.ticksLeft();
                widget = advisor.ticksLeftPlace().inInterface() && ticksLeft > 0
                    ? client.getWidget(InterfaceID.TogSidepanel.BAR)
                    : null;
                text = widget == null ? null : String.valueOf(ticksLeft);
                textWidth = widget == null ? 0 : graphics.getFontMetrics().stringWidth(text);
                bounds = null;
            }

            if (widget == null || widget.isHidden())
            {
                return null;
            }

            Rectangle bar = widget.getBounds();
            if (!bar.equals(bounds))
            {
                bounds = bar;
                point = textPoint(bar, textWidth);
            }

            OverlayUtil.renderTextLocation(graphics, point, text, Color.WHITE);
        }
        catch (Exception e)
        {
            advisor.report("barTicksOverlay", e);
        }
        return null;
    }

    // The text baseline: centred on the bar, GAP pixels above it.
    static Point textPoint(Rectangle bar, int textWidth)
    {
        return new Point(bar.x + (bar.width - textWidth) / 2, bar.y - GAP);
    }
}
