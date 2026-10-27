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

import com.google.inject.Provides;
import java.awt.Color;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Efficient Tears of Guthix",
    description = "Experimental: Highlights the efficient spot(s) in ToG.",
    tags = {"tears", "guthix", "tog", "minigame"}
)
public class EfficientTearsOfGuthixPlugin extends Plugin
{
    private static final String CONFIG_GROUP = EfficientTearsOfGuthixConfig.GROUP;
    private static final String LAST_SEEN_VERSION_KEY = "lastSeenVersion";

    // The release the one-time notice below belongs to, not the packaged version.
    static final String VERSION = "0.4";
    private static final String UPDATE_MESSAGE = "Efficient ToG " + VERSION + ": Remaining Tick Timer can now be shown in the Interface, Improved Reminder and stops the Highlights when you are done";

    // Dark red for the one-time notice, legible on either chatbox background.
    private static final Color UPDATE_MESSAGE_COLOR = new Color(0x8B0000);

    @Inject
    private ConfigManager configManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private EventBus eventBus;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private StreamAdvisor streamAdvisor;

    @Inject
    private SkillReward skillReward;

    @Inject
    private TargetOverlay targetOverlay;

    @Inject
    private InfoOverlay infoOverlay;

    @Inject
    private TimerOverlay timerOverlay;

    @Inject
    private TearChangeOverlay tearChangeOverlay;

    @Inject
    private BarTicksOverlay barTicksOverlay;

    // The stored version already equals VERSION, so logins skip the config read.
    private boolean noticeHandled;

    @Override
    protected void startUp()
    {
        noticeHandled = false;
        streamAdvisor.start();
        eventBus.register(streamAdvisor);
        skillReward.start();
        eventBus.register(skillReward);
        targetOverlay.configChanged();
        overlayManager.add(targetOverlay);
        overlayManager.add(infoOverlay);
        overlayManager.add(timerOverlay);
        overlayManager.add(tearChangeOverlay);
        overlayManager.add(barTicksOverlay);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(targetOverlay);
        overlayManager.remove(infoOverlay);
        overlayManager.remove(timerOverlay);
        overlayManager.remove(tearChangeOverlay);
        overlayManager.remove(barTicksOverlay);
        eventBus.unregister(skillReward);
        skillReward.stop();
        eventBus.unregister(streamAdvisor);
        streamAdvisor.stop();
    }

    // Shows the update notice once, on the first login after the version changes. The stored
    // version gates it; once it matches, a flag skips the config read on later logins.
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (noticeHandled || event.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        noticeHandled = true;
        if (VERSION.equals(configManager.getConfiguration(CONFIG_GROUP, LAST_SEEN_VERSION_KEY)))
        {
            return;
        }

        configManager.setConfiguration(CONFIG_GROUP, LAST_SEEN_VERSION_KEY, VERSION);
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(new ChatMessageBuilder()
                .append(UPDATE_MESSAGE_COLOR, UPDATE_MESSAGE)
                .build())
            .build());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // the notice's stored version is no setting: nothing to read again
        if (CONFIG_GROUP.equals(event.getGroup()) && !LAST_SEEN_VERSION_KEY.equals(event.getKey()))
        {
            // before the advisor: its revision bump rebuilds overlays that read these caches
            skillReward.configChanged();
            targetOverlay.configChanged();
            streamAdvisor.configChanged();
        }
    }

    @Provides
    EfficientTearsOfGuthixConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(EfficientTearsOfGuthixConfig.class);
    }
}
