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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Marks the wall the rule picked, the next best one, and where the waiting
 * blue streams can and cannot land. What to mark is worked out once per game
 * tick; a frame only projects it onto the canvas.
 */
class TargetOverlay extends Overlay
{
    private static final Stroke STROKE = new BasicStroke(2);
    private static final Stroke DIM_STROKE = new BasicStroke(1);
    private static final int OUTLINE_WIDTH = 2;
    private static final int DIM_OUTLINE_WIDTH = 1;
    private static final int OUTLINE_FEATHER = 0;

    private static final Font TICKS_FONT = FontManager.getRunescapeBoldFont().deriveFont(32f);
    private static final Color TICKS_COLOR = Color.WHITE;
    private static final Color TICKS_ENDING_COLOR = new Color(255, 140, 0);
    private static final int TICKS_ENDING = 3;
    private static final Color DRY_TICKS_COLOR = Color.RED;
    private static final int TICKS_HEIGHT = 120;

    /** One wall to mark. A dim mark is a thin outline without a fill. */
    private static final class Mark
    {
        final int wall;
        final boolean tile;
        final boolean model;
        /** How to draw the object on the wall, or null when it has no model to draw. */
        final WallStyle style;
        final Color colour;
        final boolean dim;
        /**
         * The object whose hull is drawn when there is no model or stream hull to draw, found by
         * the first frame that needs one and replaced when a later search finds another.
         */
        TileObject hullOwner;
        /** A frame searched the tile for a hull owner, so a null hullOwner means there is none. */
        boolean searched;

        Mark(int wall, boolean model, boolean tile, Color colour, boolean dim, WallStyle style)
        {
            this.wall = wall;
            this.tile = tile;
            this.model = model;
            this.style = style;
            this.colour = colour;
            this.dim = dim;
        }
    }

    private final Client client;
    private final EfficientTearsOfGuthixConfig config;
    private final StreamAdvisor advisor;
    private final ModelOutlineRenderer outlineRenderer;

    /** The advisor revision the fields below were built from. */
    private int builtRevision = -1;
    private final List<Mark> marks = new ArrayList<>();
    private final LocalPoint[] wallPoints = new LocalPoint[TogLayout.WALLS];
    private final LocalPoint[] collectPoints = new LocalPoint[TogLayout.WALLS];
    /** The view and view base the points above were worked out for. */
    private WorldView pointsView;
    private int pointsBaseX;
    private int pointsBaseY;
    /** Wall to draw the stream countdown on, or -1. */
    private int ticksWall = -1;
    private String ticksText;
    private Color ticksColour;

    /** The settings, read again on a config change; the advisor's revision bump that follows rebuilds with them. */
    private volatile boolean highlightWall;
    private volatile WallStyle wallStyle;
    private volatile boolean highlightTile;
    private volatile boolean showPossible;
    private volatile boolean showBad;
    private volatile boolean showStreamTicks;
    private volatile boolean showDryTicks;
    private volatile boolean showSecond;
    private volatile Color bestColor;
    private volatile Color secondColor;
    private volatile Color possibleColor;
    private volatile Color badColor;

    @Inject
    TargetOverlay(Client client, EfficientTearsOfGuthixConfig config, StreamAdvisor advisor, ModelOutlineRenderer outlineRenderer)
    {
        this.client = client;
        this.config = config;
        this.advisor = advisor;
        this.outlineRenderer = outlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    /** Reads the settings again; called on start and on a change of this plugin's config, before the advisor's. */
    void configChanged()
    {
        highlightWall = config.highlightWall();
        wallStyle = config.wallStyle();
        highlightTile = config.highlightTile();
        showPossible = config.showPossible();
        showBad = config.showBad();
        showStreamTicks = config.showStreamTicks();
        showDryTicks = config.showDryTicks();
        showSecond = config.showSecond();
        bestColor = config.bestColor();
        secondColor = config.secondColor();
        possibleColor = config.possibleMarkColor();
        badColor = config.badMarkColor();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try
        {
            if (!advisor.inArea() || !advisor.orderSupported())
            {
                return null;
            }

            int revision = advisor.revision();
            if (revision != builtRevision)
            {
                builtRevision = revision;
                rebuild();
            }

            for (int i = 0; i < marks.size(); i++)
            {
                Mark mark = marks.get(i);
                if (mark.tile)
                {
                    draw(graphics, tilePoly(collectPoints[mark.wall]), mark.colour, mark.dim);
                }
                if (mark.model)
                {
                    markWall(graphics, mark);
                }
            }

            if (ticksWall >= 0)
            {
                streamTicks(graphics);
            }
        }
        catch (Exception e)
        {
            advisor.report("targetOverlay", e);
        }
        return null;
    }

    /** Once per game tick: which walls to mark in which colour, and the countdown text. */
    private void rebuild()
    {
        marks.clear();
        ticksWall = -1;
        boolean model = highlightWall;
        boolean tile = highlightTile;
        boolean possible = showPossible;
        boolean bad = showBad;
        boolean ticksOn = showStreamTicks;
        boolean dryOn = showDryTicks;
        if (!model && !tile && !ticksOn && !dryOn && !possible && !bad)
        {
            return;
        }

        WorldView view = client.getTopLevelWorldView();
        if (view == null)
        {
            return;
        }

        WallStyle style = null;
        if (model)
        {
            style = wallStyle;
            model = style != null;
        }

        // the tile marks and the bad marks are drawn on the collect tiles
        updatePoints(view, tile || bad);

        int wall = advisor.targetWall();
        boolean move = advisor.shouldMove();
        boolean showSecond = (model || tile || bad) && this.showSecond;
        int second = showSecond ? advisor.secondWall() : -1;
        if (bad || (possible && (model || tile)))
        {
            int[] wallMarks = advisor.wallMarks();
            Color badColour = bad ? badColor : null;
            Color possibleColour = possible ? possibleColor : null;
            for (int other = 0; other < TogLayout.WALLS; other++)
            {
                if (other == wall || (move && other == second && showSecond))
                {
                    continue;
                }
                if (bad && wallMarks[other] == TargetRule.MARK_BAD)
                {
                    add(other, false, true, style, badColour, true);
                }
                else if (possible && wallMarks[other] == TargetRule.MARK_POSSIBLE)
                {
                    add(other, model, tile, style, possibleColour, true);
                }
            }
        }

        // the best wall stays highlighted while you stand on it; the second best only when not at the best wall
        if (model || tile)
        {
            if (move && second >= 0 && showSecond)
            {
                add(second, model, tile, style, secondColor, false);
            }
            if (wall >= 0)
            {
                add(wall, model, tile, style, bestColor, false);
            }
        }

        if (!ticksOn && !dryOn)
        {
            return;
        }

        int own = advisor.ownWall();
        int ticks = ticksOn && own >= 0 ? advisor.ownTicks() : 0;
        if (ticks > 0)
        {
            ticksWall = own;
            ticksText = Integer.toString(ticks);
            ticksColour = ticks <= TICKS_ENDING ? TICKS_ENDING_COLOR : TICKS_COLOR;
        }
        else if (dryOn && own >= 0 && advisor.dryTicks() > 0)
        {
            ticksWall = own;
            ticksText = Integer.toString(advisor.dryTicks());
            ticksColour = DRY_TICKS_COLOR;
        }
    }

    /**
     * The walls do not move, so their points are worked out again only when the
     * view or its base changed, or for one that was not in the scene.
     */
    private void updatePoints(WorldView view, boolean collect)
    {
        int baseX = view.getBaseX();
        int baseY = view.getBaseY();
        if (view != pointsView || baseX != pointsBaseX || baseY != pointsBaseY)
        {
            Arrays.fill(wallPoints, null);
            Arrays.fill(collectPoints, null);
            pointsView = view;
            pointsBaseX = baseX;
            pointsBaseY = baseY;
        }

        for (int wall = 0; wall < TogLayout.WALLS; wall++)
        {
            if (wallPoints[wall] == null)
            {
                wallPoints[wall] = LocalPoint.fromWorld(view, TogLayout.wallX(wall), TogLayout.wallY(wall));
            }
            if (collect && collectPoints[wall] == null)
            {
                collectPoints[wall] = LocalPoint.fromWorld(view, TogLayout.collectX(wall), TogLayout.collectY(wall));
            }
        }
    }

    private void add(int wall, boolean model, boolean tile, WallStyle style, Color colour, boolean dim)
    {
        if (colour != null && (model || tile))
        {
            WallStyle drawn = model && hasModel(wall) ? style : null;
            marks.add(new Mark(wall, model, tile, colour, dim, drawn));
        }
    }

    /** The hull of the whole stream object: both renderables, joined when both have one. */
    private static Shape streamHull(DecorativeObject tears)
    {
        Shape first = tears.getConvexHull();
        Shape second = tears.getConvexHull2();
        if (first == null || second == null)
        {
            return first != null ? first : second;
        }

        Area area = new Area(first);
        area.add(new Area(second));
        return area;
    }

    /**
     * The hull of the first object on the tile that has a convex hull right now: a game object, else
     * the wall object. A found object becomes the mark's hull owner; null when there is none.
     */
    private static Shape searchHull(Tile tile, Mark mark)
    {
        // an owner whose hull is gone is dropped, so a search that finds nothing is not repeated
        mark.hullOwner = null;
        if (tile == null)
        {
            return null;
        }

        for (GameObject object : tile.getGameObjects())
        {
            Shape hull = object == null ? null : object.getConvexHull();
            if (hull != null)
            {
                mark.hullOwner = object;
                return hull;
            }
        }
        WallObject wallObject = tile.getWallObject();
        Shape hull = wallObject == null ? null : wallObject.getConvexHull();
        if (hull != null)
        {
            mark.hullOwner = wallObject;
        }
        return hull;
    }

    /** The convex hull of a game or wall object, the two kinds {@link #searchHull} keeps. */
    private static Shape hull(TileObject object)
    {
        if (object instanceof GameObject)
        {
            return ((GameObject) object).getConvexHull();
        }
        return object instanceof WallObject ? ((WallObject) object).getConvexHull() : null;
    }

    /** Ticks until the blue stream on the wall the player stands at moves, drawn on that wall. */
    private void streamTicks(Graphics2D graphics)
    {
        LocalPoint local = wallPoints[ticksWall];
        if (local == null)
        {
            return;
        }

        Font previous = graphics.getFont();
        graphics.setFont(TICKS_FONT);
        Point location = Perspective.getCanvasTextLocation(client, graphics, local, ticksText, TICKS_HEIGHT);
        if (location != null)
        {
            OverlayUtil.renderTextLocation(graphics, location, ticksText, ticksColour);
        }
        graphics.setFont(previous);
    }

    /** Checked once per rebuild: a blue or green stream always has a model, a dry wall's object is asked. */
    private boolean hasModel(int wall)
    {
        Tile tile = advisor.wallTile(wall);
        DecorativeObject tears = tile == null ? null : tile.getDecorativeObject();
        return tears != null
            && (StreamAdvisor.colourOf(tears.getId()) != TargetRule.EMPTY || tears.getConvexHull() != null);
    }

    /** Draws the object on the wall in the wall style; without a model, the hull of what stands there, or its tile. */
    private void markWall(Graphics2D graphics, Mark mark)
    {
        Tile tile = advisor.wallTile(mark.wall);
        DecorativeObject tears = tile == null ? null : tile.getDecorativeObject();
        Shape hull = null;
        if (tears != null && mark.style != null)
        {
            int width = mark.dim ? DIM_OUTLINE_WIDTH : OUTLINE_WIDTH;
            switch (mark.style)
            {
                case OUTLINE:
                    outlineRenderer.drawOutline(tears, width, mark.colour, OUTLINE_FEATHER);
                    return;
                case HULL:
                    hull = streamHull(tears);
                    break;
            }
            if (hull != null)
            {
                draw(graphics, hull, mark.colour, mark.dim);
                return;
            }
        }

        hull = hull(mark.hullOwner);
        if (hull == null && !(mark.searched && mark.hullOwner == null))
        {
            // no stream hull and no owner with a hull: search the tile, once per rebuild when it finds nothing
            mark.searched = true;
            hull = searchHull(tile, mark);
        }
        draw(graphics, hull != null ? hull : tilePoly(wallPoints[mark.wall]), mark.colour, mark.dim);
    }

    private static void draw(Graphics2D graphics, Shape shape, Color colour, boolean dim)
    {
        if (shape == null)
        {
            return;
        }

        if (dim)
        {
            Stroke previous = graphics.getStroke();
            graphics.setColor(colour);
            graphics.setStroke(DIM_STROKE);
            graphics.draw(shape);
            graphics.setStroke(previous);
        }
        else
        {
            OverlayUtil.renderPolygon(graphics, shape, colour, STROKE);
        }
    }

    private Shape tilePoly(LocalPoint local)
    {
        return local == null ? null : Perspective.getCanvasTilePoly(client, local);
    }
}
