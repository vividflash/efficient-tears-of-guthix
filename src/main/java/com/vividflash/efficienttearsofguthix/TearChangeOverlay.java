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

/**
 * Each change of the tear counter, rising beside the counter for a moment.
 * Only the rise is per frame; the setting and the counter's place on screen
 * are read once per game tick.
 */
class TearChangeOverlay extends Overlay
{
    private static final int RISE = 40;
    private static final int GAP = 8;

    private final Client client;
    private final StreamAdvisor advisor;

    /** The advisor revision the field below was read at. */
    private int builtRevision = -1;
    /** Bounds of the tear counter, or null when it is not shown or the setting is off. */
    private Rectangle counter;

    @Inject
    TearChangeOverlay(Client client, StreamAdvisor advisor)
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
            if (advisor.tearChanges().isEmpty())
            {
                return null;
            }

            int revision = advisor.revision();
            if (revision != builtRevision)
            {
                builtRevision = revision;
                Widget widget = advisor.showTearChange() ? client.getWidget(InterfaceID.TogSidepanel.COUNT) : null;
                counter = widget == null || widget.isHidden() ? null : widget.getBounds();
            }

            Rectangle bounds = counter;
            if (bounds == null)
            {
                return null;
            }

            long now = System.nanoTime();
            int x = bounds.x + bounds.width + GAP;
            for (StreamAdvisor.TearChange change : advisor.tearChanges())
            {
                long age = now - change.nanos;
                if (age < 0 || age >= StreamAdvisor.TEAR_CHANGE_NANOS)
                {
                    continue;
                }

                int y = bounds.y + bounds.height - (int) (RISE * age / StreamAdvisor.TEAR_CHANGE_NANOS);
                OverlayUtil.renderTextLocation(graphics, new Point(x, y), change.text,
                    change.gain ? Color.GREEN : Color.RED);
            }
        }
        catch (Exception e)
        {
            advisor.report("tearChangeOverlay", e);
        }
        return null;
    }
}
