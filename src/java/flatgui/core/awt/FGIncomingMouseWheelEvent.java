/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package flatgui.core.awt;

import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.util.Collection;

/**
 * @author Denis Lebedev
 */
public class FGIncomingMouseWheelEvent extends MouseWheelEvent
{
    private int scrollAmountX_;

    public FGIncomingMouseWheelEvent(Component source, int id, long when, int modifiers,
                                     int x, int y, int clickCount, boolean popupTrigger,
                                     int scrollType, int scrollAmount, int wheelRotation,
                                     int scrollAmountX)
    {
        super(source, id, when, modifiers,
                x, y, clickCount, popupTrigger,
                scrollType, scrollAmount, wheelRotation);
    }

    public int getScrollAmountX()
    {
        return scrollAmountX_;
    }
}
