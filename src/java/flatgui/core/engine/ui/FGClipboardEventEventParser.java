/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import flatgui.core.FGClipboardEvent;

/**
 * @author Denis Lebedev
 */
public class FGClipboardEventEventParser extends FGFocusTargetedEventParser<FGClipboardEvent, FGClipboardEvent>
{
    @Override
    protected FGClipboardEvent reasonToEvent(FGClipboardEvent r)
    {
        return r;
    }
}
