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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.QuantityFormatter;

// The skill the tears' XP goes to if played now, and the login reminder from the game's line that
// the next run is open until the run is done, the day ends or the plugin stops.
@Slf4j
@Singleton
public class SkillReward
{
    // The game's tie-break order: the skill with the least XP gets the tears, on a tie the first
    // eligible one in this list.
    static final Skill[] ORDER = {
        Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS,
        Skill.PRAYER, Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING, Skill.CRAFTING, Skill.RUNECRAFT,
        Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING,
        Skill.FLETCHING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
    };

    // Quest progress values at which the game unlocks Runecraft, Herblore and Sailing: the quests'
    // end states, as checked by the XP lamp script.
    private static final int RUNE_MYSTERIES_DONE = 6;
    private static final int DRUIDIC_RITUAL_DONE = 4;
    private static final int PANDEMONIUM_DONE = 50;

    // The game's line on the first login of the day while the next run is open.
    static final String ELIGIBLE_MESSAGE = "You are eligible to drink from the Tears of Guthix.";
    // The game's line at the end of a run.
    private static final String RUN_END_MESSAGE = "Tears collected: ";
    // The game's line when the time in the cave ends, before the reward.
    private static final String TIME_UP_MESSAGE = "Your time in the cave is up.";
    // Ticks after the end line to wait for the reward's XP.
    private static final int SUMMARY_WAIT_TICKS = 3;


    @Inject
    private Client client;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private EfficientTearsOfGuthixConfig config;

    @Inject
    private StreamAdvisor advisor;

    private volatile Skill skill;
    // The login screen was seen, so the next login is not a world hop.
    private volatile boolean fromLoginScreen;
    // Logged in from the login screen, the open flag is still to be checked.
    private volatile boolean loginPending;
    // The game said the next run is open: remind on the next tick with stats loaded.
    private volatile boolean eligibleSeen;
    // Reminded since the last login, so the game's line after a flag reminder adds nothing.
    private volatile boolean reminded;
    // Juna came into the scene: pick the skill on the next tick with stats loaded.
    private volatile boolean junaPending;
    // What set junaPending, for the debug log: juna, stat or config.
    private volatile String junaTrigger = "config";
    // A world hop is under way, so the next login is not from the login screen.
    private volatile boolean hopping;
    // A login or world hop whose reminder check is still to be logged.
    private volatile boolean checkPending;
    // The pending check is for a login from the login screen, not a world hop.
    private volatile boolean checkFromLogin;
    // The XP skill setting, cached for the spawn handler.
    private volatile boolean showSkill;
    // The XP skill and its refresh setting both on, cached for the stat handler.
    private volatile boolean refreshSkill;
    // Per skill the XP of its last stat change, -1 before the first one since the login screen.
    private final int[] lastXp = new int[Skill.values().length];
    // Per account hash, the UTC day the game's eligible line was seen. No game state says whether
    // this week's run is still open, so the flag lives from the day's first eligible line until the
    // run ends, the game day ends at 00:00 UTC or the plugin shuts down; on start no run is open.
    private final Map<Long, LocalDate> runOpen = new ConcurrentHashMap<>();
    // The XP per skill when the time in the cave ended, null outside the reward.
    private volatile int[] rewardStartXp;
    // The tears of the run that just ended, -1 while no end of run line is due.
    private volatile int summaryTears = -1;
    // Ticks left to wait for the reward's XP.
    private int summaryWait;

    void start()
    {
        skill = null;
        // enabled on the login screen: the next login is not a world hop
        fromLoginScreen = client.getGameState() == GameState.LOGIN_SCREEN;
        loginPending = false;
        eligibleSeen = false;
        reminded = false;
        rewardStartXp = null;
        summaryTears = -1;
        Arrays.fill(lastXp, -1);
        runOpen.clear();
        readConfig();
        junaPending = showSkill;
        junaTrigger = "config";
    }

    void stop()
    {
        runOpen.clear();
    }

    // A setting changed: pick again, so turning the XP skill on at Juna shows it.
    void configChanged()
    {
        readConfig();
        junaPending = showSkill;
        junaTrigger = "config";
    }

    private void readConfig()
    {
        showSkill = config.showSkill();
        refreshSkill = showSkill && config.refreshSkill();
    }

    // The skill the tears' XP goes to if played now, as of the last login, the last time Juna came
    // into the scene, or with the refresh setting on the last XP gain in the cave. Null before the
    // first pick.
    Skill skill()
    {
        return skill;
    }

    // The XP skill setting, as of the last config read.
    boolean showSkill()
    {
        return showSkill;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        try
        {
            GameState state = event.getGameState();
            if (state == GameState.LOGGED_IN)
            {
                loginPending = fromLoginScreen;
                // the check only feeds a debug line
                checkPending = log.isDebugEnabled() && (fromLoginScreen || hopping);
                checkFromLogin = fromLoginScreen;
                hopping = false;
                fromLoginScreen = false;
            }
            else if (state == GameState.HOPPING)
            {
                hopping = true;
            }
            else if (state == GameState.LOGIN_SCREEN)
            {
                hopping = false;
                checkPending = false;
                skill = null;
                fromLoginScreen = true;
                loginPending = false;
                eligibleSeen = false;
                reminded = false;
                junaPending = false;
                rewardStartXp = null;
                summaryTears = -1;
                Arrays.fill(lastXp, -1);
            }
        }
        catch (Exception e)
        {
            advisor.report("skillGameState", e);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (showSkill && event.getGameObject().getId() == ObjectID.TOG_JUNA)
        {
            junaTrigger = "juna";
            junaPending = true;
        }
    }

    // With the refresh setting on, an XP gain in the cave picks the skill again.
    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        int index = event.getSkill().ordinal();
        boolean gained = event.getXp() != lastXp[index];
        lastXp[index] = event.getXp();
        if (gained && refreshSkill && advisor.inArea())
        {
            junaTrigger = "stat";
            junaPending = true;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        try
        {
            if (event.getType() != ChatMessageType.GAMEMESSAGE)
            {
                return;
            }
            if (event.getMessage().contains(ELIGIBLE_MESSAGE))
            {
                eligibleSeen = true;
                runOpen.put(client.getAccountHash(), LocalDate.now(ZoneOffset.UTC));
                log.debug("[reminder] game eligible line seen");
            }
            else if (event.getMessage().contains(RUN_END_MESSAGE))
            {
                runOpen.remove(client.getAccountHash());
                log.debug("[reminder] run done, open flag cleared");
                if (rewardStartXp != null)
                {
                    summaryTears = parseTears(event.getMessage());
                    summaryWait = SUMMARY_WAIT_TICKS;
                }
            }
            else if (event.getMessage().contains(TIME_UP_MESSAGE) && config.runSummary())
            {
                rewardStartXp = client.getSkillExperiences().clone();
                log.debug("[summary] XP read at the end of the time");
            }
        }
        catch (Exception e)
        {
            advisor.report("skillChat", e);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        try
        {
            if (summaryTears >= 0)
            {
                postSummary();
            }

            if (checkPending && client.getOverallExperience() > 0)
            {
                checkPending = false;
                if (log.isDebugEnabled())
                {
                    log.debug("[reminder] login check login {} runOpen {} eligibleSeen {} reminded {} setting {}",
                        checkFromLogin, runOpen(), eligibleSeen, reminded, config.loginReminder());
                }
            }

            // outside the cave a pending Juna pick waits for the first tick inside
            boolean juna = junaPending && advisor.inArea();
            if ((!juna && !loginPending && !eligibleSeen) || client.getOverallExperience() <= 0)
            {
                return;
            }

            boolean remind = reminderDue();
            boolean junaPick = juna && showSkill;
            if (junaPick || remind)
            {
                int[] experience = client.getSkillExperiences();
                boolean[] open = eligible();
                Skill picked = pick(experience, open);
                if (log.isDebugEnabled())
                {
                    log.debug("[xp] pick {} -> {} trigger {} xp {} eligible {}", skill, picked,
                        junaPick ? junaTrigger : eligibleSeen ? "eligible" : "login",
                        picked == null ? -1 : experience[picked.ordinal()], Arrays.toString(open));
                }
                if (picked != skill)
                {
                    skill = picked;
                    advisor.infoChanged();
                }
            }
            if (juna)
            {
                junaPending = false;
            }
            // below the game's own line the reminder only adds the skill
            boolean gameLine = eligibleSeen;
            loginPending = false;
            eligibleSeen = false;
            if (remind)
            {
                postReminder(skill, gameLine);
            }
        }
        catch (Exception e)
        {
            advisor.report("skillTick", e);
        }
    }

    // True when the reminder is due.
    private boolean reminderDue()
    {
        // the open flag covers every login after the game's first one of the day
        return !reminded && config.loginReminder() && (eligibleSeen || (loginPending && runOpen()));
    }

    // Posts the reminder for this skill; below the game's own line it only adds the skill.
    private void postReminder(Skill picked, boolean gameLine)
    {
        reminded = true;
        if (log.isDebugEnabled())
        {
            log.debug("[reminder] posted, skill {}, game line {}", picked, gameLine);
        }
        remind(gameLine
            ? "The ToG XP goes to " + picked.getName() + "."
            : "Tears of Guthix is ready. The XP goes to " + picked.getName() + ".");
    }

    // Posts the end of run line once the reward's XP has arrived.
    private void postSummary()
    {
        int[] now = client.getSkillExperiences();
        Skill gained = biggestGain(rewardStartXp, now);
        if (gained == null && --summaryWait > 0)
        {
            return;
        }

        if (log.isDebugEnabled())
        {
            log.debug("[summary] tears {} skill {} wait left {}", summaryTears, gained, summaryWait);
        }
        if (gained != null)
        {
            remind(summaryLine(summaryTears, gained, now[gained.ordinal()] - rewardStartXp[gained.ordinal()]));
        }
        rewardStartXp = null;
        summaryTears = -1;
    }

    // The number after the game's end of run prefix, 0 when it cannot be read.
    static int parseTears(String message)
    {
        int start = message.indexOf(RUN_END_MESSAGE) + RUN_END_MESSAGE.length();
        int end = start;
        while (end < message.length() && Character.isDigit(message.charAt(end)))
        {
            end++;
        }
        return end == start ? 0 : Integer.parseInt(message.substring(start, end));
    }

    // The skill that gained the most XP, null when none did. Both arrays are indexed by Skill.ordinal().
    static Skill biggestGain(int[] before, int[] after)
    {
        Skill best = null;
        int bestGain = 0;
        for (Skill candidate : ORDER)
        {
            int gain = after[candidate.ordinal()] - before[candidate.ordinal()];
            if (gain > bestGain)
            {
                best = candidate;
                bestGain = gain;
            }
        }
        return best;
    }

    static String summaryLine(int tears, Skill skill, int xp)
    {
        return "Tears of Guthix: " + tears + " tears, " + QuantityFormatter.formatNumber(xp) + " " + skill.getName() + " XP.";
    }

    // Per entry of ORDER, whether the game counts that skill.
    private boolean[] eligible()
    {
        boolean[] eligible = new boolean[ORDER.length];
        for (int i = 0; i < ORDER.length; i++)
        {
            switch (ORDER[i])
            {
                case RUNECRAFT:
                    eligible[i] = client.getVarpValue(VarPlayerID.RUNEMYSTERIES) >= RUNE_MYSTERIES_DONE;
                    break;
                case HERBLORE:
                    eligible[i] = client.getVarpValue(VarPlayerID.DRUIDQUEST) >= DRUIDIC_RITUAL_DONE;
                    break;
                case SAILING:
                    eligible[i] = client.getVarbitValue(VarbitID.SAILING_INTRO) >= PANDEMONIUM_DONE;
                    break;
                case CONSTRUCTION:
                    // Construction XP can only be gained in an owned house
                    eligible[i] = client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION) != 0
                        || client.getSkillExperience(Skill.CONSTRUCTION) > 0;
                    break;
                default:
                    eligible[i] = true;
            }
        }
        return eligible;
    }

    // The eligible skill with the least XP, the first in ORDER on a tie.
    //
    // - experience: indexed by Skill.ordinal()
    // - eligible: indexed like ORDER
    static Skill pick(int[] experience, boolean[] eligible)
    {
        Skill best = null;
        for (int i = 0; i < ORDER.length; i++)
        {
            if (eligible[i] && (best == null || experience[ORDER[i].ordinal()] < experience[best.ordinal()]))
            {
                best = ORDER[i];
            }
        }
        return best;
    }

    // The game's eligible line was seen today on this account and no run has ended since.
    private boolean runOpen()
    {
        return LocalDate.now(ZoneOffset.UTC).equals(runOpen.get(client.getAccountHash()));
    }

    private void remind(String message)
    {
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(new ChatMessageBuilder()
                .append(message)
                .build())
            .build());
    }
}
