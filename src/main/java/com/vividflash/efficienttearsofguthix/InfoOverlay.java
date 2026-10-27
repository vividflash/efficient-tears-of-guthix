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
import java.awt.Point;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;

// Stream order and at Juna the XP skill, rebuilt only when one of them or a setting changes. Below
// them the timer line, whose text is updated in place once per game tick, unless the timer has its
// own box.
@Slf4j
class InfoOverlay extends OverlayPanel
{
    private final StreamAdvisor advisor;
    private final SkillReward skillReward;
    private final SkillIconManager skillIconManager;

    private final LineComponent timer = LineComponent.builder().build();
    private int builtInfoRevision = -1;
    private int timerRevision = -1;
    private boolean timerShown;

    @Inject
    InfoOverlay(StreamAdvisor advisor, SkillReward skillReward, SkillIconManager skillIconManager)
    {
        this.advisor = advisor;
        this.skillReward = skillReward;
        this.skillIconManager = skillIconManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setClearChildren(false);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try
        {
            if (!advisor.inArea() || !advisor.showInfo())
            {
                return null;
            }

            boolean rebuild = advisor.infoRevision() != builtInfoRevision;
            int revision = advisor.revision();
            if (revision != timerRevision)
            {
                timerRevision = revision;
                boolean shown = !advisor.splitTimer() && TimerLine.update(advisor, timer);
                rebuild |= shown != timerShown;
                timerShown = shown;
            }
            if (rebuild)
            {
                builtInfoRevision = advisor.infoRevision();
                rebuild();
            }

            return super.render(graphics);
        }
        catch (Exception e)
        {
            advisor.report("infoOverlay", e);
            return null;
        }
    }

    private void rebuild()
    {
        List<LayoutableRenderableEntity> lines = panelComponent.getChildren();
        lines.clear();

        String order = advisor.orderText();
        lines.add(LineComponent.builder()
            .left("Order:")
            .right(order == null ? "syncing" : order)
            .build());

        if (order != null && !advisor.orderSupported())
        {
            lines.add(LineComponent.builder()
                .left("Order not supported")
                .leftColor(Color.RED)
                .build());
        }

        Skill skill = advisor.atEntrance() && skillReward.showSkill() ? skillReward.skill() : null;
        if (skill != null)
        {
            lines.add(SplitComponent.builder()
                .first(new ImageComponent(skillIconManager.getSkillImage(skill, true)))
                .second(LineComponent.builder()
                    .left("XP:")
                    .right(skill.getName())
                    .build())
                .orientation(ComponentOrientation.HORIZONTAL)
                .gap(new Point(4, 0))
                .build());
        }

        if (timerShown)
        {
            lines.add(timer);
        }
        if (log.isDebugEnabled())
        {
            log.debug("[info] rebuilt order {} supported {} skill {} timer {}", order, advisor.orderSupported(), skill,
                timerShown);
        }
    }
}
