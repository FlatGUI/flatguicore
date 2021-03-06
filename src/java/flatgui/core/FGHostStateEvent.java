/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package flatgui.core;

import java.awt.*;

/**
 * @author Denis Lebedev
 */
public class FGHostStateEvent
{
    public enum EventType {Resize}

    public static final int HOST_FIRST = 406;
    public static final int HOST_LAST = 406;

    public static final int HOST_RESIZE = HOST_FIRST;

    private final EventType type_;

    private final Dimension hostSize_;

    private FGHostStateEvent(EventType type, Dimension hostSize)
    {
        type_ = type;
        hostSize_ = hostSize;
    }

    public static FGHostStateEvent createHostSizeEvent(Dimension hostSize)
    {
        return new FGHostStateEvent(EventType.Resize, hostSize);
    }

    public EventType getType()
    {
        return type_;
    }

    public Dimension getHostSize()
    {
        return hostSize_;
    }
}
