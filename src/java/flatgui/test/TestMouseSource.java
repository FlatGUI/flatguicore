/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */
package flatgui.test;

import java.awt.*;

/**
 * @author Denis Lebedev
 */
public class TestMouseSource extends Component
{
    private int localX_;

    private int localY_;

    public TestMouseSource(int localX, int localY)
    {
        localX_ = localX;
        localY_ = localY;
    }

    public int getLocalX()
    {
        return localX_;
    }

    public int getLocalY()
    {
        return localY_;
    }
}
