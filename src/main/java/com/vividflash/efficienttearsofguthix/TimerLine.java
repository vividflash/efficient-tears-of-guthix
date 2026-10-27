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
import net.runelite.client.ui.overlay.components.LineComponent;

// The timer line shared by the info panel and the timer's own box.
final class TimerLine
{
    private TimerLine()
    {
    }

    // Sets the line to the entry timing at Juna or the session ticks left. False when there is
    // neither, or the info panel is off.
    static boolean update(StreamAdvisor advisor, LineComponent line)
    {
        if (!advisor.showInfo())
        {
            return false;
        }

        int entryWait = advisor.entryWait();
        int ticksLeft = advisor.ticksLeft();
        if (entryWait == 0)
        {
            line.setLeft("Enter:");
            line.setRight("now");
            line.setRightColor(Color.GREEN);
        }
        else if (entryWait > 0)
        {
            line.setLeft("Enter in:");
            line.setRight(entryWait + " ticks");
            line.setRightColor(Color.WHITE);
        }
        else if (ticksLeft > 0 && advisor.ticksLeftPlace().inOverlay())
        {
            line.setLeft("Ticks left:");
            line.setRight(String.valueOf(ticksLeft));
            line.setRightColor(Color.WHITE);
        }
        else
        {
            return false;
        }
        return true;
    }
}
