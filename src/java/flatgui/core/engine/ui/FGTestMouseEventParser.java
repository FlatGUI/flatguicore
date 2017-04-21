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

import flatgui.test.TestMouseSource;

import java.awt.event.MouseEvent;

/**
 * @author Denis Lebedev
 */
public class FGTestMouseEventParser extends FGMouseEventParser
{
    public FGTestMouseEventParser(int unitSizePx)
    {
        super(unitSizePx);
    }

    @Override
    protected void onTargetComponentFound(MouseEvent mouseEvent)
    {
        super.onTargetComponentFound(mouseEvent);
        Object source = mouseEvent.getSource();
        if (source instanceof TestMouseSource)
        {
            int x = ((TestMouseSource) source).getLocalX();
            int y = ((TestMouseSource) source).getLocalY();
            if (x >= 0 && y >= 0)
            {
                setMouseXRel(x);
                setMouseYRel(y);
            }
        }
    }
}
