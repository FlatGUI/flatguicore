/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import clojure.lang.Var;
import flatgui.core.FGHostStateEvent;
import flatgui.core.awt.AbstractHostComponent;
import flatgui.core.engine.*;
import flatgui.core.engine.Container;
import flatgui.core.websocket.FGWebInteropUtil;
import flatgui.run.FGRunUtil;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.NoninvertibleTransformException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Denis Lebedev
 */
public class FGAWTAppContainer extends FGAppContainer<FGWebInteropUtil>
{
    private HostComponent hostComponent_;

    public FGAWTAppContainer(Map<Object, Object> container)
    {
        this(container, DFLT_UNIT_SIZE_PX);
    }

    public FGAWTAppContainer(Map<Object, Object> container, int unitSizePx)
    {
        super(container.get(ClojureContainerParser.getIdKey()).toString(), container, new FGWebInteropUtil(unitSizePx), new FGClojureResultCollector(unitSizePx), unitSizePx);
    }

    public static FGAWTAppContainer loadSourceCreateAndInit(InputStream source, String containerNs, String containerVarName)
    {
        String sourceCode = new Scanner(source).useDelimiter("\\Z").next();

        System.out.println(clojure.lang.RT.byteCast(1));
        clojure.lang.Compiler.load(new StringReader(sourceCode));

        return createAndInit(containerNs, containerVarName);
    }

    public static FGAWTAppContainer createAndInit(String containerNs, String containerVarName)
    {
        Var containerVar = FGRunUtil.getVar(containerNs, containerVarName);
        return createAndInit(containerVar);
    }

    public static FGAWTAppContainer createAndInit(Var containerVar)
    {
        Map<Object, Object> container = (Map<Object, Object>) containerVar.get();

        FGAWTAppContainer appContainer = new FGAWTAppContainer(container);
        appContainer.initialize();

        return appContainer;
    }

    public final Component getComponent()
    {
        if (!EventQueue.isDispatchThread())
        {
            throw new IllegalStateException("this method should be called only on EDT");
        }
        if (hostComponent_ == null)
        {
            hostComponent_ = new HostComponent();
        }
        return hostComponent_;
    }

    public final void paintAllFromRoot(Consumer<List<Object>> primitivePainter) throws NoninvertibleTransformException
    {
        Container.IContainerAccessor containerAccessor = getContainer().getContainerAccessor();
        Container.IPropertyValueAccessor propertyValueAccessor = getContainer().getPropertyValueAccessor();
        getResultCollector().paintComponentWithChildren(
                primitivePainter,
                containerAccessor,
                propertyValueAccessor,
                Integer.valueOf(0));
    }


    //
    // Special methods needed for FGLegacyCoreGlue - will be refactored
    //

    public List<Object> getPaintAllSequence()
    {
        List<Object> sequence = new ArrayList<>(); // TODO can know list size in advance
        Container.IContainerAccessor containerAccessor = getContainer().getContainerAccessor();
        getResultCollector().collectPaintAllSequence(
                sequence,
                containerAccessor,
                Integer.valueOf(0));
        return sequence;
    }

    //
    //
    //


    private class HostComponent extends AbstractHostComponent
    {
        public HostComponent()
        {
            addComponentListener(new ComponentAdapter()
            {
                @Override
                public void componentResized(ComponentEvent e)
                {
                    int w = getWidth();
                    int h = getHeight();
                    FGHostStateEvent hostStateEvent = FGHostStateEvent.createHostSizeEvent(new Dimension(w, h));
                    evolve(hostStateEvent);
                }
            });
        }

        @Override
        protected void changeCursorIfNeeded() throws Exception
        {
            // TODO
        }

        @Override
        protected void paintAll(Graphics bg, double clipX, double clipY, double clipW, double clipH) throws Exception
        {
            paintAllFromRoot(primitive ->
            {
                if (primitive.size() > 0)
                {
                    if (primitive.get(0) instanceof String)
                    {
                        getPrimitivePainter().paintPrimitive(bg, primitive);
                    }
                    else
                    {
                        for (Object p : primitive)
                        {
                            getPrimitivePainter().paintPrimitive(bg, (List<Object>) p);
                        }
                    }
                }
            });
        }

        @Override
        protected void acceptEvolveReason(Object evolveReason)
        {
            try
            {
                evolve(evolveReason).get();
                repaint();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
