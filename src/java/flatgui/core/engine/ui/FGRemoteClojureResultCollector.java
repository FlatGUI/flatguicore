/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import clojure.lang.Keyword;
import clojure.lang.Var;
import flatgui.core.FGWebContainerWrapper;
import flatgui.core.IFGModule;
import flatgui.core.engine.Container;
import flatgui.core.engine.remote.FGLegacyCoreGlue;
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

    private final FGWebContainerWrapper.IKeyCache keyCache_;
    private final FGLegacyCoreGlue.GlueModule glueModule_;
    private Supplier<List<Object>> paintAllSequenceSupplier_;

    private List<Object> paintAllList_;
    private boolean paintAllListChanged_;
    private TextSelectionModel textSelectionModel_;
    private List<String> selectedTextComponentLines_;
    private boolean textSelectionModelChanged_;

    private final FGWebContainerWrapper.IDataTransmitter<List<Object>> paintAllTransmitter_;
    private final Map<Integer, List<String>> componentIdToStringPool_;
    private final FGWebContainerWrapper.IDataTransmitter<Map<Object, Object>> lookVecStringPoolMapTransmitter_;
    private final Map<Integer, List<String>> componentIdToResourceStringPool_;
    private final FGWebContainerWrapper.IDataTransmitter<Map<Object, Object>> lookVecResourceStringPoolMapTransmitter_;
    private final Map<Object, IDataTransmitterWrapper> propertyToTransWrapper_;
    private final Set<IDataTransmitterWrapper> allWrappers_;
    private final FGWebContainerWrapper.IDataTransmitter<TextSelectionModel> textSelectionModelTransmitter_;

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

        paintAllTransmitter_ = new FGWebContainerWrapper.PaintAllTransmitter(keyCache_, null);
        componentIdToStringPool_ = new HashMap<>();
        lookVecStringPoolMapTransmitter_ = new FGWebContainerWrapper.StringPoolMapTransmitter(keyCache_, null);
        componentIdToResourceStringPool_ = new HashMap<>();
        lookVecResourceStringPoolMapTransmitter_ = new FGWebContainerWrapper.ResourceStringPoolMapTransmitter(keyCache_, null);
        propertyToTransWrapper_ = new LinkedHashMap<>();

        diffsToTransmit_ = new ArrayList<>(3 + propertyToTransWrapper_.size());

        addTransmitterWrapper(new ChildCountMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new IdentityMapTransmitterWrapper("look-vec", new FGWebContainerWrapper.LookVectorTransmitter(
                glueModule_::getStringPoolId, keyCache_, null, fontsWithMetricsAlreadyReceived)));
        addTransmitterWrapper(new BooleanFlagsMapTransmitterWrapper(keyCache_));
        //addTransmitterWrapper(new ChildCountMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new ClipSizeMapTransmitterWrapper(keyCache_));
//        addTransmitterWrapper(new IdentityMapTransmitterWrapper("look-vec", new FGWebContainerWrapper.LookVectorTransmitter(
//                glueModule_::getStringPoolId, keyCache_, null, fontsWithMetricsAlreadyReceived)));
        addTransmitterWrapper(new ViewportMatrixMapTransmitterWrapper(keyCache_));
        addTransmitterWrapper(new PositionMatrixMapTransmitterWrapper(keyCache_));
        // TODO client evolver, but that has to be redesigned into property change transition function, to be more generic
        allWrappers_ = new LinkedHashSet<>(propertyToTransWrapper_.values());

        textSelectionModelTransmitter_ = new TextSelectionModelTransmitter(glueModule_::getStringPoolId);
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
    public void appendResult(Integer parentComponentUid, List<Object> path, Container.Node node, Object newValue)
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
        super.postProcessAfterEvolveCycle(containerAccessor, containerMutator);

        if (initialized_)
        {
            prepareAccumulatedDataForTrasmitting();
        }
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

    private void collectInitialDataForNode(Container container, Integer nodeIndex)
    {
        Container.Node node = container.getNode(nodeIndex);
        collectResultForTransmitting(node.getComponentUid(), node.getPropertyId(), container.getPropertyValue(nodeIndex));
    }


    void collectResultForTransmitting(Integer componentUid, Object propertyId, Object newValue)
    {
        // Only :children and :z-position changes may affect paint all sequence
        if (propertyId == CHILDREN_KW || propertyId == Z_POSITION_KW)
        {
            List<Object> paintAllList = paintAllSequenceSupplier_.get();
            //if (paintAllList != null)
            {
                if (paintAllList_ == null || !paintAllList_.equals(paintAllList))
                {
                    paintAllListChanged_ = true;
                }
                else
                {
                    paintAllListChanged_ = false;
                }
                paintAllList_ = paintAllList;
            }
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
            if (collectTextSelectionModel(newValue))
            {
                textSelectionModelChanged_ = true;
            }
        }
    }

    private boolean collectTextSelectionModel(Object newValue)
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
                if (textSelectionModel_ == null || !textSelectionModel_.equals(newModel))
                {
                    textSelectionModel_ = newModel;
                    selectedTextComponentLines_ = (List<String>) lines;
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
            boolean multiLine = textSelectionModel_.getCaretLine() != textSelectionModel_.getSelectionMarkLine();
            // Otherwise it's empty selection
            if (multiLine ||
                    textSelectionModel_.getCaretLinePos() != textSelectionModel_.getSelectionMarkLinePos())
            {
                String selStartLine = selectedTextComponentLines_.get(textSelectionModel_.getCaretLine());
                int selStartLinePoolId = stringPoolIdSupplier_.getStringPoolId(selStartLine, textSelectionComponentUid_);
                int selEndLinePoolUid;
                if (multiLine)
                {
                    String selEndLine = selectedTextComponentLines_.get(textSelectionModel_.getSelectionMarkLine());
                    selEndLinePoolUid = stringPoolIdSupplier_.getStringPoolId(selEndLine, textSelectionComponentUid_);
                }
                else
                {
                    selEndLinePoolUid = selStartLinePoolId;
                }

                // This would have needed to be keyCache_.getUniqueId before key cache was deprecated
                n += FGWebContainerWrapper.wtireShort(stream, textSelectionComponentUid_.intValue());
                n += FGWebContainerWrapper.wtireShort(stream, selStartLinePoolId);
                n += FGWebContainerWrapper.wtireShort(stream, textSelectionModel_.getCaretLinePos());
                n += FGWebContainerWrapper.wtireShort(stream, selEndLinePoolUid);
                n += FGWebContainerWrapper.wtireShort(stream, textSelectionModel_.getSelectionMarkLinePos());
                for (int l=textSelectionModel_.getCaretLine(); l<textSelectionModel_.getSelectionMarkLine(); l++)
                {
                    String line = selectedTextComponentLines_.get(l);
                    int linePoolId = stringPoolIdSupplier_.getStringPoolId(line, textSelectionComponentUid_);
                    n += FGWebContainerWrapper.wtireShort(stream, linePoolId);
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

}
