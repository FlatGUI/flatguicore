/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import clojure.lang.Keyword;
import clojure.lang.Var;
import flatgui.core.FGWebContainerWrapper;
import flatgui.core.IFGModule;
import flatgui.core.engine.Container;
import flatgui.core.engine.Node;
import flatgui.core.engine.remote.FGLegacyCoreGlue;
import flatgui.core.util.Tuple;
import flatgui.core.websocket.FGPaintVectorBinaryCoder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author Denis Lebedev
 */
public class FGRemoteClojureResultCollector extends FGClojureResultCollector
{
    private static final String RESPONSE_FEED_NS = IFGModule.RESPONSE_FEED_NS;

    private static final Var affineTransform_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "affinetransform");
    private static final Var clipSizeExtractor_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "clip-size-extractor");
    private static final Var extractRegularStrings_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-regular-strings");
    private static final Var extractResourceStrings_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-resource-strings");

    private static final Keyword CHILDREN_KW = Keyword.intern("children");
    private static final Keyword Z_POSITION_KW = Keyword.intern("z-position");
    private static final Keyword LOOK_VEC_KW = Keyword.intern("look-vec");

    private static final Keyword MODEL_KW = Keyword.intern("model");
    private static final Keyword CARET_LINE_KW = Keyword.intern("caret-line");
    private static final Keyword CARET_LINE_POS_KW = Keyword.intern("caret-line-pos");
    private static final Keyword SELECTION_MARK_LINE_KW = Keyword.intern("selection-mark-line");
    private static final Keyword SELECTION_MARK_LINE_POS_KW = Keyword.intern("selection-mark-line-pos");
    private static final Keyword LINES_KW = Keyword.intern("lines");

    public static final byte SET_CURSOR_COMMAND_CODE = 66;
    private static final byte DEFAULT_CURSOR_CODE = 8;
    private static final Map<String, Integer> CURSOR_NAME_TO_CODE;
    static
    {
        Map<String, Integer> m = new HashMap<>();
        m.put("alias", Integer.valueOf(0));
        m.put("all-scroll", Integer.valueOf(1));
        m.put("auto", Integer.valueOf(2));
        m.put("cell", Integer.valueOf(3));
        m.put("context-menu", Integer.valueOf(4));
        m.put("col-resize", Integer.valueOf(5));
        m.put("copy", Integer.valueOf(6));
        m.put("crosshair", Integer.valueOf(7));
        m.put("default", Integer.valueOf(DEFAULT_CURSOR_CODE));
        m.put("e-resize", Integer.valueOf(9));
        m.put("ew-resize", Integer.valueOf(10));
        m.put("help", Integer.valueOf(11));
        m.put("move", Integer.valueOf(12));
        m.put("n-resize", Integer.valueOf(13));
        m.put("ne-resize", Integer.valueOf(14));
        m.put("nw-resize", Integer.valueOf(15));
        m.put("nwse-resize", m.get("nw-resize"));
        m.put("ns-resize", Integer.valueOf(16));
        m.put("no-drop", Integer.valueOf(17));
        m.put("none", Integer.valueOf(18));
        m.put("not-allowed", Integer.valueOf(19));
        m.put("pointer", Integer.valueOf(20));
        m.put("progress", Integer.valueOf(21));
        m.put("row-resize", Integer.valueOf(22));
        m.put("s-resize", Integer.valueOf(23));
        m.put("se-resize", Integer.valueOf(24));
        m.put("sw-resize", Integer.valueOf(25));
        m.put("nesw-resize", m.get("sw-resize"));
        m.put("text", Integer.valueOf(26));
        m.put("vertical-text", Integer.valueOf(27));
        m.put("w-resize", Integer.valueOf(28));
        m.put("wait", Integer.valueOf(29));
        m.put("zoom-in", Integer.valueOf(30));
        m.put("zoom-out", Integer.valueOf(31));
        CURSOR_NAME_TO_CODE = Collections.unmodifiableMap(m);
    }

    private final FGWebContainerWrapper.IKeyCache keyCache_;
    private final FGLegacyCoreGlue.GlueModule glueModule_;
    private Supplier<List<Object>> paintAllSequenceSupplier_;

    private List<Object> paintAllList_;
    private TextSelectionModel textSelectionModel_;
    private List<String> selectedTextComponentLines_;
    private boolean textSelectionModelChanged_;
    private boolean cursorHasChanged_;
    private Set<Integer> removedComponentUids_;
    private List<Integer> addedComponentUids_;
    private List<Integer> addedComponentParentUids_;
    private Tuple removedAddedUidsTriple_;

    private final FGWebContainerWrapper.IDataTransmitter<List<Object>> paintAllTransmitter_;
    private final Map<Integer, List<String>> componentIdToStringPool_;
    private final FGWebContainerWrapper.IDataTransmitter<Map<Object, Object>> lookVecStringPoolMapTransmitter_;
    private final Map<Integer, List<String>> componentIdToResourceStringPool_;
    private final FGWebContainerWrapper.IDataTransmitter<Map<Object, Object>> lookVecResourceStringPoolMapTransmitter_;
    private final Map<Object, IDataTransmitterWrapper> propertyToTransWrapper_;
    private final Set<IDataTransmitterWrapper> allWrappers_;
    private final BooleanFlagsMapTransmitterWrapper booleanFlagsMapTransmitterWrapper_;
    private final TextSelectionModelTransmitter textSelectionModelTransmitter_;
    private final AddRemoveComponentsTransmitter addRemoveComponentsTransmitter_;

    private Collection<ByteBuffer> diffsToTransmit_;

    private boolean initialized_;

    public FGRemoteClojureResultCollector(int unitSizePx,
                                          FGWebContainerWrapper.IKeyCache keyCache,
                                          FGLegacyCoreGlue.GlueModule glueModule,
                                          Set<String> fontsWithMetricsAlreadyReceived)
    {
        super(unitSizePx);
        keyCache_ = keyCache;
        glueModule_ = glueModule;

        removedComponentUids_ = new HashSet<>();
        addedComponentUids_ = new ArrayList<>();
        addedComponentParentUids_ = new ArrayList<>();
        removedAddedUidsTriple_ = Tuple.triple(removedComponentUids_, addedComponentParentUids_, addedComponentUids_);

        diffsToTransmit_ = new ArrayList<>(12);

        componentIdToStringPool_ = new HashMap<>();
        componentIdToResourceStringPool_ = new HashMap<>();

        ///// TODO one constructor?
        paintAllTransmitter_ = new FGWebContainerWrapper.PaintAllTransmitter(keyCache_, null);
        lookVecStringPoolMapTransmitter_ = new FGWebContainerWrapper.StringPoolMapTransmitter(keyCache_, null);
        lookVecResourceStringPoolMapTransmitter_ = new FGWebContainerWrapper.ResourceStringPoolMapTransmitter(keyCache_, null);
        propertyToTransWrapper_ = new LinkedHashMap<>();
        addTransmitterWrapper(new ChildCountMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new IdentityMapTransmitterWrapper("look-vec", new FGWebContainerWrapper.LookVectorTransmitter(
                glueModule_::getStringPoolId, keyCache_, null, fontsWithMetricsAlreadyReceived)));
        booleanFlagsMapTransmitterWrapper_ = new BooleanFlagsMapTransmitterWrapper(keyCache_);
        addTransmitterWrapper(booleanFlagsMapTransmitterWrapper_);
        //addTransmitterWrapper(new ChildCountMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new ClipSizeMapTransmitterWrapper(keyCache_));
//        addTransmitterWrapper(new IdentityMapTransmitterWrapper("look-vec", new FGWebContainerWrapper.LookVectorTransmitter(
//                glueModule_::getStringPoolId, keyCache_, null, fontsWithMetricsAlreadyReceived)));
        addTransmitterWrapper(new ViewportMatrixMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new PositionMatrixMapTransmitterWrapper(keyCache_));
        // TODO client evolver, but that has to be redesigned into property change transition function, to be more generic
        allWrappers_ = new LinkedHashSet<>(propertyToTransWrapper_.values());
        textSelectionModelTransmitter_ = new TextSelectionModelTransmitter(glueModule_::getStringPoolId);
        addRemoveComponentsTransmitter_ = new AddRemoveComponentsTransmitter();
        /////
    }

    public FGRemoteClojureResultCollector(FGRemoteClojureResultCollector source,
                                          FGLegacyCoreGlue.GlueModule glueModule,
                                          Set<String> fontsWithMetricsAlreadyReceived)
    {
        super(source);

        keyCache_ = source.keyCache_;
        glueModule_ = glueModule;

        removedComponentUids_ = new HashSet<>(source.removedComponentUids_);
        addedComponentUids_ = new ArrayList<>(source.addedComponentUids_);
        addedComponentParentUids_ = new ArrayList<>(source.addedComponentParentUids_);
        removedAddedUidsTriple_ = Tuple.triple(removedComponentUids_, addedComponentParentUids_, addedComponentUids_);

        diffsToTransmit_ = new ArrayList<>(source.diffsToTransmit_);

        componentIdToStringPool_ = new HashMap<>(source.componentIdToStringPool_);
        componentIdToResourceStringPool_ = new HashMap<>(source.componentIdToResourceStringPool_);

        ///// TODO one constructor?
        paintAllTransmitter_ = new FGWebContainerWrapper.PaintAllTransmitter(keyCache_, null);
        lookVecStringPoolMapTransmitter_ = new FGWebContainerWrapper.StringPoolMapTransmitter(keyCache_, null);
        lookVecResourceStringPoolMapTransmitter_ = new FGWebContainerWrapper.ResourceStringPoolMapTransmitter(keyCache_, null);
        propertyToTransWrapper_ = new LinkedHashMap<>();
        addTransmitterWrapper(new ChildCountMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new IdentityMapTransmitterWrapper("look-vec", new FGWebContainerWrapper.LookVectorTransmitter(
                glueModule_::getStringPoolId, keyCache_, null, fontsWithMetricsAlreadyReceived)));
        booleanFlagsMapTransmitterWrapper_ = new BooleanFlagsMapTransmitterWrapper(keyCache_);
        addTransmitterWrapper(booleanFlagsMapTransmitterWrapper_);
        //addTransmitterWrapper(new ChildCountMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new ClipSizeMapTransmitterWrapper(keyCache_));
//        addTransmitterWrapper(new IdentityMapTransmitterWrapper("look-vec", new FGWebContainerWrapper.LookVectorTransmitter(
//                glueModule_::getStringPoolId, keyCache_, null, fontsWithMetricsAlreadyReceived)));
        addTransmitterWrapper(new ViewportMatrixMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new PositionMatrixMapTransmitterWrapper(keyCache_));
        // TODO client evolver, but that has to be redesigned into property change transition function, to be more generic
        allWrappers_ = new LinkedHashSet<>(propertyToTransWrapper_.values());
        textSelectionModelTransmitter_ = new TextSelectionModelTransmitter(glueModule_::getStringPoolId);
        addRemoveComponentsTransmitter_ = new AddRemoveComponentsTransmitter();
        /////
    }

    void initialize(Supplier<List<Object>> paintAllSequenceSupplier)
    {
        paintAllSequenceSupplier_ = paintAllSequenceSupplier;
    }

    @Override
    public void componentInitialized(Container container, Integer componentUid)
    {
        super.componentInitialized(container, componentUid);

        Container.IComponent component = container.getComponent(componentUid);
        Collection<Integer> indices = component.getPropertyIndices();
        for (Integer i : indices)
        {
            collectInitialDataForNode(container, i);
        }
    }

    @Override
    public void appendResult(int parentComponentUid, List<Object> path, Node node, Object newValue)
    {
        super.appendResult(parentComponentUid, path, node, newValue);

        if (initialized_)
        {
            collectResultForTransmitting(node.getComponentUid(), node.getPropertyId(), newValue);
        }
    }

    @Override
    public void postProcessAfterEvolveCycle(Container.IContainerAccessor containerAccessor, Container.IContainerMutator containerMutator)
    {
        cursorHasChanged_ = hasCursorChanged();

        super.postProcessAfterEvolveCycle(containerAccessor, containerMutator);

        if (initialized_)
        {
            prepareAccumulatedDataForTrasmitting();
        }

        cursorHasChanged_ = false;
    }

    public Collection<ByteBuffer> getInitialDataToTransmit(Container container)
    {
        resetAll(true);
        int nodeCount = container.getNodeCount();
        for (int i=0; i<nodeCount; i++)
        {
            collectInitialDataForNode(container, Integer.valueOf(i));
        }

        prepareAccumulatedDataForTrasmitting();
        initialized_ = true;
        return getDiffsToTransmit();
    }

    public Collection<ByteBuffer> getDiffsToTransmit()
    {
        resetAll(false);

        Collection<ByteBuffer> diffs = new ArrayList<>(diffsToTransmit_);
        diffsToTransmit_.clear();
        return diffs;
    }

    @Override
    protected void lookVectorGenerated(Integer componentUid, List<Object> lookVec)
    {
        super.lookVectorGenerated(componentUid, lookVec);
        collectResultForTransmitting(componentUid, LOOK_VEC_KW, lookVec);
    }

    @Override
    public void componentAdded(Integer parentComponentUid, Integer componentUid)
    {
        super.componentAdded(parentComponentUid, componentUid);
        addedComponentUids_.add(componentUid);
        addedComponentParentUids_.add(parentComponentUid);
    }

    @Override
    public void componentRemoved(Integer componentUid)
    {
        super.componentRemoved(componentUid);
        removedComponentUids_.add(componentUid);
        booleanFlagsMapTransmitterWrapper_.removeComponent(componentUid);
    }

    private void collectInitialDataForNode(Container container, Integer nodeIndex)
    {
        Node node = container.getNode(nodeIndex);
        collectResultForTransmitting(node.getComponentUid(), node.getPropertyId(), container.getPropertyValue(nodeIndex));
    }


    void collectResultForTransmitting(Integer componentUid, Object propertyId, Object newValue)
    {
        // Only :children and :z-position changes may affect paint all sequence
        if (propertyId == CHILDREN_KW || propertyId == Z_POSITION_KW)
        {
            List<Object> paintAllList = paintAllSequenceSupplier_.get();
            paintAllList_ = paintAllList;
        }

        {
            IDataTransmitterWrapper transmitterWrapper = propertyToTransWrapper_.get(propertyId);
            if (transmitterWrapper != null)
            {
                transmitterWrapper.appendResult(componentUid, propertyId, newValue);

                if (propertyId == LOOK_VEC_KW)
                {
                    componentIdToStringPool_.put(componentUid, (List<String>) extractRegularStrings_.invoke(newValue));
                    componentIdToResourceStringPool_.put(componentUid, (List<String>) extractResourceStrings_.invoke(newValue));
                }
            }
        }

        if (propertyId == MODEL_KW)
        {
            if (collectTextSelectionModel(componentUid, newValue))
            {
                textSelectionModelChanged_ = true;
            }
        }
    }

    private boolean collectTextSelectionModel(Integer componentUid, Object newValue)
    {
        if (newValue instanceof Map)
        {
            Map<Keyword, Object> model = (Map<Keyword, Object>) newValue;
            Object caretLine = model.get(CARET_LINE_KW);
            Object caretLinePos = model.get(CARET_LINE_POS_KW);
            Object selectionMarkLine = model.get(SELECTION_MARK_LINE_KW);
            Object selectionMarkLinePos = model.get(SELECTION_MARK_LINE_POS_KW);
            Object lines = model.get(LINES_KW);
            if (caretLine instanceof Number &&
                caretLinePos instanceof Number &&
                selectionMarkLine instanceof Number &&
                selectionMarkLinePos instanceof Number &&
                lines instanceof List)
            {
                TextSelectionModel newModel = new TextSelectionModel(((Number) caretLine).intValue(),
                        ((Number) caretLinePos).intValue(),
                        ((Number) selectionMarkLine).intValue(),
                        ((Number) selectionMarkLinePos).intValue());
                if (textSelectionModel_ == null ||
                        (!textSelectionModel_.equals(newModel) && (textSelectionModel_.isNonEmpty() || newModel.isNonEmpty())))
                {
                    textSelectionModel_ = newModel;
                    selectedTextComponentLines_ = (List<String>) lines;
                    textSelectionModelTransmitter_.setTextSelectionModel(textSelectionModel_, selectedTextComponentLines_, componentUid);
                    return true;
                }
            }
        }
        return false;
    }

    private void resetAll(boolean full)
    {
        paintAllList_ = null;
        textSelectionModelChanged_ = false;
        componentIdToStringPool_.clear();
        componentIdToResourceStringPool_.clear();
        for (IDataTransmitterWrapper w : allWrappers_)
        {
            w.reset(full);
        }
        removedComponentUids_.clear();
        addedComponentUids_.clear();
        addedComponentParentUids_.clear();
    }

    private void prepareAccumulatedDataForTrasmitting()
    {
        if (paintAllList_ != null)
        //if (paintAllListChanged_)
        {
            // TODO Transmit it (or even everything) only once after init. It makes no sense to transmit repeatedly during the init process
            diffsToTransmit_.add(paintAllTransmitter_.convertToBinary(paintAllTransmitter_.getCommandCode(), paintAllList_));
        }

        if (!componentIdToStringPool_.isEmpty())
        {
            Map<Object, Object> stringPoolDiffs = glueModule_.getStringPoolDiffs((Map) componentIdToStringPool_);
            diffsToTransmit_.add(lookVecStringPoolMapTransmitter_.convertToBinary(lookVecStringPoolMapTransmitter_.getCommandCode(), stringPoolDiffs));
        }
        if (!componentIdToResourceStringPool_.isEmpty())
        {
            Map<Object, Object> resourceStringPoolDiffs = glueModule_.getStringPoolDiffs((Map) componentIdToResourceStringPool_);
            diffsToTransmit_.add(lookVecResourceStringPoolMapTransmitter_.convertToBinary(lookVecResourceStringPoolMapTransmitter_.getCommandCode(), resourceStringPoolDiffs));
        }

        for (IDataTransmitterWrapper w : allWrappers_)
        {
            ByteBuffer byteBuffer = w.commitAndWriteDataDiff();
            if (byteBuffer != null)
            {
                diffsToTransmit_.add(byteBuffer);
            }
        }

        if (textSelectionModelChanged_)
        {
             diffsToTransmit_.add(textSelectionModelTransmitter_.convertToBinary(textSelectionModelTransmitter_.getCommandCode(), textSelectionModel_));
        }

        if (cursorHasChanged_)
        {
            Keyword cursor = getLatestCursor();
            Integer cursorCode = cursor != null ? CURSOR_NAME_TO_CODE.get(cursor.getName()) : null;
            byte cursorCodeByte = cursorCode != null ? cursorCode.byteValue() : DEFAULT_CURSOR_CODE;
            ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[]{SET_CURSOR_COMMAND_CODE, cursorCodeByte});
            diffsToTransmit_.add(byteBuffer);
        }

        if (!removedComponentUids_.isEmpty() || !addedComponentUids_.isEmpty())
        {
            System.out.println("-DLTEMP- FGRemoteClojureResultCollector.prepareAccumulatedDataForTrasmitting-----ADD/REMOVE");
            diffsToTransmit_.add(
                    addRemoveComponentsTransmitter_.convertToBinary(addRemoveComponentsTransmitter_.getCommandCode(), removedAddedUidsTriple_));
            removedComponentUids_.clear();
            addedComponentUids_.clear();
            addedComponentParentUids_.clear();
        }
    }

    private void addTransmitterWrapper(IDataTransmitterWrapper wrapper)
    {
        for (Object contribProperty : wrapper.getContributingPropertyIds())
        {
            propertyToTransWrapper_.put(contribProperty, wrapper);
        }
    }

    // Inner classes

    private interface IDataTransmitterWrapper
    {
        Set<Object> getContributingPropertyIds();

        void appendResult(Integer componentUid, Object propertyId, Object newValue);

        ByteBuffer commitAndWriteDataDiff();

        void reset(boolean full);
    }

    private static abstract class AbstractDataTransmitterWrapper<V> implements IDataTransmitterWrapper
    {
        private final FGWebContainerWrapper.IDataTransmitter<Map<Object, Object>> dataTransmitter_;

        protected final Map<Integer, V> componentUidToData_;

        public AbstractDataTransmitterWrapper(FGWebContainerWrapper.IDataTransmitter<Map<Object, Object>> dataTransmitter)
        {
            dataTransmitter_ = dataTransmitter;
            componentUidToData_ = new HashMap<>();
        }

        @Override
        public ByteBuffer commitAndWriteDataDiff()
        {
            if (hasDataToTransmit())
            {
                //System.out.println("-DLTEMP- commitAndWriteDataDiff NEW " + dataTransmitter_.getCommandCode() + " coding: " + componentUidToData_);
                FGWebContainerWrapper.DEBUG_OP = "NEW";
                ByteBuffer b = dataTransmitter_.convertToBinary(dataTransmitter_.getCommandCode(), (Map) componentUidToData_);
                FGWebContainerWrapper.DEBUG_OP = "OLD";
                //reset(false);
                return b;
            }
            else
            {
                return null;
            }
        }

        protected boolean hasDataToTransmit()
        {
            return !componentUidToData_.isEmpty();
        }

        protected final Set<Object> setOf(Object... elems)
        {
            Set<Object> s = new HashSet<>(elems.length);
            Collections.addAll(s, elems);
            return s;
        }

        @Override
        public void reset(boolean full)
        {
            componentUidToData_.clear();
        }
    }

    private static class IdentityMapTransmitterWrapper extends AbstractDataTransmitterWrapper<Object>
    {
        private final Set<Object> propertyIdSet_;

        public IdentityMapTransmitterWrapper(String propertyName, FGWebContainerWrapper.IDataTransmitter<Map<Object, Object>> dataTransmitter)
        {
            super(dataTransmitter);
            propertyIdSet_ = Collections.unmodifiableSet(setOf(Keyword.intern(propertyName)));
        }

        @Override
        public Set<Object> getContributingPropertyIds()
        {
            return propertyIdSet_;
        }

        @Override
        public void appendResult(Integer componentUid, Object propertyId, Object newValue)
        {
            if (newValue != null)
            {
                componentUidToData_.put(componentUid, newValue);
            }
        }
    }

    private static class ChildCountMapTransmitterWrapper extends AbstractDataTransmitterWrapper<Number>
    {
        ChildCountMapTransmitterWrapper(FGWebContainerWrapper.IKeyCache keyCache)
        {
            super(new FGWebContainerWrapper.ChildCountMapTransmitter(keyCache, null));
        }

        @Override
        public Set<Object> getContributingPropertyIds()
        {
            return setOf(Keyword.intern("children"));
        }

        @Override
        public void appendResult(Integer componentUid, Object propertyId, Object newValue)
        {
            if (newValue != null)
            {
                componentUidToData_.put(componentUid, ((Map) newValue).size());
            }
        }
    }

    private static class ClipSizeMapTransmitterWrapper extends AbstractDataTransmitterWrapper<Object>
    {
        ClipSizeMapTransmitterWrapper(FGWebContainerWrapper.IKeyCache keyCache)
        {
            super(new FGWebContainerWrapper.ClipRectTransmitter(keyCache, null));
        }

        @Override
        public Set<Object> getContributingPropertyIds()
        {
            return setOf(Keyword.intern("clip-size"));
        }

        @Override
        public void appendResult(Integer componentUid, Object propertyId, Object newValue)
        {
            if (newValue != null)
            {
                componentUidToData_.put(componentUid, clipSizeExtractor_.invoke(newValue));
            }
        }
    }

    private static class PositionMatrixMapTransmitterWrapper extends AbstractDataTransmitterWrapper<Object>
    {
        PositionMatrixMapTransmitterWrapper(FGWebContainerWrapper.IKeyCache keyCache)
        {
            super(new FGWebContainerWrapper.PositionMatrixMapTrasmitter(keyCache, null));
        }

        @Override
        public Set<Object> getContributingPropertyIds()
        {
            return setOf(Keyword.intern("position-matrix"));
        }

        @Override
        public void appendResult(Integer componentUid, Object propertyId, Object newValue)
        {
            if (newValue != null)
            {
                componentUidToData_.put(componentUid, affineTransform_.invoke(newValue));
            }
        }
    }

    private static class ViewportMatrixMapTransmitterWrapper extends AbstractDataTransmitterWrapper<Object>
    {
        ViewportMatrixMapTransmitterWrapper(FGWebContainerWrapper.IKeyCache keyCache)
        {
            super(new FGWebContainerWrapper.ViewportMatrixMapTrasmitter(keyCache, null));
        }

        @Override
        public Set<Object> getContributingPropertyIds()
        {
            return setOf(Keyword.intern("viewport-matrix"));
        }

        @Override
        public void appendResult(Integer componentUid, Object propertyId, Object newValue)
        {
            if (newValue != null)
            {
                componentUidToData_.put(componentUid, affineTransform_.invoke(newValue));
            }
        }
    }

    private static class BooleanFlagsMapTransmitterWrapper extends AbstractDataTransmitterWrapper<Byte>
    {
        private static final byte STATE_FLAGS_VISIBILITY_MASK = 1;
        private static final byte STATE_FLAGS_POPUP_MASK = 2;
        private static final byte STATE_FLAGS_ROLLOVER_DISABLED_MASK = 4;
        private static final byte STATE_FLAGS_PRECISE_TEXT_MEASUREMENT = 8;

        private static final Keyword ROLLOWER_NOTIFY_DISABLED_KW = Keyword.intern("rollover-notify-disabled");
        private static final Keyword POPUP_KW = Keyword.intern("popup");
        private static final Keyword VISIBLE_KW = Keyword.intern("visible");
        private static final Keyword EDITABLE_KW = Keyword.intern("editable");

        private boolean hasDataToTransmit_;

        BooleanFlagsMapTransmitterWrapper(FGWebContainerWrapper.IKeyCache keyCache)
        {
            super(new FGWebContainerWrapper.BooleanFlagsMapTransmitter(keyCache, null));
        }

        @Override
        public Set<Object> getContributingPropertyIds()
        {
            return setOf(ROLLOWER_NOTIFY_DISABLED_KW,
                         POPUP_KW,
                         VISIBLE_KW,
                         EDITABLE_KW);
        }

        @Override
        public void appendResult(Integer componentUid, Object propertyId, Object newValue)
        {
            boolean newValIsTrue = newValue != null && !(newValue instanceof Boolean && !((Boolean) newValue).booleanValue());
            //if (newValIsTrue)
            {
                Byte oldFlags = componentUidToData_.get(componentUid);
                Byte newFlags;
                if (propertyId == ROLLOWER_NOTIFY_DISABLED_KW)
                {
                    newFlags = changeFlag(oldFlags, STATE_FLAGS_ROLLOVER_DISABLED_MASK, newValIsTrue);
                }
                else if (propertyId == POPUP_KW)
                {
                    newFlags = changeFlag(oldFlags, STATE_FLAGS_POPUP_MASK, newValIsTrue);
                }
                else if (propertyId == VISIBLE_KW)
                {
                    newFlags = changeFlag(oldFlags, STATE_FLAGS_VISIBILITY_MASK, newValIsTrue);
                }
                else if (propertyId == EDITABLE_KW)
                {
                    newFlags = changeFlag(oldFlags, STATE_FLAGS_PRECISE_TEXT_MEASUREMENT, newValIsTrue);
                }
                else
                {
                    throw new IllegalArgumentException("Unsupported property: " + propertyId);
                }
                componentUidToData_.put(componentUid, newFlags);
            }
        }

        @Override
        protected boolean hasDataToTransmit()
        {
            return hasDataToTransmit_;
        }

        @Override
        public void reset(boolean full)
        {
            if (full)
            {
                super.reset(full);
            }
            else
            {
                hasDataToTransmit_ = false;
            }
        }

        void removeComponent(Integer componentUid)
        {
            componentUidToData_.remove(componentUid);
        }

        private Byte changeFlag(Byte oldFlags, byte flagMask, boolean add)
        {
            hasDataToTransmit_ = true;
            if (add)
            {
                return Byte.valueOf(oldFlags != null ? (byte) (oldFlags.byteValue() | flagMask) : flagMask);
            }
            else
            {
                return Byte.valueOf(oldFlags != null ? (byte) (oldFlags.byteValue() & ~flagMask) : 0);
            }
        }
    }

    private static class TextSelectionModel
    {
        private final int caretLine_;
        private final int caretLinePos_;
        private final int selectionMarkLine_;
        private final int selectionMarkLinePos_;

        TextSelectionModel(int caretLine, int caretLinePos, int selectionMarkLine, int selectionMarkLinePos)
        {
            caretLine_ = caretLine;
            caretLinePos_ = caretLinePos;
            selectionMarkLine_ = selectionMarkLine;
            selectionMarkLinePos_ = selectionMarkLinePos;
        }

        TextSelectionModel(TextSelectionModel source)
        {
            caretLine_ = source.caretLine_;
            caretLinePos_ = source.caretLinePos_;
            selectionMarkLine_ = source.selectionMarkLine_;
            selectionMarkLinePos_ = source.selectionMarkLinePos_;
        }

        int getCaretLine()
        {
            return caretLine_;
        }

        int getCaretLinePos()
        {
            return caretLinePos_;
        }

        int getSelectionMarkLine()
        {
            return selectionMarkLine_;
        }

        int getSelectionMarkLinePos()
        {
            return selectionMarkLinePos_;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TextSelectionModel that = (TextSelectionModel) o;

            if (getCaretLine() != that.getCaretLine()) return false;
            if (getCaretLinePos() != that.getCaretLinePos()) return false;
            if (getSelectionMarkLine() != that.getSelectionMarkLine()) return false;
            return getSelectionMarkLinePos() == that.getSelectionMarkLinePos();
        }

        @Override
        public int hashCode()
        {
            int result = getCaretLine();
            result = 31 * result + getCaretLinePos();
            result = 31 * result + getSelectionMarkLine();
            result = 31 * result + getSelectionMarkLinePos();
            return result;
        }

        @Override
        public String toString()
        {
            return caretLine_ +
                    "-" + caretLinePos_ +
                    "-" + selectionMarkLine_ +
                    "-" + selectionMarkLinePos_;
        }

        boolean isMultiLine()
        {
            return getCaretLine() != getSelectionMarkLine();
        }

        boolean isNonEmpty()
        {
            return isMultiLine()
                    || getCaretLinePos() != getSelectionMarkLinePos();
        }
    }

    /*
     * This assumes text model is passed to client line-by-line -- this is what textdield's look vector does
     */
    private static class TextSelectionModelTransmitter extends FGWebContainerWrapper.AbstractTransmitter<TextSelectionModel>
    {
        private final FGPaintVectorBinaryCoder.StringPoolIdSupplier stringPoolIdSupplier_;

        private TextSelectionModel textSelectionModel_;
        private List<String> selectedTextComponentLines_;
        private Integer textSelectionComponentUid_;

        public TextSelectionModelTransmitter(FGPaintVectorBinaryCoder.StringPoolIdSupplier stringPoolIdSupplier)
        {
            stringPoolIdSupplier_ = stringPoolIdSupplier;
        }

        @Override
        public int writeBinary(ByteArrayOutputStream stream, int n, TextSelectionModel data)
        {
            if (textSelectionModel_.isNonEmpty())
            {
                boolean caretLineFirst = textSelectionModel_.getCaretLine() <= textSelectionModel_.getSelectionMarkLine();
                int firstLine = caretLineFirst ? textSelectionModel_.getCaretLine() : textSelectionModel_.getSelectionMarkLine();
                int lastLine = caretLineFirst ? textSelectionModel_.getSelectionMarkLine() : textSelectionModel_.getCaretLine();
                //System.out.println("-DLTEMP- TextSelectionModelTransmitter.writeBinary " + (caretLineFirst ? "CAR FIRST" : "CAR LAST"));

                String selStartLine = selectedTextComponentLines_.get(firstLine);
                int selStartLinePoolId = stringPoolIdSupplier_.getStringPoolId(selStartLine, textSelectionComponentUid_);
                int selEndLinePoolUid;
                if (textSelectionModel_.isMultiLine())
                {
                    String selEndLine = selectedTextComponentLines_.get(lastLine);
                    selEndLinePoolUid = stringPoolIdSupplier_.getStringPoolId(selEndLine, textSelectionComponentUid_);
                }
                else
                {
                    selEndLinePoolUid = selStartLinePoolId;
                }

                // This would have needed to be keyCache_.getUniqueId before key cache was deprecated
                n += FGWebContainerWrapper.wtireShort(stream, textSelectionComponentUid_.intValue());
                if (textSelectionModel_.isMultiLine())
                {
                    n += FGWebContainerWrapper.wtireShort(stream, selStartLinePoolId);
                    n += FGWebContainerWrapper.wtireShort(stream, caretLineFirst ? textSelectionModel_.getCaretLinePos() : textSelectionModel_.getSelectionMarkLinePos());
                    n += FGWebContainerWrapper.wtireShort(stream, selEndLinePoolUid);
                    n += FGWebContainerWrapper.wtireShort(stream, caretLineFirst ? textSelectionModel_.getSelectionMarkLinePos() : textSelectionModel_.getCaretLinePos());
                    for (int l=firstLine+1; l<lastLine; l++)
                    {
                        String line = selectedTextComponentLines_.get(l);
                        int linePoolId = stringPoolIdSupplier_.getStringPoolId(line, textSelectionComponentUid_);
                        n += FGWebContainerWrapper.wtireShort(stream, linePoolId);
                    }
                }
                else
                {
                    boolean caretFirst = textSelectionModel_.getCaretLinePos() < textSelectionModel_.getSelectionMarkLinePos();
                    n += FGWebContainerWrapper.wtireShort(stream, selStartLinePoolId);
                    n += FGWebContainerWrapper.wtireShort(stream, caretFirst ? textSelectionModel_.getCaretLinePos() : textSelectionModel_.getSelectionMarkLinePos());
                    n += FGWebContainerWrapper.wtireShort(stream, caretFirst ? textSelectionModel_.getSelectionMarkLinePos() : textSelectionModel_.getCaretLinePos());
                }
            }
            return n;
        }

        @Override
        public byte getCommandCode()
        {
            return FGWebContainerWrapper.TEXT_SELECTION_MODEL_COMMAND_CODE;
        }

        @Override
        public Supplier<TextSelectionModel> getEmptyDataSupplier()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Supplier<TextSelectionModel> getSourceDataSupplier()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextSelectionModel getDiffToTransmit(TextSelectionModel previousData, TextSelectionModel newData)
        {
            return newData;
        }

        public void setTextSelectionModel(TextSelectionModel textSelectionModel,
                                          List<String> selectedTextComponentLines,
                                          Integer textSelectionComponentUid)
        {
            textSelectionModel_ = textSelectionModel;
            selectedTextComponentLines_ = selectedTextComponentLines;
            textSelectionComponentUid_ = textSelectionComponentUid;
        }
    }

    public static class AddRemoveComponentsTransmitter extends FGWebContainerWrapper.AbstractTransmitter<Tuple>
    {
        @Override
        public int writeBinary(ByteArrayOutputStream stream, int n, Tuple data)
        {
            Collection<Integer> removedUids = data.getFirst();
            List<Integer> addedParentUids = data.getSecond();
            List<Integer> addedUids = data.getThird();

            n += FGWebContainerWrapper.wtireShort(stream, removedUids.size());
            for (Integer i : removedUids)
            {
                n += FGWebContainerWrapper.wtireShort(stream, i);
            }
            for (int i=0; i<addedUids.size(); i++)
            {
                n += FGWebContainerWrapper.wtireShort(stream, addedParentUids.get(i));
                n += FGWebContainerWrapper.wtireShort(stream, addedUids.get(i));
            }
            return n;
        }

        @Override
        public byte getCommandCode()
        {
            return FGWebContainerWrapper.REMOVE_ADD_COMPONENTS_COMMAND_CODE;
        }

        @Override
        public Supplier<Tuple> getEmptyDataSupplier()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Supplier<Tuple> getSourceDataSupplier()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tuple getDiffToTransmit(Tuple previousData, Tuple newData)
        {
            return newData;
        }
    }

}
