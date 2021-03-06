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

import clojure.lang.Keyword;
import flatgui.core.*;
import flatgui.core.util.Tuple;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
* @author Denis Lebedev
*/
public abstract class AbstractHostComponent extends Canvas
{
    protected static final Map<Keyword, Integer> FG_TO_AWT_CUSROR_MAP;
    static
    {
        Map<Keyword, Integer> m = new HashMap<>();

        m.put(Keyword.intern("wait"), Cursor.WAIT_CURSOR);
        m.put(Keyword.intern("text"), Cursor.TEXT_CURSOR);
        m.put(Keyword.intern("ns-resize"), Cursor.N_RESIZE_CURSOR);
        m.put(Keyword.intern("ew-resize"), Cursor.W_RESIZE_CURSOR);
        m.put(Keyword.intern("nesw-resize"), Cursor.NE_RESIZE_CURSOR);
        m.put(Keyword.intern("nwse-resize"), Cursor.NW_RESIZE_CURSOR);

        FG_TO_AWT_CUSROR_MAP = Collections.unmodifiableMap(m);
    }

    private IFGPrimitivePainter primitivePainter_;

    private Image bufferImage_;

    private boolean appTriggered_ = false;

    private final FGAWTInteropUtil interopUtil_;

    private String lastUserDefinedFontStr_ = null;
    private Font lastUserDefinedFont_ = null;

    public AbstractHostComponent()
    {
        setFocusTraversalKeysEnabled(false);
        interopUtil_ = new FGAWTInteropUtil(FGContainer.UNIT_SIZE_PX);
        primitivePainter_ = new FGDefaultPrimitivePainter(FGContainer.UNIT_SIZE_PX);
        primitivePainter_.addFontChangeListener(e -> {
            Tuple newValue = e.getNewValue();
            lastUserDefinedFontStr_ = newValue.getFirst();
            lastUserDefinedFont_ = newValue.getSecond();
            interopUtil_.setReferenceFont(lastUserDefinedFontStr_, lastUserDefinedFont_);});
        setFocusable(true);

        Consumer<Object> eventConsumer = this::acceptEvolveReason;
        ActionListener resizeProcessor = e -> resetPendingImage();

        addMouseListener(new ContainerMouseListener(eventConsumer));
        addMouseMotionListener(new ContainerMouseMotionListener(eventConsumer));
        addMouseWheelListener(new ContainerMouseWheelListener(eventConsumer));
        addKeyListener(new ContainerKeyListener(eventConsumer));
        addComponentListener(new ContainerComponentListener(eventConsumer, resizeProcessor));
        setupBlinkHelperTimer(eventConsumer);
    }

    public final IFGInteropUtil getInterop()
    {
        return interopUtil_;
    }

    public static Timer setupBlinkHelperTimer(Consumer<Object> timerEventConsumer)
    {
        Timer blinkTimer = new Timer("FlatGUI Blink Helper Timer", true);
        blinkTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                //timerEventConsumer.accept(new FGTimerEvent(System.currentTimeMillis()));
            }
        }, 530, 530);
        return blinkTimer;
    }

    @Override
    public void paint(Graphics g)
    {
        if (appTriggered_)
        {
            // TODO possibly use dirty rects to optimize
            g.drawImage(getPendingImage(), 0, 0, null);
        }
        else
        {
            g.drawImage(getPendingImage(), 0, 0, null);
        }

        appTriggered_ = false;
    }

    @Override
    public void update(Graphics g)
    {
        try
        {
            Rectangle clipBounds = g.getClipBounds();
            double clipX = clipBounds.getX() / FGContainer.UNIT_SIZE_PX;
            double clipY = clipBounds.getY() / FGContainer.UNIT_SIZE_PX;
            double clipW = clipBounds.getWidth() / FGContainer.UNIT_SIZE_PX;
            double clipH = clipBounds.getHeight() / FGContainer.UNIT_SIZE_PX;

            Graphics bg = getBufferGraphics();
            ((Graphics2D) bg).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (lastUserDefinedFont_ != null)
            {
                bg.setFont(lastUserDefinedFont_);
            }
            else
            {
                interopUtil_.setReferenceFont(null, bg.getFont());
            }
            interopUtil_.setReferenceGraphics(bg);

            paintAll(bg, clipX, clipY, clipW, clipH);

            appTriggered_ = true;
            paint(g);

            changeCursorIfNeeded();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected abstract void changeCursorIfNeeded() throws Exception;

    //protected abstract Iterable<Object> getPaintList(double clipX, double clipY, double clipW, double clipH) throws Exception;
    protected abstract void paintAll(Graphics bg, double clipX, double clipY, double clipW, double clipH) throws Exception;

    Image getPendingImage()
    {
        if (bufferImage_ == null)
        {
            bufferImage_ = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        }
        return bufferImage_;
    }

    private Graphics getBufferGraphics()
    {
        return getPendingImage().getGraphics();
    }

    private void resetPendingImage()
    {
        bufferImage_ = null;
    }

    protected abstract void acceptEvolveReason(Object evolveReason);

    protected final IFGPrimitivePainter getPrimitivePainter()
    {
        return primitivePainter_;
    }

    // Inner classes

    private static class ContainerListener
    {
        private final Consumer<Object> eventConsumer_;

        ContainerListener(Consumer<Object> eventConsumer)
        {
            eventConsumer_ = eventConsumer;
        }

        protected final void eventImpl(Object e)
        {
            eventConsumer_.accept(e);
        }
    }

    private static class ContainerMouseListener extends ContainerListener implements MouseListener
    {
        ContainerMouseListener(Consumer<Object> eventConsumer)
        {super(eventConsumer);}
        @Override
        public void mouseClicked(MouseEvent e)
        {eventImpl(e);}
        @Override
        public void mousePressed(MouseEvent e)
        {eventImpl(e);}
        @Override
        public void mouseReleased(MouseEvent e)
        {eventImpl(e);}
        @Override
        public void mouseEntered(MouseEvent e)
        {eventImpl(e);}
        @Override
        public void mouseExited(MouseEvent e)
        {eventImpl(e);}
    }

    private static class ContainerMouseMotionListener extends ContainerListener implements MouseMotionListener
    {
        ContainerMouseMotionListener(Consumer<Object> eventConsumer)
        {super(eventConsumer);}
        @Override
        public void mouseDragged(MouseEvent e)
        {eventImpl(e);}
        @Override
        public void mouseMoved(MouseEvent e)
        {eventImpl(e);}
    }

    private static class ContainerMouseWheelListener extends ContainerListener implements MouseWheelListener
    {
        ContainerMouseWheelListener(Consumer<Object> eventConsumer)
        {super(eventConsumer);}
        @Override
        public void mouseWheelMoved(MouseWheelEvent e)
        {eventImpl(e);}
    }

    private static class ContainerKeyListener extends ContainerListener implements KeyListener
    {
        ContainerKeyListener(Consumer<Object> eventConsumer)
        {super(eventConsumer);}
        @Override
        public void keyTyped(KeyEvent e)
        {eventImpl(e);}
        @Override
        public void keyPressed(KeyEvent e)
        {eventImpl(e);}
        @Override
        public void keyReleased(KeyEvent e)
        {eventImpl(e);}
    }

    private static class ContainerComponentListener extends ContainerListener implements ComponentListener
    {
        private final ActionListener resizeProcessor_;

        ContainerComponentListener(Consumer<Object> eventConsumer, ActionListener resizeProcessor)
        {
            super(eventConsumer);
            resizeProcessor_ = resizeProcessor;
        }
        @Override
        public void componentResized(ComponentEvent e)
        {
            resizeProcessor_.actionPerformed(null);
            eventImpl(parseResizeEvent(e));
        }
        @Override
        public void componentMoved(ComponentEvent e) {}
        @Override
        public void componentShown(ComponentEvent e) {}
        @Override
        public void componentHidden(ComponentEvent e) {}

        private static FGHostStateEvent parseResizeEvent(ComponentEvent e)
        {
            return FGHostStateEvent.createHostSizeEvent(e.getComponent().getSize());
        }
    }
}
