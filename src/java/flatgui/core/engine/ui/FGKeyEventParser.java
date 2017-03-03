/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import flatgui.core.FGClipboardEvent;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;

/**
 * @author Denis Lebedev
 */
public class FGKeyEventParser extends FGFocusTargetedEventParser<KeyEvent, Object>
{
    @Override
    protected Object reasonToEvent(KeyEvent r)
    {
        if (GraphicsEnvironment.isHeadless())
        {
            return r;
        }

        if (isClipboardPasteEvent(r))
        {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            return FGClipboardEvent.createPasteEvent(contents);
        }
        else if (isClipboardCopyEvent(r))
        {
            return FGClipboardEvent.createCopyEvent();
        }
        else
        {
            return r;
        }
    }

    public static boolean isClipboardEvent(KeyEvent r)
    {
        return isClipboardCopyEvent(r) || isClipboardPasteEvent(r);
    }

    public static boolean isClipboardCopyEvent(KeyEvent r)
    {
        return (r.getKeyCode() == KeyEvent.VK_C || r.getKeyCode() == KeyEvent.VK_INSERT)
                && (r.getModifiers() & KeyEvent.CTRL_MASK) != 0;
    }

    public static boolean isClipboardPasteEvent(KeyEvent r)
    {
        return (r.getKeyCode() == KeyEvent.VK_V && (r.getModifiers() & KeyEvent.CTRL_MASK) != 0
                || r.getKeyCode() == KeyEvent.VK_INSERT && (r.getModifiers() & KeyEvent.SHIFT_MASK) != 0);
    }
}
