/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package flatgui.core.websocket;

import flatgui.core.FGClipboardEvent;
import flatgui.core.FGHostStateEvent;
import flatgui.core.awt.FGIncomingMouseWheelEvent;
import flatgui.core.engine.ui.FGKeyEventParser;
import flatgui.core.engine.ui.FGTransferable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Denis Lebedev
 */
public class FGInputEventDecoder
{
    public static final Component dummySourceComponent_ = new Container();

    private Collection<IParser<BinaryInput, ?>> binaryParsers_;

    public FGInputEventDecoder()
    {
        binaryParsers_ = new ArrayList<>();
        addBinaryParser(new MouseBinaryParser());
        addBinaryParser(new KeyBinaryParser());
        addBinaryParser(new ClipboardBinaryParser());
        addBinaryParser(new HostEventBinaryParser());
    }

    public final void addBinaryParser(IParser<BinaryInput, ?> parser)
    {
        binaryParsers_.add(parser);
    }

    public <E> E getInputEvent(BinaryInput binaryData)
    {
        return getInputEvent(binaryData, binaryParsers_);
    }

    private static <S, E> E getInputEvent(S input, Collection<IParser<S, ?>> parsers)
    {
        try
        {
            for (IParser p : parsers)
            {
                Object e = p.getInputEvent(input);
                if (e != null)
                {
                    return (E)e;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    // Inner classes

    public static class BinaryInput
    {
        private byte[] payload_;
        private int offset_;
        private int len_;

        public BinaryInput(byte[] payload, int offset, int len)
        {
            payload_ = payload;
            offset_ = offset;
            len_ = len;
        }

        public byte[] getPayload()
        {
            return payload_;
        }

        public int getOffset()
        {
            return offset_;
        }

        public int getLen()
        {
            return len_;
        }
    }

    public interface IParser<S, E>
    {
        public E getInputEvent(S jsonObj) throws Exception;
    }

    public static abstract class AbstractBinaryParser<E> implements IParser<BinaryInput, E>
    {
        public E getInputEvent(BinaryInput binaryData) throws Exception
        {
            byte[] array = binaryData.getPayload();
            int ofs = binaryData.getOffset();
            int id = array[ofs]+400;
            return parseImpl(binaryData, id);
        }

        protected abstract E parseImpl(BinaryInput binaryData, int id) throws Exception;
    }

    public static class MouseBinaryParser extends AbstractBinaryParser<MouseEvent>
    {
        @Override
        protected MouseEvent parseImpl(BinaryInput binaryData, int id) throws Exception
        {
            if (id >= MouseEvent.MOUSE_FIRST && id <= MouseEvent.MOUSE_LAST)
            {
                byte[] array = binaryData.getPayload();
                int ofs = binaryData.getOffset();

                int x = array[ofs + 1] & 0x7F;
                if ((array[ofs + 1] & 0x80) == 0x80)
                {
                    x += 0x80;
                }
                x += ((array[ofs + 3] & 0x70) << 4);
                if ((array[ofs + 3] & 0x80) == 0x80)
                {
                    x += 0x80 << 4;
                }

                int y = array[ofs + 2] & 0x7F;
                if ((array[ofs + 2] & 0x80) == 0x80)
                {
                    y += 0x80;
                }
                y += ((array[ofs + 3] & 0x0F) << 8);

                if (id == MouseEvent.MOUSE_WHEEL)
                {
                    int sX = array[ofs + 4];
                    int sY = array[ofs + 5];
                    return getMouseWheelEvent(id, x, y, sX, sY);
                }
                else
                {
                    return getMouseRegularEvent(id, x, y);
                }
            }
            else
            {
                return null;
            }
        }
    }

    public static class KeyBinaryParser extends AbstractBinaryParser<KeyEvent>
    {
        private boolean ctrlPressed_ = false;
        private boolean shiftPressed_ = false;
        private boolean altPressed_ = false;
        private boolean escPressed_ = false;

        @Override
        protected KeyEvent parseImpl(BinaryInput binaryData, int id) throws Exception
        {
            if (id >= KeyEvent.KEY_FIRST && id <= KeyEvent.KEY_LAST)
            {
                byte[] array = binaryData.getPayload();
                int ofs = binaryData.getOffset();

                int keyCode;
                int keyCodeLo = array[ofs + 1] & 0x7F;
                if ((array[ofs + 1] & 0x80) == 0x80)
                {
                    keyCodeLo += 0x80;
                }
                int keyCodeHi = array[ofs + 2] & 0x7F;
                if ((array[ofs + 2] & 0x80) == 0x80)
                {
                    keyCodeHi += 0x80;
                }
                keyCode = keyCodeHi*256+keyCodeLo;

                char charCode;
                int charCodeLo = array[ofs + 3] & 0x7F;
                if ((array[ofs + 3] & 0x80) == 0x80)
                {
                    charCodeLo += 0x80;
                }
                int charCodeHi = array[ofs + 4] & 0x7F;
                if ((array[ofs + 4] & 0x80) == 0x80)
                {
                    charCodeHi += 0x80;
                }
                charCode = (char)(charCodeHi*256+charCodeLo);

                //System.out.println("-DLTEMP- KeyBinaryParser.parseImpl " + keyCode + " " + charCode + " " + (int)charCode);

                if (keyCode == 0x0D)
                {
                    keyCode = KeyEvent.VK_ENTER;  // 0x0A
                    charCode = KeyEvent.VK_ENTER; // 0x0A
                }
                else if (keyCode == KeyEvent.VK_PERIOD && charCode == 0) // Here we get regular Del as num keyboard period key when num lock is off
                {
                    keyCode = KeyEvent.VK_DELETE;
                    charCode = KeyEvent.VK_DELETE;
                }
                else if (keyCode == 0xBE) // Period in all browsers
                {
                    keyCode = KeyEvent.VK_PERIOD;
                    charCode = '.';
                }

                // TODO send modifiers from client

                boolean modifierKey = false;
                if (keyCode == KeyEvent.VK_CONTROL)
                {
                    modifierKey = true;
                    if (id == KeyEvent.KEY_PRESSED) ctrlPressed_ = true;
                    if (id == KeyEvent.KEY_RELEASED) ctrlPressed_ = false;
                }
                if (keyCode == KeyEvent.VK_SHIFT)
                {
                    modifierKey = true;
                    if (id == KeyEvent.KEY_PRESSED) shiftPressed_ = true;
                    if (id == KeyEvent.KEY_RELEASED) shiftPressed_ = false;
                }
//                if (keyCode == KeyEvent.VK_ALT)
//                {
//                    modifierKey = true;
//                    if (id == KeyEvent.KEY_PRESSED) altPressed_ = true;
//                    if (id == KeyEvent.KEY_RELEASED) altPressed_ = false;
//                }
                // Esc to use in place of Ctrl in browser where OS does not let us Ctrl+Tab
                if (keyCode == KeyEvent.VK_ESCAPE)
                {
                    modifierKey = true;
                    if (id == KeyEvent.KEY_PRESSED) escPressed_ = true;
                    if (id == KeyEvent.KEY_RELEASED) escPressed_ = false;
                }

                int modifiers = 0;
                if (ctrlPressed_ || (escPressed_ && keyCode == KeyEvent.VK_TAB))
                {
                    modifiers |= InputEvent.CTRL_MASK;
                }
                if (shiftPressed_)
                {
                    modifiers |= InputEvent.SHIFT_MASK;
                }
                if (altPressed_)
                {
                    modifiers |= InputEvent.ALT_MASK;
                }

                KeyEvent e = new KeyEvent(dummySourceComponent_, id, 0,
                        modifierKey ? 0 : modifiers,
                        id == KeyEvent.KEY_TYPED ? KeyEvent.VK_UNDEFINED : keyCode,
                        charCode);
                if (!FGKeyEventParser.isClipboardEvent(e))
                {
                    return e;
                }
                else
                {
                    // Client sends explicit clipboard event
                    return null;
                }
            }
            else
            {
                return null;
            }
        }
    }

    public static class ClipboardBinaryParser extends AbstractBinaryParser<FGClipboardEvent>
    {

        @Override
        protected FGClipboardEvent parseImpl(BinaryInput binaryData, int id) throws Exception
        {
            if (id >= FGClipboardEvent.CLIPBOARD_FIRST && id <= FGClipboardEvent.CLIPBOARD_LAST)
            {
                if (id == FGClipboardEvent.CLIPBOARD_PASTE)
                {
                    byte[] array = binaryData.getPayload();
                    int ofs = binaryData.getOffset();

                    int lenLo = array[ofs + 1] & 0x7F;
                    if ((array[ofs + 1] & 0x80) == 0x80)
                    {
                        lenLo += 0x80;
                    }
                    int lenHi = array[ofs + 2] & 0x7F;
                    if ((array[ofs + 2] & 0x80) == 0x80)
                    {
                        lenHi += 0x80;
                    }
                    int len = lenHi * 256 + lenLo;

                    int contentType = array[ofs + 3];

                    if (contentType == FGClipboardEvent.CLIPBOARD_TEXT_PLAIN)
                    {
                        char[] charArray = new char[len];
                        for (int i = 0; i < len; i++)
                        {
                            charArray[i] = (char) array[ofs + 4 + i];
                        }
                        String data = String.valueOf(charArray);

                        System.out.println("-DLTEMP- ClipboardBinaryParser.parseImpl received data = " + data +
                                " lenLo = " + lenLo + " lenHi = " + lenHi + " len = " + len);

                        return FGClipboardEvent.createPasteEvent(FGTransferable.createTextTransferable(data));
                    }
                    else if (contentType == FGClipboardEvent.CLIPBOARD_IMAGE_PNG)
                    {
                        InputStream in = new ByteArrayInputStream(array, 4, array.length-4);
                        BufferedImage image = ImageIO.read(in);
                        return FGClipboardEvent.createPasteEvent(FGTransferable.createImageTransferable(image));
                    }
                    else
                    {
                        System.out.println("-DLTEMP- ClipboardBinaryParser.parseImpl content type not supported: " + contentType);
                        return null;
                    }
                }
                else if (id == FGClipboardEvent.CLIPBOARD_COPY)
                {
                    // TODO
                    // do we need this notification on server? Client natively copies the data
                    // into its clipboard. Then when user does paste, client generates paste
                    // event which carries the data to server. Same data, processed, actually
                    // returns to client packaged to look vector commands (like drawString).

                    System.out.println("-DLTEMP- ClipboardBinaryParser.parseImpl COPY EVENT");
                    return FGClipboardEvent.createCopyEvent();
                }
                else
                {
                    return null;
                }
            }
            else
            {
                return null;
            }
        }
    }

    public static class HostEventBinaryParser extends AbstractBinaryParser<FGHostStateEvent>
    {
        public static final String C = "c";
        public static final String S = "s";

        @Override
        protected FGHostStateEvent parseImpl(BinaryInput binaryData, int id) throws Exception
        {
            if (id >= FGHostStateEvent.HOST_FIRST && id <= FGHostStateEvent.HOST_LAST)
            {
                byte[] array = binaryData.getPayload();
                int ofs = binaryData.getOffset();

                int w;
                int wLo = array[ofs + 1] & 0x7F;
                if ((array[ofs + 1] & 0x80) == 0x80)
                {
                    wLo += 0x80;
                }
                int wHi = array[ofs + 2] & 0x7F;
                if ((array[ofs + 2] & 0x80) == 0x80)
                {
                    wHi += 0x80;
                }
                w = wHi*256+wLo;

                int h;
                int hLo = array[ofs + 3] & 0x7F;
                if ((array[ofs + 3] & 0x80) == 0x80)
                {
                    hLo += 0x80;
                }
                int hHi = array[ofs + 4] & 0x7F;
                if ((array[ofs + 4] & 0x80) == 0x80)
                {
                    hHi += 0x80;
                }
                h = (char)(hHi*256+hLo);

                FGHostStateEvent e = FGHostStateEvent.createHostSizeEvent(new Dimension(w, h));
                return e;
            }
            else
            {
                return null;
            }
        }
    }

    private static MouseEvent getMouseRegularEvent(int id, int x, int y)
    {
        boolean buttonEvent = id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED ||
                id == MouseEvent.MOUSE_CLICKED || id == MouseEvent.MOUSE_DRAGGED;
        int clickCount = buttonEvent && (id != MouseEvent.MOUSE_DRAGGED) ? 1 : 0;
        int button = buttonEvent ? MouseEvent.BUTTON1 : 0;
        MouseEvent e = new MouseEvent(dummySourceComponent_, id, 0,
                buttonEvent ? InputEvent.getMaskForButton(button) : 0,
                x, y, x, y,
                clickCount, false, button);
        return e;
    }

    private static MouseEvent getMouseWheelEvent(int id, int x, int y, int sX, int sY)
    {
        MouseWheelEvent e = new FGIncomingMouseWheelEvent(dummySourceComponent_, id, 0, 0, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, sY, (sY != 0 ? (int)Math.signum(sY) : (int)Math.signum(sX)), sX);
        return e;
    }
}
