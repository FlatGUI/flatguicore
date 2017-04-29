/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */
package flatgui.core.engine.ui;

import flatgui.core.engine.Container;

import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * @author Denis Lebedev
 */
public class FGTestMouseEventParser extends FGMouseEventParser
{
    static final double TEST_SCALE = 1024.0d;

    public FGTestMouseEventParser(int unitSizePx)
    {
        super(unitSizePx);
    }

    @Override
    public Map<MouseEvent, Integer> parseInputEvent(Container container, MouseEvent mouseEvent)
    {
        return super.parseInputEvent(container, mouseEvent);
    }

    public static int doubleToInt(double d)
    {
        return (int) (d * TEST_SCALE);
    }

    @Override
    protected double scaleMouseX(double mouseEventX)
    {
        return mouseEventX / TEST_SCALE;
    }

    @Override
    protected double scaleMouseY(double mouseEventY)
    {
        return mouseEventY / TEST_SCALE;
    }
}
