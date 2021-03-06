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

import clojure.lang.Keyword;
import clojure.lang.Var;
import flatgui.core.engine.remote.FGLegacyCoreGlue;
import flatgui.core.util.IFGChangeListener;
import flatgui.core.websocket.FGPaintVectorBinaryCoder;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Denis Lebedev
 */
public class FGWebContainerWrapper
{
    public static final byte POSITION_MATRIX_MAP_COMMAND_CODE = 0;
    public static final byte VIEWPORT_MATRIX_MAP_COMMAND_CODE = 1;
    public static final byte CLIP_SIZE_MAP_COMMAND_CODE = 2;
    public static final byte LOOK_VECTOR_MAP_COMMAND_CODE = 3;
    public static final byte CHILD_COUNT_MAP_COMMAND_CODE = 4;
    public static final byte BOOLEAN_STATE_FLAGS_COMMAND_CODE = 5;
    public static final byte STRING_POOL_MAP_COMMAND_CODE = 7;
    public static final byte RESOURCE_STRING_POOL_MAP_COMMAND_CODE = 8;
    public static final byte CLIENT_EVOLVER_MAP_COMMAND_CODE = 9;

    public static final byte PAINT_ALL_LIST_COMMAND_CODE = 64;
    public static final byte REPAINT_CACHED_COMMAND_CODE = 65;
    //public static final byte SET_CURSOR_COMMAND_CODE = 66;
    public static final byte PUSH_TEXT_TO_CLIPBOARD = 67;
    public static final byte TEXT_SELECTION_MODEL_COMMAND_CODE = 68;
    public static final byte REMOVE_ADD_COMPONENTS_COMMAND_CODE = 69;

    public static final byte FINISH_PREDICTION_TRANSMISSION = 100;
    public static final byte MOUSE_LEFT_DOWN_PREDICTION = 101;
    public static final byte MOUSE_LEFT_UP_PREDICTION = 102;
    public static final byte MOUSE_LEFT_CLICK_PREDICTION = 103;
    public static final byte MOUSE_MOVE_OR_DRAG_PREDICTION_HEADER = 104;
    public static final byte MOUSE_MOVE_OR_DRAG_PREDICTION = 105;
    public static final byte PING_RESPONSE = 106;
    public static final byte METRICS_REQUEST = 107;

    public static String DEBUG_OP = "OLD";

    public static final byte[] MOUSE_LEFT_CLICK_PREDICTION_SEQUENCE = new byte[]
    {
        MOUSE_LEFT_DOWN_PREDICTION,
        MOUSE_LEFT_UP_PREDICTION,
        MOUSE_LEFT_CLICK_PREDICTION
    };

    private static Set<String> RECT_COMMANDS;
    static
    {
        RECT_COMMANDS = new HashSet<>();
        RECT_COMMANDS.add("drawLine");
        RECT_COMMANDS.add("drawRect");
        RECT_COMMANDS.add("fillRect");
        RECT_COMMANDS.add("drawOval");
        RECT_COMMANDS.add("fillOval");
        RECT_COMMANDS.add("clipRect");
        RECT_COMMANDS.add("setClip");
    }


    private final FGContainerStateTransmitter stateTransmitter_;
    private final Map<Object, FGContainerStateTransmitter> stateTransmitterForks_;

    private final IFGContainer fgContainer_;
    private Function<FGEvolveInputData, Future<FGEvolveResultData>> eventConsumer_;

    private Set<String> fontsWithMetricsAlreadyReceived_;

    public FGWebContainerWrapper(IFGContainer fgContainer, Set<String> fontsWithMetricsAlreadyReceived)
    {
        fgContainer_ = fgContainer;
        eventConsumer_ = fgContainer_.connect(e -> {}, this);

        fontsWithMetricsAlreadyReceived_ = fontsWithMetricsAlreadyReceived;

        stateTransmitter_ = new FGContainerStateTransmitter(fgContainer_, fontsWithMetricsAlreadyReceived_);
        stateTransmitterForks_ = new HashMap<>();
    }

    //
    public IFGContainer getContainer()
    {
        return fgContainer_;
    }
    //

    public synchronized void initialize()
    {
        fgContainer_.initialize();
    }

    public synchronized void unInitialize()
    {
        fgContainer_.unInitialize();
    }

    public synchronized boolean isActive()
    {
        return fgContainer_.isActive();
    }

    /**
     * @return results future or null if this event has aleady been predicted and results sent to the remote
     */
    public synchronized Future<FGEvolveResultData> feedEvent(FGEvolveInputData inputData)
    {
        // TODO get rid of eventConsumer_, call fgContainer_
        Future<FGEvolveResultData> changedPathsFuture = fgContainer_.feedEvent(inputData); //eventConsumer_.apply(inputData);
        obtainForkIfNeeded(inputData);
        return changedPathsFuture;
    }

    public synchronized Future<FGEvolveResultData> feedTargetedEvent(List<Keyword> targetCellIdPath, FGEvolveInputData inputData)
    {
        Future<FGEvolveResultData> changedPathsFuture = fgContainer_.feedTargetedEvent(targetCellIdPath, inputData);
        obtainForkIfNeeded(inputData);
        return changedPathsFuture;
    }

    public Collection<ByteBuffer> getUnsolicitedResponseForClient(Consumer<Collection<ByteBuffer>> responseConsumer)
    {
        return getResponseForClientImpl(stateTransmitter_, null, responseConsumer);
    }

    public synchronized Collection<ByteBuffer> getResponseForClient(Future<FGEvolveResultData> evolveResultsFuture)
    {
        return getResponseForClientImpl(stateTransmitter_, evolveResultsFuture, null);
    }

    public synchronized Collection<ByteBuffer> getForkedResponseForClient(Object evolveReason, Future<FGEvolveResultData> evolveResultsFuture)
    {
        FGContainerStateTransmitter stateTransmitter = stateTransmitterForks_.get(evolveReason);
        if (stateTransmitter == null)
        {
            throw new IllegalStateException("stateTransmitter is null for " + evolveReason.toString());
        }
        return getResponseForClientImpl(stateTransmitter, evolveResultsFuture, null);
    }

    public synchronized void clearForks()
    {
        stateTransmitterForks_.clear();
    }

    public synchronized void resetCache()
    {
        stateTransmitter_.resetDataCache();
    }

    public void addFontStrListener(IFGChangeListener<String> listener)
    {
        stateTransmitter_.addFontStrListener(listener);
    }

    private volatile int debug_ = 0;
    public Collection<ByteBuffer> getResponseForClientImpl(
        FGContainerStateTransmitter stateTransmitter, Future<FGEvolveResultData> evolveResultsFuture,
        Consumer<Collection<ByteBuffer>> unsolicitedResponseConsumer)
    {
        // TODO
        // 1. computeDataDiffsToTransmit needs to be heavily optimized
        // 2. maybe for too frequent mouse move and drag events we can skip

        //if (debug_ == 0 || stateTransmitter.initialCycle_)
        {
            try
            {
//                // TODO eventually get rid of old version of collecting byte buffer data
//                Future<Collection<ByteBuffer>> responseFuture =
//                        fgContainer_.submitTask(() -> stateTransmitter.computeDataDiffsToTransmit(evolveResultsFuture));
//                Collection<ByteBuffer> r = responseFuture.get();

                Future<Collection<ByteBuffer>> responseFuture2 =
                        fgContainer_.submitTask(() -> {
                            if (evolveResultsFuture != null || unsolicitedResponseConsumer != null)
                            {
                                Collection<ByteBuffer> diffs = ((FGLegacyCoreGlue)fgContainer_).getDiffsToTransmit();
                                if (unsolicitedResponseConsumer != null)
                                {
                                    unsolicitedResponseConsumer.accept(diffs);
                                    return null;
                                }
                                return diffs;
                            }
                            else
                            {
                                return ((FGLegacyCoreGlue)fgContainer_).getInitialDataToTransmit();
                            }
                        });
                if (unsolicitedResponseConsumer != null)
                {
                    // Do not wait for Future to finish in this case
                    return Collections.emptyList();
                }

                Collection<ByteBuffer> r2 = responseFuture2.get();

//                if (evolveResultsFuture != null)
//                {
//                    Set<ByteBuffer> rSet = new HashSet<>(r);
//                    Set<ByteBuffer> r2Set = new HashSet<>(r2);
//                    boolean hit = rSet.equals(r2Set);
//                    System.out.println("-DLTEMP- FGWebContainerWrapper.getResponseForClientImpl" + (hit ? " HIT" : " MISS"));
//                    if (!hit)
//                    {
//                        Map<Byte, ByteBuffer> rMap = new HashMap<>();
//                        for (ByteBuffer b : r)
//                        {
//                            rMap.put(b.get(0), b);
//                        }
//                        Map<Byte, ByteBuffer> r2Map = new HashMap<>();
//                        for (ByteBuffer b : r2)
//                        {
//                            r2Map.put(b.get(0), b);
//                        }
//                        for (Byte cmd : rMap.keySet())
//                        {
//                            if (!rMap.get(cmd).equals(r2Map.get(cmd)))
//                            {
//                                System.out.println("                           getResponseForClientImpl missed cmd: " + cmd);
//                            }
//                        }
//                    }
//                }

                debug_ = 4;
                //return r;
                return r2;
            }
            catch (InterruptedException | ExecutionException e)
            {
                e.printStackTrace();
            }
        }
//        else
//        {
//            debug_--;
//        }

        return Collections.emptyList();
    }

    public synchronized boolean markFontAsHavingReceivedMetrics(String font)
    {
        return fontsWithMetricsAlreadyReceived_.add(font);
    }

    public static int wtireShort(ByteArrayOutputStream stream, int num)
    {
        stream.write((byte)(num & 0xFF));
        stream.write((byte)((num >> 8) & 0xFF));
        return 2;
    }

    private void obtainForkIfNeeded(FGEvolveInputData evolveInputData)
    {
        if (evolveInputData.shouldFork())
        {
            Object reason = evolveInputData.getEvolveReason();
            IFGModule forkedModule = fgContainer_.getForkedFGModule(reason);
            stateTransmitterForks_.put(reason, stateTransmitter_.fork(forkedModule));
        }
    }

    private void ensureActive()
    {
        if (!fgContainer_.isActive())
        {
            throw new IllegalStateException("Container is not active");
        }
    }

    private java.util.List<Object> compressPaintVector(java.util.List<Object> commandVector)
    {
        // TODO paint.clj: bother with clipping only when viewport size is different from clip size

        commandVector = commandVector.stream().filter(cmd -> {
            Object command = ((List) cmd).get(0);
//            if (command.equals("pushCurrentClip") || command.equals("popCurrentClip") ||
//                    command.equals("clipRect") || command.equals("setClip")) {
//                return false;
//            }
            if (RECT_COMMANDS.contains(command)) {
                double x = getCoord(((List) cmd), 1);
                double y = getCoord(((List) cmd), 2);
                double w = getCoord(((List) cmd), 3);
                double h = getCoord(((List) cmd), 4);
                if (x == 0 && y == 0 && w == 0 && h == 0) return false;
            }
            return true;
        }).collect(Collectors.toList());

        java.util.List<Object> compressed = new ArrayList<>(commandVector.size());

        int size = commandVector.size();
        for (int i = 0; i < size;)
        {
            List singleCommand = new ArrayList<>((List) commandVector.get(i));
            String command = (String) singleCommand.get(0);

            if (i < size-1)
            {
//                if (getCommand(commandVector, i).equals("popCurrentClip") &&
//                        getCommand(commandVector, i+1).equals("pushCurrentClip"))
//                {
//                    // Skip
//                    i = i+2;
//                }
                if (getCommand(commandVector, i).equals("transform"))
                {
                    double ctx = ((AffineTransform) singleCommand.get(1)).getTranslateX();
                    double cty = ((AffineTransform) singleCommand.get(1)).getTranslateY();
                    boolean coalesce = false;

                    int j=i+1;
                    while (j < size && getCommand(commandVector, j).equals("transform"))
                    {
                        List jCommand = new ArrayList<>((List) commandVector.get(j));
                        ctx += ((AffineTransform) jCommand.get(1)).getTranslateX();
                        cty += ((AffineTransform) jCommand.get(1)).getTranslateY();
                        coalesce = true;
                        j++;
                    }

                    if (coalesce)
                    {
                        compressed.add(Arrays.asList("transform", AffineTransform.getTranslateInstance(ctx, cty)));
                        i=j;
                    }
                    else
                    {
                        compressed.add(singleCommand);
                        i++;
                    }
                }
//                else if (getCommand(commandVector, i).equals("drawString"))
//                {
//                    singleCommand.set(1, "");
//                    i++;
//                }
                else
                {
                    compressed.add(singleCommand);
                    i++;
                }
            }
            else
            {
                compressed.add(singleCommand);
                i++;
            }
        }

        return compressed;
    }

    private static String getCommand(java.util.List<Object> commandVector, int i)
    {
        List singleCommand = new ArrayList<>((List) commandVector.get(i));
        return (String) singleCommand.get(0);
    }

    private static double getCoord(List singleCommand, int i)
    {
        return ((Number) singleCommand.get(i)).doubleValue();
    }

    private java.util.List<Object> compressPaintVector2(java.util.List<Object> commandVector)
    {
        java.util.List<Object> compressed = new ArrayList<>(commandVector.size());

        double ctx = 0;
        double cty = 0;

        for (int i=0; i<commandVector.size(); i++)
        {
            List singleCommand = new ArrayList<>((List)commandVector.get(i));
            String command = (String) singleCommand.get(0);
            if (command.equals("transform"))
            {
                ctx += ((AffineTransform) singleCommand.get(1)).getTranslateX();
                cty += ((AffineTransform) singleCommand.get(1)).getTranslateY();
            }
//            else if (command.equals("pushCurrentClip") || command.equals("popCurrentClip"))
//            {
//                // Skip
//            }
            else
            {
                double ctxScaled = ctx / IFGContainer.UNIT_SIZE_PX;
                double ctyScaled = cty / IFGContainer.UNIT_SIZE_PX;

                switch (command)
                {
                    case "drawString":
                        double x = ((Number)singleCommand.get(2)).doubleValue();
                        double y = ((Number)singleCommand.get(3)).doubleValue();
                        singleCommand.set(2, Double.valueOf(x + ctxScaled));
                        singleCommand.set(3, Double.valueOf(y + ctyScaled));
                        break;
                    case "drawLine":
                        x = ((Number)singleCommand.get(1)).doubleValue();
                        y = ((Number)singleCommand.get(2)).doubleValue();
                        singleCommand.set(1, Double.valueOf(x + ctxScaled));
                        singleCommand.set(2, Double.valueOf(y + ctyScaled));
                        double x2 = ((Number)singleCommand.get(3)).doubleValue();
                        double y2 = ((Number)singleCommand.get(4)).doubleValue();
                        singleCommand.set(3, Double.valueOf(x2 + ctxScaled));
                        singleCommand.set(4, Double.valueOf(y2 + ctyScaled));
                        break;
                    case "drawRect":
                    case "fillRect":
                    case "drawOval":
                    case "fillOval":
                    case "clipRect":
                    case "setClip":
                        x = ((Number)singleCommand.get(1)).doubleValue();
                        y = ((Number)singleCommand.get(2)).doubleValue();
                        singleCommand.set(1, Double.valueOf(x + ctxScaled));
                        singleCommand.set(2, Double.valueOf(y + ctyScaled));
                }

                compressed.add(singleCommand);
            }
        }

        return compressed;
    }

    private static boolean eq(Object a, Object b)
    {
        if (a == null)
        {
            return b == null;
        }
        else
        {
            return a.equals(b);
        }
    }

// May be useful for troubleshooting
//    private static String map2str(Map<Object, Object> map, String keyStr)
//    {
//        StringBuilder sb = new StringBuilder();
//        for (Object k : map.keySet())
//        {
//            if (keyStr == null || k.toString().equals(keyStr))
//            {
//                sb.append(k.toString() + "=" + map.get(k) + " ");
//            }
//        }
//        return sb.toString();
//    }

    public interface IDataTransmitter<S>
    {
        public byte getCommandCode();

        public Supplier<S> getEmptyDataSupplier();

        public Supplier<S> getSourceDataSupplier();

        public S getDiffToTransmit(S previousData, S newData);

        public ByteBuffer convertToBinary(byte commandCode, S data);
    }

    public static abstract class AbstractTransmitter<S> implements IDataTransmitter<S>
    {
        @Override
        public ByteBuffer convertToBinary(byte commandCode, S data)
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.write(commandCode);

            writeBinary(stream, 1, data);

            return ByteBuffer.wrap(stream.toByteArray());

//            // TODO ZIP only if >10 bytes
//            try
//            {
//                ByteArrayOutputStream bo = new ByteArrayOutputStream();
//                DeflaterOutputStream go = new GZIPOutputStream(bo);
//                go.write(stream.toByteArray());
//                go.close();
//                byte[] result = bo.toByteArray();
//                return ByteBuffer.wrap(result);
//            }
//            catch (IOException ex)
//            {
//                ex.printStackTrace();
//                return null;
//            }
        }

        public abstract int writeBinary(ByteArrayOutputStream stream, int n, S data);
    }

    public interface IKeyCache
    {
        int getUniqueId(Object key);
    }

    static abstract class AbstractMapTransmitter<V> extends AbstractTransmitter<Map<Object, Object>>
    {
        private IKeyCache keyCache_;
        private Supplier<Map<Object, Object>> sourceMapSupplier_;

        public AbstractMapTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
            {
            keyCache_ = keyCache;
            sourceMapSupplier_ = sourceMapSupplier;
        }

        @Override
        public int writeBinary(ByteArrayOutputStream stream, int n, Map<Object, Object> data)
        {
            for (Map.Entry<Object, Object> e : data.entrySet())
            {
                Object componentId = e.getKey();

                //System.out.println("     -DLTEMP- AbstractMapTransmitter.writeBinary "+DEBUG_OP+" cmd=" + getCommandCode() + " " + componentId + "->" + e.getValue());

                int uid = keyCache_.getUniqueId(componentId);
                onWritingUid(componentId, uid);
                stream.write((byte)(uid & 0xFF));
                n++;
                stream.write((byte)((uid >> 8) & 0xFF));
                n++;
                int bytesWritten = writeValue(stream, n, (V)e.getValue());
                n += bytesWritten;
            }
            return n;
        }

        @Override
        public Supplier<Map<Object, Object>> getEmptyDataSupplier()
        {
            return HashMap::new;
        }

        @Override
        public Supplier<Map<Object, Object>> getSourceDataSupplier()
        {
            return sourceMapSupplier_;
        }

        protected void onWritingUid(Object componentId, int uid)
        {
        }


//        /**
//         * Computes value to transmit for given key when value is changed. It is the whole
//         * new value by default. Implementations may compute diff between old and new value
//         * since they have specific knowledge about the type of the value.
//         *
//         * @param key key
//         * @param prevValue previous value by given key
//         * @param newValue new value by given key
//         * @return value to transmit
//         */
//        protected Object computeValueToTransmit(Object key, Object prevValue, Object newValue)
//        {
//            // By default - whole new value
//            return newValue;
//        }

        protected abstract int writeValue(ByteArrayOutputStream stream, int n, V value);
    }

    static abstract class MapTransmitter<V> extends AbstractMapTransmitter<V>
    {
        public MapTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        public Map<Object, Object> getDiffToTransmit(Map<Object, Object> previousData, Map<Object, Object> newData)
        {
            Map<Object, Object> diff = newData.entrySet().stream()
                    .filter(e -> !eq(previousData.get(e.getKey()), e.getValue()))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
            return diff.isEmpty() ? null : diff;
        }
    }

    static abstract class MapFullNewTransmitter<V> extends AbstractMapTransmitter<V>
    {
        public MapFullNewTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        public Map<Object, Object> getDiffToTransmit(Map<Object, Object> previousData, Map<Object, Object> newData)
        {
            return newData.isEmpty() ? null : newData;
        }
    }

    static abstract class TransformMatrixMapTransmitter extends MapTransmitter<AffineTransform>
    {
        TransformMatrixMapTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        protected int writeValue(ByteArrayOutputStream stream, int n, AffineTransform matrix)
        {
            int tx = getTranslateX(matrix);
            int ty = getTranslateY(matrix);
            writeDimImpl(stream, tx, ty);
            return 3;
        }

        protected int writeDimImpl(ByteArrayOutputStream stream, int tx, int ty)
        {
            // Viewport matrix requires more for example when huge table is scrolled.
            // Here it uses 3 bytes per each coord
            stream.write((byte)(tx & 0xFF));
            stream.write((byte)((tx >> 8) & 0xFF));
            stream.write((byte)((tx >> 16) & 0xFF));
            stream.write((byte)(ty & 0xFF));
            stream.write((byte)((ty >> 8) & 0xFF));
            stream.write((byte)((ty >> 16) & 0xFF));
            return 6;
        }

        protected abstract int getTranslateX(AffineTransform matrix);

        protected abstract int getTranslateY(AffineTransform matrix);
    }

    public static class PositionMatrixMapTrasmitter extends TransformMatrixMapTransmitter
    {
        public PositionMatrixMapTrasmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        protected int getTranslateX(AffineTransform matrix)
        {
            return (int)matrix.getTranslateX();
        }

        @Override
        protected int getTranslateY(AffineTransform matrix)
        {
            return (int)matrix.getTranslateY();
        }

        @Override
        public byte getCommandCode()
        {
            return POSITION_MATRIX_MAP_COMMAND_CODE;
        }
    }

    public static class ViewportMatrixMapTrasmitter extends TransformMatrixMapTransmitter
    {
        public ViewportMatrixMapTrasmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        protected int getTranslateX(AffineTransform matrix)
        {
            return -(int)matrix.getTranslateX();
        }

        @Override
        protected int getTranslateY(AffineTransform matrix)
        {
            return -(int)matrix.getTranslateY();
        }

        @Override
        public byte getCommandCode()
        {
            return VIEWPORT_MATRIX_MAP_COMMAND_CODE;
        }
    }

    static abstract class ShortMapTrasmitter extends MapTransmitter<Number>
    {
        public ShortMapTrasmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        protected int writeValue(ByteArrayOutputStream stream, int n, Number value)
        {
            short number = value.shortValue();
            stream.write((byte)(number & 0xFF));
            stream.write((byte)((number >> 8) & 0xFF));
            return 2;
        }
    }

    static abstract class ByteMapTrasmitter extends MapTransmitter<Number>
    {
        public ByteMapTrasmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        protected int writeValue(ByteArrayOutputStream stream, int n, Number value)
        {
            byte number = value.byteValue();
            stream.write(number);
            return 1;
        }
    }

    public static class ChildCountMapTransmitter extends ShortMapTrasmitter
    {
        public ChildCountMapTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        public byte getCommandCode()
        {
            return CHILD_COUNT_MAP_COMMAND_CODE;
        }
    }

    public static class BooleanFlagsMapTransmitter extends ByteMapTrasmitter
    {
        public BooleanFlagsMapTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        public byte getCommandCode()
        {
            return BOOLEAN_STATE_FLAGS_COMMAND_CODE;
        }
    }

    public static class ClipRectTransmitter extends MapTransmitter<List<Number>>
    {
        public ClipRectTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        protected int writeValue(ByteArrayOutputStream stream, int n, List<Number> value)
        {
            int w = (int)(value.get(0).doubleValue() * IFGContainer.UNIT_SIZE_PX);
            int h = (int)(value.get(1).doubleValue() * IFGContainer.UNIT_SIZE_PX);
            stream.write((byte)(w & 0xFF));
            stream.write((byte)(h & 0xFF));
            stream.write((byte) ((w >> 8) & 0x0F | (h >> 4) & 0xF0));
            return 3;
        }

        @Override
        public byte getCommandCode()
        {
            return CLIP_SIZE_MAP_COMMAND_CODE;
        }
    }

    public static class LookVectorTransmitter extends MapTransmitter<List<Object>>
    {
        private FGPaintVectorBinaryCoder coder_;

        public LookVectorTransmitter(FGPaintVectorBinaryCoder.StringPoolIdSupplier stringPoolIdSupplier,
                                     IKeyCache keyCache,
                                     Supplier<Map<Object, Object>> sourceMapSupplier,
                                     Set<String> fontsWithMetricsAlreadyReceived)
        {
            super(keyCache, sourceMapSupplier);
            coder_ = new FGPaintVectorBinaryCoder(stringPoolIdSupplier, fontsWithMetricsAlreadyReceived);
        }

        @Override
        protected void onWritingUid(Object componentId, int uid)
        {
            coder_.setCodedComponentUid(componentId, uid);
        }

        @Override
        protected int writeValue(ByteArrayOutputStream stream, int n, List<Object> value)
        {
            byte[] array = new byte[131072];
            int lookVecLen = coder_.writeCoded(array, 0, value);
            stream.write((byte)(lookVecLen & 0xFF));
            stream.write((byte)((lookVecLen >> 8) & 0xFF));
            stream.write(array, 0, lookVecLen);
            return lookVecLen + 2;
        }

        @Override
        public byte getCommandCode()
        {
            return LOOK_VECTOR_MAP_COMMAND_CODE;
        }

        void addFontStrListener(IFGChangeListener<String> listener)
        {
            coder_.addFontStrListener(listener);
        }
    }

    public static abstract class StringTransmitter extends MapTransmitter<String>
    {
        public StringTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        protected int writeValue(ByteArrayOutputStream stream, int n, String value)
        {
            return writeString(stream, n, value);
        }

        public static int writeString(ByteArrayOutputStream stream, int n, String value)
        {
            int strLen = value.length();
            stream.write((byte)(strLen & 0xFF));
            stream.write((byte)((strLen >> 8) & 0xFF));
            byte[] srtBytes = value.getBytes();
            stream.write(srtBytes, 0, srtBytes.length);
            return strLen + 2;
        }
    }

    public static abstract class AbstractStringPoolMapTransmitter extends MapTransmitter<Map<Integer, String>>//MapFullNewTransmitter<Map<Integer, String>>
    {
        public AbstractStringPoolMapTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        protected int writeValue(ByteArrayOutputStream stream, int n, Map<Integer, String> value)
        {
            int strCount = value.size();
            if (strCount > 255)
            {
                throw new UnsupportedOperationException();
            }

            int w=0;
            stream.write((byte) (strCount & 0xFF));
            w++;

            for(Map.Entry<Integer, String> e : value.entrySet())
            {
                stream.write(e.getKey().byteValue());
                w++;
                w += StringTransmitter.writeString(stream, w, e.getValue());
            }

            return w;
        }
    }

    public static class StringPoolMapTransmitter extends AbstractStringPoolMapTransmitter
    {
        public StringPoolMapTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        public byte getCommandCode()
        {
            return STRING_POOL_MAP_COMMAND_CODE;
        }
    }

    public static class ResourceStringPoolMapTransmitter extends AbstractStringPoolMapTransmitter
    {
        public ResourceStringPoolMapTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        public byte getCommandCode()
        {
            return RESOURCE_STRING_POOL_MAP_COMMAND_CODE;
        }
    }

    public static class ClientEvolverTransmitter extends StringTransmitter
    {
        public ClientEvolverTransmitter(IKeyCache keyCache, Supplier<Map<Object, Object>> sourceMapSupplier)
        {
            super(keyCache, sourceMapSupplier);
        }

        @Override
        public byte getCommandCode()
        {
            return CLIENT_EVOLVER_MAP_COMMAND_CODE;
        }
    }

    static abstract class ListTransmitter<V> extends AbstractTransmitter<List<Object>>
    {
        private Supplier<List<Object>> sourceListSupplier_;

        ListTransmitter(Supplier<List<Object>> sourceListSupplier)
        {
            sourceListSupplier_ = sourceListSupplier;
        }

        public List<Object> getDiffToTransmit(List<Object> previousData, List<Object> newData)
        {
            return newData.isEmpty() || newData.equals(previousData) ? null : newData;
        }

        @Override
        public int writeBinary(ByteArrayOutputStream stream, int n, List<Object> data)
        {
            for (Object e : data)
            {
                int bytesWritten = writeValue(stream, n, (V)e);
                n += bytesWritten;
            }
            return n;
        }

        @Override
        public Supplier<List<Object>> getEmptyDataSupplier()
        {
            return ArrayList::new;
        }

        @Override
        public Supplier<List<Object>> getSourceDataSupplier()
        {
            return sourceListSupplier_;
        }

        protected abstract int writeValue(ByteArrayOutputStream stream, int n, V value);
    }

    static abstract class KeyListTransmitter extends ListTransmitter<Object>
    {
        private IKeyCache keyCache_;

        public KeyListTransmitter(IKeyCache keyCache, Supplier<List<Object>> sourceListSupplier)
        {
            super(sourceListSupplier);
            keyCache_ = keyCache;
        }

        @Override
        protected int writeValue(ByteArrayOutputStream stream, int n, Object value)
        {
            int uid = keyCache_.getUniqueId(value);
            stream.write((byte)(uid & 0xFF));
            stream.write((byte)((uid >> 8) & 0xFF));
            return 2;
        }
    }

    public static class PaintAllTransmitter extends KeyListTransmitter
    {
        public PaintAllTransmitter(IKeyCache keyCache, Supplier<List<Object>> sourceListSupplier)
        {
            super(keyCache, sourceListSupplier);
        }

        @Override
        public byte getCommandCode()
        {
            return PAINT_ALL_LIST_COMMAND_CODE;
        }
    }

    // TODO remove
    public static class KeyCache implements IKeyCache
    {
        //private int uid_ = 0;
        //private Map<Object, Integer> cache_;

        public static final KeyCache INSTANCE = new KeyCache();

        private KeyCache()
        {
            //cache_ = new HashMap<>();
        }

        @Override
        public int getUniqueId(Object key)
        {
            if (!(key instanceof Integer))
            {
                throw new IllegalArgumentException("non-integer key");
            }

//            Integer cache = cache_.get(key);
//            if (cache == null)
//            {
//                //cache = Integer.valueOf(uid_);
//                cache = (Integer) key;
//
//                cache_.put(key, cache);
//
//                //System.out.println("-DLTEMP- KeyCahe.getUniqueId CACHE " + key + " -> " + cache.intValue());
//
//                uid_++;
//            }
//            return cache.intValue();
            return (Integer) key;
        }
    }

    static class FGContainerStateTransmitter
    {
        private static final String RESPONSE_FEED_NS = IFGModule.RESPONSE_FEED_NS;

        private static final Var extractPositionMatrix_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-position-matrix");
        private static final Var extractViewportMatrix_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-viewport-matrix");
        private static final Var extractClipSize_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-clip-size");
        private static final Var extractLookVector_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-look-vector");
        private static final Var extractChildCount_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-child-count");
        private static final Var extractBitFlags_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-bit-flags");
        private static final Var extractStringPool_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-string-pool");
        private static final Var extractResourceStringPool_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-resource-string-pool");
        private static final Var extractClientEvolver_ = clojure.lang.RT.var(RESPONSE_FEED_NS, "extract-client-evolver");

//        private static final byte DEFAULT_CURSOR_CODE = 8;
//        private static final Map<String, Integer> CURSOR_NAME_TO_CODE;
//        static
//        {
//            Map<String, Integer> m = new HashMap<>();
//            m.put("alias", Integer.valueOf(0));
//            m.put("all-scroll", Integer.valueOf(1));
//            m.put("auto", Integer.valueOf(2));
//            m.put("cell", Integer.valueOf(3));
//            m.put("context-menu", Integer.valueOf(4));
//            m.put("col-resize", Integer.valueOf(5));
//            m.put("copy", Integer.valueOf(6));
//            m.put("crosshair", Integer.valueOf(7));
//            m.put("default", Integer.valueOf(DEFAULT_CURSOR_CODE));
//            m.put("e-resize", Integer.valueOf(9));
//            m.put("ew-resize", Integer.valueOf(10));
//            m.put("help", Integer.valueOf(11));
//            m.put("move", Integer.valueOf(12));
//            m.put("n-resize", Integer.valueOf(13));
//            m.put("ne-resize", Integer.valueOf(14));
//            m.put("nw-resize", Integer.valueOf(15));
//            m.put("nwse-resize", m.get("nw-resize"));
//            m.put("ns-resize", Integer.valueOf(16));
//            m.put("no-drop", Integer.valueOf(17));
//            m.put("none", Integer.valueOf(18));
//            m.put("not-allowed", Integer.valueOf(19));
//            m.put("pointer", Integer.valueOf(20));
//            m.put("progress", Integer.valueOf(21));
//            m.put("row-resize", Integer.valueOf(22));
//            m.put("s-resize", Integer.valueOf(23));
//            m.put("se-resize", Integer.valueOf(24));
//            m.put("sw-resize", Integer.valueOf(25));
//            m.put("nesw-resize", m.get("sw-resize"));
//            m.put("text", Integer.valueOf(26));
//            m.put("vertical-text", Integer.valueOf(27));
//            m.put("w-resize", Integer.valueOf(28));
//            m.put("wait", Integer.valueOf(29));
//            m.put("zoom-in", Integer.valueOf(30));
//            m.put("zoom-out", Integer.valueOf(31));
//            CURSOR_NAME_TO_CODE = Collections.unmodifiableMap(m);
//        }

        private static final BinaryOperator THROWING_MERGER = (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u));};

        private IKeyCache keyCache_;

        private final IFGContainer fgContainer_;
        private final IFGModule fgModule_;

        // TODO track removed components, otherwise memory leak here
        //
        private Map<Byte, Object> cmdToLastData_;

        private Map<Byte, IDataTransmitter<Object>> cmdToDataTransmitter_;
        private FGPaintVectorBinaryCoder.StringPoolIdSupplier stringPoolIdSupplier_;

        // TODO(new core) 1) replace path with index; 2) repalce Clojure extractors with Java ones
        //
        private Map<Object, Map<Keyword, Object>> idPathToComponent_;

        private final LookVectorTransmitter lookVectorTransmitter_;

        private final Set<String> fontsWithMetricsAlreadyReceived_;

        boolean initialCycle_;

        public FGContainerStateTransmitter(IFGContainer fgContainer, Set<String> fontsWithMetricsAlreadyReceived)
        {
            this(true, fgContainer, fgContainer.getFGModule(), null, new KeyCache(), fontsWithMetricsAlreadyReceived);
        }

        public FGContainerStateTransmitter(boolean initialCycle,
                                           IFGContainer fgContainer,
                                           IFGModule module,
                                           Map<Byte, Object> presetDataCache,
                                           IKeyCache keyCache,
                                           Set<String> fontsWithMetricsAlreadyReceived)
        {
            initialCycle_ = initialCycle;

            fgContainer_ = fgContainer;
            fgModule_ = module;
            keyCache_ = keyCache;
            cmdToDataTransmitter_ = new LinkedHashMap<>();

            stringPoolIdSupplier_ = fgModule_::getStringPoolId;

            fontsWithMetricsAlreadyReceived_ = fontsWithMetricsAlreadyReceived;

            addDataTransmitter(new PositionMatrixMapTrasmitter(keyCache_,
                    () -> idPathToComponent_.entrySet().stream()
                            .map(e -> checkMapEntry(e, "Position matrix"))
                            .collect(Collectors.toMap(e -> e.getKey(), e -> extractPositionMatrix_.invoke(e.getValue())))));

            addDataTransmitter(new ViewportMatrixMapTrasmitter(keyCache_,
                    () -> idPathToComponent_.entrySet().stream()
                        .map(e -> checkMapEntry(e, "Viewport matrix"))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> extractViewportMatrix_.invoke(e.getValue())))));

            addDataTransmitter(new ClipRectTransmitter(keyCache_,
                    () -> idPathToComponent_.entrySet().stream()
                        .map(e -> checkMapEntry(e, "Clip size"))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> extractClipSize_.invoke(e.getValue())))));

            lookVectorTransmitter_ = new LookVectorTransmitter(stringPoolIdSupplier_, keyCache_,
                () -> idPathToComponent_.entrySet().stream()
                    .map(e -> checkMapEntry(e, "Look vector"))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> extractLookVector_.invoke(e.getValue()))),
                    fontsWithMetricsAlreadyReceived);

            addDataTransmitter(lookVectorTransmitter_);

            addDataTransmitter(new ChildCountMapTransmitter(keyCache_,
                    () -> idPathToComponent_.entrySet().stream()
                        .map(e -> checkMapEntry(e, "Child count"))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> extractChildCount_.invoke(e.getValue())))));

            addDataTransmitter(new BooleanFlagsMapTransmitter(keyCache_,
                    () -> idPathToComponent_.entrySet().stream()
                        .map(e -> checkMapEntry(e, "Bit flags"))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> extractBitFlags_.invoke(e.getValue())))));

            addDataTransmitter(new PaintAllTransmitter(keyCache_, fgModule_::getPaintAllSequence2));

            addDataTransmitter(new StringPoolMapTransmitter(keyCache_, createStringPoolSupplier(extractStringPool_)));

            addDataTransmitter(new ResourceStringPoolMapTransmitter(keyCache_, createStringPoolSupplier(extractResourceStringPool_)));

            addDataTransmitter(new ClientEvolverTransmitter(keyCache_, createClientEvolverSupplier()));

            if (presetDataCache == null)
            {
                resetDataCache();
            }
            else
            {
                cmdToLastData_ = presetDataCache;
            }

//            // It is important to initialize component UIDs exactly in the order returned by getPaintAllSequence2.
//            // This order always starts from root and the contains children (and children of children recursively)
//            // subsequently. So when client iterates components in end-to-beginning direction, if finds the topmost
//            // child - the receiver of mouse event
//            fgModule_.getPaintAllSequence2().forEach(keyCache_::getUniqueId);
        }

        public final FGContainerStateTransmitter fork(IFGModule module)
        {
            Map<Byte, Object> cmdToLastData = new HashMap<>();
            for (Byte cmd : cmdToLastData_.keySet())
            {
                Object data = cmdToLastData_.get(cmd);
                if (data instanceof Map)
                {
                    cmdToLastData.put(cmd, new HashMap<>((Map)data));
                }
                else
                {
                    cmdToLastData.put(cmd, data);
                }
            }

            return new FGContainerStateTransmitter(false, fgContainer_, module, cmdToLastData, keyCache_, fontsWithMetricsAlreadyReceived_);
        }

        private long debugStartTime_;
        public Collection<ByteBuffer> computeDataDiffsToTransmit(Future<FGEvolveResultData> evolveResultFuture)
        {
            debugStartTime_ = System.nanoTime();

            FGEvolveResultData evolveResultData = null;

            if (!initialCycle_ && evolveResultFuture != null)
            {
                try
                {
                    evolveResultData = evolveResultFuture.get();
                }
                catch (InterruptedException | ExecutionException e)
                {
                    e.printStackTrace();
                    return Collections.emptyList();
                }
            }

            // TODO refactoring response calc code
            if (true)
            {
                long spentTime = System.nanoTime() - debugStartTime_;
                //System.out.println("spentTime = " + (spentTime/1000));
                return null;
            }

            idPathToComponent_ = fgModule_.getComponentIdPathToComponent(
                evolveResultData == null ? null : evolveResultData.getChangedPaths());

            //System.out.println("-DLTEMP- FGContainerStateTransmitter.computeDataDiffsToTransmit----");
            //System.out.println(idPathToComponent_.keySet());
            //System.out.println();

            initialCycle_ = false;

            Map<Byte, Object> newDatas = cmdToDataTransmitter_.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> {
                        Object sourceData = e.getValue().getSourceDataSupplier().get();
                        return sourceData;
                    },
                    THROWING_MERGER,
                    LinkedHashMap::new));


////            Optional<Map.Entry<Object, Object>> hscrlPmOld = ((Map<Object, Object>)cmdToLastData_.get(POSITION_MATRIX_MAP_COMMAND_CODE)).entrySet().stream()
////                .filter(e -> e.getKey().toString().equals("[:main :config :scroll :h-scrollbar :scroller]")).findFirst();
////
////            Optional<Map.Entry<Object, Object>> hscrlPm = ((Map<Object, Object>)newDatas.get(POSITION_MATRIX_MAP_COMMAND_CODE)).entrySet().stream()
////                .filter(e -> e.getKey().toString().equals("[:main :config :scroll :h-scrollbar :scroller]")).findFirst();

//            List<Tuple> stats = new ArrayList<>(newDatas.size());

            Collection<ByteBuffer> result = newDatas.entrySet().stream()
                    .map(e -> {
                        IDataTransmitter<Object> transmitter = cmdToDataTransmitter_.get(e.getKey());
                        Object prevData = cmdToLastData_.get(e.getKey());
                        Object newData = e.getValue();
                        Object diff = transmitter.getDiffToTransmit(prevData, newData);
                        ByteBuffer bin = diff != null ? transmitter.convertToBinary(e.getKey().byteValue(), diff) : null;

//                        if (bin != null)
//                        {
//                            System.out.println(transmitter.getClass().getSimpleName() + " produced " + bin.capacity());
//                        }

//                        if (bin != null)
//                        {
//                            if (diff instanceof Map)
//                            {
//                                stats.add(Tuple.triple(transmitter.getClass().getSimpleName(), bin.capacity(),
//                                    ((Map)diff).keySet().stream()
//                                        //.filter(k -> "[:main :config :scroll :h-scrollbar :scroller]".equals(k.toString()))
//                                        .collect(Collectors.toMap(
//                                            Function.identity(),
//                                            k -> "("+((Map)prevData).get(k)+"->"+((Map)newData).get(k)+")") )));
//                            }
//                            else
//                            {
//                                stats.add(Tuple.pair(transmitter.getClass().getSimpleName(), bin.capacity()));
//                            }
//                        }
                        return bin;
                    })
                    .filter(b -> b != null)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

//            if (!stats.isEmpty() && fgModule_ instanceof FGForkModule)
//            {
//                System.out.println("-DLTEMP- FGContainerStateTransmitter.computeDataDiffsToTransmit coded:"
//                    //+ hscrlPmOld + "->" + hscrlPm + "\n"
//                    + stats);
//            }

            // Cursor

            if (evolveResultData != null)
            {
                //TODO(new core)

//                Collection<List<Keyword>> targetComponentPaths = evolveResultData.getEvolveReasonToTargetPath().values();
//                Set<Object> reasons = evolveResultData.getEvolveReasonToTargetPath().keySet();
//                if (!reasons.isEmpty() && reasons.stream().anyMatch(r -> r instanceof MouseEvent))
//                {
//                    //TODO(new core) getComponentIdPathToComponent is an expensive operation bu here it is called once again
//                    //
//                    Map<List<Keyword>, Map<Keyword, Object>> targetIdPathToComponent = fgModule_.getComponentIdPathToComponent(targetComponentPaths);
//                    Keyword cursor = HostComponent.resolveCursor(targetIdPathToComponent, fgContainer_);
//                    Integer cursorCode = cursor != null ? CURSOR_NAME_TO_CODE.get(cursor.getName()) : null;
//                    byte cursorCodeByte = cursorCode != null ? cursorCode.byteValue() : DEFAULT_CURSOR_CODE;
//                    Byte lastCursor = (Byte) cmdToLastData_.get(SET_CURSOR_COMMAND_CODE);
//                    if (lastCursor == null || cursorCodeByte != lastCursor.byteValue())
//                    {
//                        result.add(ByteBuffer.wrap(new byte[]{SET_CURSOR_COMMAND_CODE, cursorCodeByte}));
//                        cmdToLastData_.put(SET_CURSOR_COMMAND_CODE, Byte.valueOf(cursorCodeByte));
//                    }
//                }
            }

            // Clipboard

            //TODO(new core)
//            String textForClipboard = HostComponent.getTextForClipboard(fgContainer_);
//            if (textForClipboard != null && !textForClipboard.equals(cmdToLastData_.get(PUSH_TEXT_TO_CLIPBOARD)))
//            {
//                ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                StringTransmitter.writeString(stream, 1, textForClipboard);
//                byte[] textBytes = stream.toByteArray();
//                byte[] cmd = new byte[textBytes.length+1];
//                cmd[0] = PUSH_TEXT_TO_CLIPBOARD;
//                for (int i=0; i<textBytes.length; i++)
//                {
//                    cmd[i+1] = textBytes[i];
//                }
//
//                result.add(ByteBuffer.wrap(cmd));
//
//                newDatas.put(PUSH_TEXT_TO_CLIPBOARD, textForClipboard);
//            }

            mergeNewDatasToLast(newDatas);

            long spentTime = System.nanoTime() - debugStartTime_;
            int size = 0;
            for (ByteBuffer b : result)
            {
                size += b.capacity();
            }
            System.out.println("spentTime = " + (spentTime/1000) + " sending " + size);

            return result;
        }

        public void resetDataCache()
        {
            cmdToLastData_ = cmdToDataTransmitter_.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getEmptyDataSupplier().get()));
        }

        void addFontStrListener(IFGChangeListener<String> listener)
        {
            lookVectorTransmitter_.addFontStrListener(listener);
        }

        private static <K> Map.Entry<K, Map<Keyword, Object>> checkMapEntry(Map.Entry<K, Map<Keyword, Object>> entry, String entityName)
        {
            if (entry.getValue() == null)
            {
                throw new NullPointerException(entityName + " is null for " + entry.getKey());
            }
            return entry;
        }

        private Supplier<Map<Object, Object>> createStringPoolSupplier(Var extractorFn)
        {
            return () -> {
                Map<Object, List<String>> idPathToString = new HashMap<>();
                idPathToComponent_.forEach((k, v) -> idPathToString.put(k, (List<String>) extractorFn.invoke(v)));
                return fgModule_.getStringPoolDiffs(idPathToString);
            };
        }

        private Supplier<Map<Object, Object>> createClientEvolverSupplier()
        {
            return () -> {
                Map<Object, Object> result = new HashMap<>();
                idPathToComponent_.forEach((k, v) -> {
                    Object clientEvolver = extractClientEvolver_.invoke(v);
                    if (clientEvolver != null) result.put(k, clientEvolver);});
                return result;
            };
        }

        private void addDataTransmitter(IDataTransmitter<?> dataTransmitter)
        {
            cmdToDataTransmitter_.put(Byte.valueOf(dataTransmitter.getCommandCode()), (IDataTransmitter<Object>) dataTransmitter);
        }

        // TODO track removed components otherwise we are keeping all the garbage here!
        private void mergeNewDatasToLast(Map<Byte, Object> newDatas)
        {
            for(Byte cmd : newDatas.keySet())
            {
                Object newData = newDatas.get(cmd);
                if (newData instanceof Map)
                {
                    Map<Object, Object> prevData = (Map<Object, Object>) cmdToLastData_.get(cmd);
                    if (prevData == null)
                    {
                        cmdToLastData_.put(cmd, newData);
                    }
                    else
                    {
                        prevData.putAll((Map<?, ?>) newData);
                    }
                }
                else
                {
                    cmdToLastData_.put(cmd, newData);
                }
            }
        }
    }
}
