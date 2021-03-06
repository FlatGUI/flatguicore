/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import clojure.lang.Keyword;
import clojure.lang.PersistentHashMap;
import clojure.lang.Var;
import flatgui.core.awt.FGDefaultPrimitivePainter;
import flatgui.core.engine.Container;
import flatgui.core.engine.IResultCollector;
import flatgui.core.engine.Node;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Denis Lebedev
 */
public class FGClojureResultCollector implements IResultCollector, ClipboardOwner
{
    private static final Keyword VISIBLE_POPUP_KW = Keyword.intern("_visible-popup");
    private static final Keyword TO_CLIPBOARD_KW = Keyword.intern("->clipboard");
    protected static final Keyword CURSOR_KW = Keyword.intern("cursor");


    //private static final String FG_NS = "flatgui.core";
    private static final Var rebuildLook_ = clojure.lang.RT.var("flatgui.paint", "rebuild-look");

    private final int unitSizePx_;

    private final Map<Integer, Set<Integer>> parentToVisiblePopupChildCount_;

    private final Set<Integer> changedComponents_;

    private final List<List<Object>> lookVectors_;

    private volatile boolean cursorHasChanged_;
    private volatile Keyword latestCursor_;

    public FGClojureResultCollector(int unitSizePx)
    {
        changedComponents_ = new HashSet<>();
        parentToVisiblePopupChildCount_ = new HashMap<>();
        lookVectors_ = new ArrayList<>();
        unitSizePx_ = unitSizePx;
    }

    public FGClojureResultCollector(FGClojureResultCollector source)
    {
        changedComponents_ = new HashSet<>();
        parentToVisiblePopupChildCount_ = new HashMap<>(source.parentToVisiblePopupChildCount_);
        lookVectors_ = new ArrayList<>(source.lookVectors_);
        unitSizePx_ = source.unitSizePx_;
    }

    @Override
    public void componentAdded(Integer parentComponentUid, Integer componentUid)
    {
        if (componentUid.intValue() >= lookVectors_.size())
        {
            int add = componentUid.intValue() + 1 - lookVectors_.size();
            for (int i=0; i<add; i++)
            {
                lookVectors_.add(null);
            }
        }
    }

    @Override
    public void componentRemoved(Integer componentUid)
    {
        lookVectors_.set(componentUid.intValue(), null);
        parentToVisiblePopupChildCount_.remove(componentUid);
        changedComponents_.remove(componentUid);
        changedComponentsForRemote_.remove(componentUid);
    }

    @Override
    public void componentInitialized(Container container, Integer componentUid)
    {
    }

    @Override
    public void appendResult(int parentComponentUid, List<Object> path, Node node, Object newValue)
    {
        Integer componentUid = node.getComponentUid();
        Object property = node.getPropertyId();

        changedComponents_.add(componentUid);
        changedComponentsForRemote_.add(componentUid);

        // TODO
        // Evolved property node can already contain boolean flags so that there is no need to compare.
        // But it has to be a UI subclass
        if (property.equals(VISIBLE_POPUP_KW) && parentComponentUid != -1)
        {
            Integer parentUid = Integer.valueOf(parentComponentUid);
            Set<Integer> visiblePopupChildIndices = parentToVisiblePopupChildCount_.get(parentUid);
            if (visiblePopupChildIndices == null)
            {
                visiblePopupChildIndices = new HashSet<>();
                parentToVisiblePopupChildCount_.put(parentUid, visiblePopupChildIndices);
            }
            if (newValue != null && !(newValue instanceof Boolean && !((Boolean) newValue).booleanValue()))
            {
                visiblePopupChildIndices.add(componentUid);
            }
            else
            {
                visiblePopupChildIndices.remove(componentUid);
            }
        }
        else if (newValue != null && property.equals(TO_CLIPBOARD_KW) && !GraphicsEnvironment.isHeadless())
        {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents((Transferable) newValue, this);
        }
        else if (property.equals(CURSOR_KW))
        {
            Keyword newCursor = (Keyword) newValue;
            if (!Objects.equals(latestCursor_, newCursor))
            {
                cursorHasChanged_ = true;
                latestCursor_ = newCursor;
            }
        }
    }

    @Override
    public void postProcessAfterEvolveCycle(Container.IContainerAccessor containerAccessor, Container.IContainerMutator containerMutator)
    {
        // TODO (*1) maybe rework deflookfn to utilize indexed access to properties

        //System.out.println("-DLTEMP- (1)FGClojureResultCollector.postProcessAfterEvolveCycle " + changedComponents_);
        for (Integer changedComponentUid : changedComponents_)
        {
            Container.IComponent componentAccessor = containerAccessor.getComponent(changedComponentUid.intValue());
            FGClojureContainerParser.FGComponentDataCache componentDataCache =
                    (FGClojureContainerParser.FGComponentDataCache) componentAccessor.getCustomData();

            // TODO creating Clojure map should not be needed after (*1)
            Object componentClj = PersistentHashMap.create(componentAccessor);
            //Object lookResult = rebuildLook_.invoke(componentClj);
            //System.out.println("-DLTEMP- (2)FGClojureResultCollector.postProcessAfterEvolveCycle " + changedComponentUid + " " + lookResult);
            List<Object> lookVec = (List<Object>) rebuildLook_.invoke(componentClj);
            List<Object> oldLookVec = changedComponentUid.intValue() < lookVectors_.size()
                    ? lookVectors_.get(changedComponentUid.intValue()) : null;
            if (!Objects.equals(oldLookVec, lookVec))
            {
                containerMutator.setValue(componentDataCache.getLookVecIndex(), lookVec);
                lookVectors_.set(changedComponentUid.intValue(), lookVec);
                lookVectorGenerated(changedComponentUid, lookVec);
            }
        }

        changedComponents_.clear();
        cursorHasChanged_ = false;
    }

    protected void lookVectorGenerated(Integer componentUid, List<Object> lookVec)
    {
    }

    boolean hasVisiblePopupChildren(Integer componentUid)
    {
        Set<Integer> visiblePopupChildCount = parentToVisiblePopupChildCount_.get(componentUid);
        return visiblePopupChildCount != null && !visiblePopupChildCount.isEmpty();
    }

    // Paint in Java

    private static Double mxGet(List<List<Number>> matrix, int r, int c)
    {
        return matrix.get(r).get(c).doubleValue();
    }

    private static Double mxX(List<List<Number>> matrix)
    {
        return mxGet(matrix, 0, 0);
    }

    private static Double mxY(List<List<Number>> matrix)
    {
        return mxGet(matrix, 1, 0);
    }

    private final AffineTransform affineTransform(List<List<Number>> matrix)
    {
        double m00 = matrix.get(0).get(0).doubleValue();
        double m10 = matrix.get(1).get(0).doubleValue();
        double m01 = matrix.get(0).get(1).doubleValue();
        double m11 = matrix.get(1).get(1).doubleValue();
        double m02 = unitSizePx_ * matrix.get(0).get(3).doubleValue();
        double m12 = unitSizePx_ * matrix.get(1).get(3).doubleValue();
        return new AffineTransform(m00, m10, m01, m11, m02, m12);
    }

    private final List<Object> PUSH_CURRENT_CLIP = Collections.unmodifiableList(
            Arrays.asList("pushCurrentClip"));
    private final List<Object> POP_CURRENT_CLIP = Collections.unmodifiableList(
            Arrays.asList("popCurrentClip"));
    private final List<Object> SET_CLIP = Arrays.asList(FGDefaultPrimitivePainter.SET_CLIP, 0, 0, null, null);
    private final List<Object> CLIP_RECT = Arrays.asList(FGDefaultPrimitivePainter.CLIP_RECT, 0, 0, null, null);
    private final List<Object> TRANSFORM = Arrays.asList(FGDefaultPrimitivePainter.TRANSFORM, null);

    private final List<Object> setClip(Double clipW, Double clipH)
    {
        SET_CLIP.set(3, clipW);
        SET_CLIP.set(4, clipH);
        return SET_CLIP;
    }

    private final List<Object> clipRect(Double clipW, Double clipH)
    {
        CLIP_RECT.set(3, clipW);
        CLIP_RECT.set(4, clipH);
        return CLIP_RECT;
    }

    private final List<Object> transform(AffineTransform matrix)
    {
        TRANSFORM.set(1, matrix);
        return TRANSFORM;
    }

    void paintComponentWithChildren(Consumer<List<Object>> primitivePainter, Container.IContainerAccessor containerAccessor, Container.IPropertyValueAccessor propertyValueAccessor, Integer componentUid) throws NoninvertibleTransformException
    {
        List<Object> lookVector = lookVectors_.get(componentUid);
        if (lookVector == null)
        {
            // TODO lookVector seems to be null for newly added table cells, but not always??
            return;
        }

        Container.IComponent componentAccessor = containerAccessor.getComponent(componentUid.intValue());
        FGClojureContainerParser.FGComponentDataCache componentDataCache =
                (FGClojureContainerParser.FGComponentDataCache) componentAccessor.getCustomData();

        Object visible = propertyValueAccessor.getPropertyValue(componentDataCache.getVisibleIndex());
        if (visible == null || visible instanceof Boolean && !(((Boolean) visible).booleanValue()))
        {
            return;
        }

        Object positionMatrixObj = propertyValueAccessor.getPropertyValue(componentDataCache.getPositionMatrixIndex());

        // TODO Cache both AWT format matrix object and and its inverse in the original matrix (in meta)?

        AffineTransform positionMatrix = affineTransform((List<List<Number>>) positionMatrixObj);
        AffineTransform positionMatrixInv = positionMatrix.createInverse();

        Object viewportMatrixObj = propertyValueAccessor.getPropertyValue(componentDataCache.getViewportMatrixIndex());
        AffineTransform viewportMatrix = affineTransform((List<List<Number>>) viewportMatrixObj);
        AffineTransform viewportMatrixInv = viewportMatrix.createInverse();

        List<List<Number>> clipSizeObj = (List<List<Number>>)propertyValueAccessor.getPropertyValue(componentDataCache.getClipSizeIndex());
        Double clipW = mxX(clipSizeObj);
        Double clipH = mxY(clipSizeObj);

        Boolean popup = (Boolean)propertyValueAccessor.getPropertyValue(componentDataCache.getPopupIndex());

        primitivePainter.accept(PUSH_CURRENT_CLIP);
        primitivePainter.accept(transform(positionMatrix));
        primitivePainter.accept(popup.booleanValue() ? setClip(clipW, clipH) : clipRect(clipW, clipH));
        primitivePainter.accept(transform(viewportMatrix));
        primitivePainter.accept(lookVector);
        Integer childrenIndex = componentDataCache.getChildrenIndex();
        if (childrenIndex != null)
        {
            // Note that getChildIndices() returns indices properly ordered by z positions
            for (Integer childIndex : componentAccessor.getChildIndices())
            {
                try
                {
                    paintComponentWithChildren(primitivePainter, containerAccessor, propertyValueAccessor, childIndex);
                }
                catch (Throwable ex)
                {
                    System.out.println("ERROR painting component " + childIndex + " " +
                            containerAccessor.getComponent(childIndex).get(FGClojureContainerParser.ID_KW) + " " +
                            lookVectors_.get(childIndex));
                    throw ex;
                }
            }
        }
        primitivePainter.accept(transform(viewportMatrixInv));
        primitivePainter.accept(transform(positionMatrixInv));
        primitivePainter.accept(POP_CURRENT_CLIP);
    }



    //
    // Special means needed for FGLegacyCoreGlue - will be refactored
    // Maybe split FGClojureResultCollector into two different classes
    //

    private final Set<Integer> changedComponentsForRemote_ = new HashSet<>();

    Set<Integer> getChangedComponentsForRemote()
    {
        return changedComponentsForRemote_;
    }

    void clearChangedComponentsForWeb()
    {
        changedComponentsForRemote_.clear();
    }

    void collectPaintAllSequence(List<Object> sequence,
                                 Container.IContainerAccessor containerAccessor,
                                 Integer componentUid)
    {
        Container.IComponent componentAccessor = containerAccessor.getComponent(componentUid.intValue());
        FGClojureContainerParser.FGComponentDataCache componentDataCache =
                (FGClojureContainerParser.FGComponentDataCache) componentAccessor.getCustomData();

        sequence.add(componentUid);
        Integer childrenIndex = componentDataCache.getChildrenIndex();
        if (childrenIndex != null)
        {
            // Note that getChildIndices() returns indices properly ordered by z positions
            for (Integer childIndex : componentAccessor.getChildIndices())
            {
                collectPaintAllSequence(sequence, containerAccessor, childIndex);
            }
        }
    }

    Keyword getLatestCursor()
    {
        return latestCursor_;
    }

    protected final boolean hasCursorChanged()
    {
        return cursorHasChanged_;
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents)
    {
    }
}
