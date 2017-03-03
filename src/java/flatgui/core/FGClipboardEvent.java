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

import java.awt.datatransfer.Transferable;

/**
 * @author Denis Lebedev
 */
public class FGClipboardEvent
{
    public static final int CLIPBOARD_FIRST = 403;
    public static final int CLIPBOARD_LAST = 405;

    public static final int CLIPBOARD_PASTE = CLIPBOARD_FIRST;
    public static final int CLIPBOARD_COPY = CLIPBOARD_FIRST+1;

    private final int type_;

    private Transferable data_;

    private FGClipboardEvent(int type, Transferable data)
    {
        type_ = type;
        data_ = data;
    }

    public static FGClipboardEvent createPasteEvent(Transferable data)
    {
        return new FGClipboardEvent(CLIPBOARD_PASTE, data);
    }

    public static FGClipboardEvent createCopyEvent()
    {
        return new FGClipboardEvent(CLIPBOARD_COPY, null);
    }

    public int getType()
    {
        return type_;
    }

    public Transferable getData()
    {
        return data_;
    }
}
