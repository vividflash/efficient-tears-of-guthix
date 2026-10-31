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

import java.awt.event.KeyEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;

// Holds Juna's last dialogue, whose continue starts the walk in, until the enter timing says now.
// Lets everything through while the timing is unknown or the info panel that shows it is off.
@Slf4j
@Singleton
public class EntryGuard implements KeyListener
{
    static final String ENTRY_DIALOGUE = "Collect as much as you can from the blue streams. If you let in water from the green streams, it will take away from the blue. For Guthix is god of balance, and balance lies in the juxtaposition of opposites.";

    @Inject
    private Client client;

    @Inject
    private KeyManager keyManager;

    @Inject
    private EfficientTearsOfGuthixConfig config;

    @Inject
    private StreamAdvisor advisor;

    private volatile boolean enabled;
    // An NPC dialogue is loaded, so the client tick only reads widgets while one can be up.
    private boolean dialogueLoaded;
    // Read on the key thread, written each client tick while a dialogue is up.
    private volatile boolean active;
    // A click or key was held back since the guard came on, for the debug log.
    private volatile boolean blocked;
    // The dialogue text last read, so the match runs once per text and not per frame.
    private String lastText;
    private boolean lastTextMatches;

    void start()
    {
        configChanged();
        keyManager.registerKeyListener(this);
    }

    void stop()
    {
        keyManager.unregisterKeyListener(this);
        dialogueLoaded = false;
        active = false;
    }

    void configChanged()
    {
        enabled = config.holdEntry();
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.CHAT_LEFT)
        {
            dialogueLoaded = true;
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() == InterfaceID.CHAT_LEFT && event.isUnload())
        {
            dialogueLoaded = false;
            setActive(false);
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!dialogueLoaded)
        {
            return;
        }

        try
        {
            setActive(enabled && advisor.showInfo() && advisor.entryWait() > 0 && entryDialogueOpen());
        }
        catch (Exception e)
        {
            advisor.report("entryGuardTick", e);
        }
    }

    private void setActive(boolean hold)
    {
        if (hold != active)
        {
            active = hold;
            if (log.isDebugEnabled())
            {
                log.debug("[entry] hold {} wait {} blocked {}", hold, advisor.entryWait(), blocked);
            }
            blocked = false;
        }
    }

    private boolean entryDialogueOpen()
    {
        Widget widget = client.getWidget(InterfaceID.ChatLeft.TEXT);
        if (widget == null || widget.isHidden())
        {
            return false;
        }

        String text = widget.getText();
        if (text != lastText)
        {
            lastText = text;
            lastTextMatches = isEntryDialogue(text);
        }
        return lastTextMatches;
    }

    static boolean isEntryDialogue(String widgetText)
    {
        return widgetText != null && ENTRY_DIALOGUE.equals(Text.removeTags(widgetText.replace("<br>", " ")));
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (active && event.getParam1() == InterfaceID.ChatLeft.CONTINUE)
        {
            blocked = true;
            event.consume();
        }
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        hold(e);
    }

    // Client key events reach the game as separate pressed and typed events: both are consumed.
    @Override
    public void keyTyped(KeyEvent e)
    {
        hold(e);
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
    }

    private void hold(KeyEvent e)
    {
        if (active && (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyChar() == ' '))
        {
            blocked = true;
            e.consume();
        }
    }
}
