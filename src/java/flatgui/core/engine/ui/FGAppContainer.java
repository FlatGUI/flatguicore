/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import clojure.lang.Associative;
import clojure.lang.Keyword;
import flatgui.core.FGClipboardEvent;
import flatgui.core.FGHostStateEvent;
import flatgui.core.IFGEvolveConsumer;
import flatgui.core.IFGInteropUtil;
import flatgui.core.engine.AppContainer;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Denis Lebedev
 */
public class FGAppContainer<Interop extends IFGInteropUtil> extends AppContainer<FGClojureContainerParser, FGClojureResultCollector>
{
    public static final int DFLT_UNIT_SIZE_PX = 64;

    private static final Keyword INTEROP_KW = Keyword.intern("interop");

    private final FGMouseEventParser mouseEventParser_;
    private final Interop interopUtil_;

    public FGAppContainer(String containerId, Map<Object, Object> container, Interop interopUtil)
    {
        this(containerId, container, interopUtil, new FGClojureResultCollector(DFLT_UNIT_SIZE_PX), DFLT_UNIT_SIZE_PX);
    }

    public FGAppContainer(String containerId, Map<Object, Object> container, Interop interopUtil, FGClojureResultCollector resultCollector, int unitSizePx)
    {
        this(containerId, container, interopUtil, resultCollector, unitSizePx, new FGMouseEventParser(unitSizePx));
    }

    public FGAppContainer(String containerId, Map<Object, Object> container, Interop interopUtil, FGClojureResultCollector resultCollector, int unitSizePx,
                          FGMouseEventParser mouseEventParser)
    {
        super(containerId, new FGClojureContainerParser(),
                resultCollector, assocInterop(container, interopUtil));

        interopUtil_ = interopUtil;

        mouseEventParser_ = mouseEventParser;
        getInputEventParser().registerReasonClassParser(MouseEvent.class, mouseEventParser_);
        getInputEventParser().registerReasonClassParser(MouseWheelEvent.class, new FGMouseEventParser(unitSizePx));
        getInputEventParser().registerReasonClassParser(KeyEvent.class, new FGKeyEventParser());
        getInputEventParser().registerReasonClassParser(FGHostStateEvent.class, (c, inputEvent) ->
        {
            Map<Object, Integer> m = new HashMap<>();
            m.put(inputEvent, Integer.valueOf(0)); // Root is always 0
            return m;
        });
        getInputEventParser().registerReasonClassParser(FGClipboardEvent.class, new FGClipboardEventEventParser());
    }

    public final Interop getInteropUtil()
    {
        return interopUtil_;
    }

    public void addEvolveConsumer(IFGEvolveConsumer evolveConsumer)
    {
        getContainer().addEvolveConsumer(evolveConsumer);
    }

    private static Map<Object, Object> assocInterop(Map<Object, Object> container, IFGInteropUtil interopUtil)
    {
        return (Map<Object, Object>) ((Associative)container).assoc(INTEROP_KW, interopUtil);
    }
}
