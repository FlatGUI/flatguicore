/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import clojure.lang.Keyword;
import flatgui.core.FGEvolveResultData;
import flatgui.core.engine.Container;
import flatgui.core.websocket.FGWebInteropUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author Denis Lebedev
 */
public class FGRemoteAppContainer extends FGAppContainer<FGWebInteropUtil>
{
    private final ActionListener unsolicitedEvloveCallback_;

    public FGRemoteAppContainer(String sessionId, Map<Object, Object> container, FGRemoteClojureResultCollector resultCollector)
    {
        this(sessionId, container, DFLT_UNIT_SIZE_PX, resultCollector);
    }

    public FGRemoteAppContainer(String sessionId, Map<Object, Object> container, int unitSizePx, FGRemoteClojureResultCollector resultCollector)
    {
        super(sessionId, container, new FGWebInteropUtil(unitSizePx), resultCollector, unitSizePx);
        resultCollector.initialize(this::getPaintAllSequence);
        unsolicitedEvloveCallback_ = null;
    }

    public FGRemoteAppContainer(String sessionId, FGRemoteAppContainer source, FGRemoteClojureResultCollector resultCollector, ActionListener unsolicitedEvloveCallback)
    {
        super(sessionId, null, source.getContainer(), new FGWebInteropUtil((int) source.getInteropUtil().getUnitSizePx()), resultCollector, (int)source.getInteropUtil().getUnitSizePx(),
            new FGMouseEventParser((int) source.getInteropUtil().getUnitSizePx()));
        resultCollector.initialize(this::getPaintAllSequence);
        unsolicitedEvloveCallback_ = unsolicitedEvloveCallback;
    }

    public Collection<ByteBuffer> getDiffsToTransmit()
    {
        return ((FGRemoteClojureResultCollector)getResultCollector()).getDiffsToTransmit();
    }

    public Collection<ByteBuffer> getInitialDataToTransmit()
    {
        return ((FGRemoteClojureResultCollector)getResultCollector()).getInitialDataToTransmit(getContainer());
    }

    //
    // Special methods needed for FGLegacyCoreGlue - will be refactored
    //

    public Future<FGEvolveResultData> evolveRemote(Object evolveReason)
    {
        Future<FGEvolveResultData> future = getEvolverExecutorService().submit(() -> {
            evolveImpl(evolveReason);
            Set<Integer> changedComponentUids = getResultCollector().getChangedComponentsForRemote();
            Set<List> changedComponentPaths = new HashSet<>(changedComponentUids.size());
            for (Integer changedComponentUid : changedComponentUids)
            {
                List changedComponentPath = ((Container.ComponentAccessor)getContainer().getComponent(changedComponentUid)).getComponentPath();
                changedComponentPaths.add(changedComponentPath);
            }
            getResultCollector().clearChangedComponentsForWeb();
            return new FGLegacyGlueEvolveResultData(changedComponentPaths);
        });
        return future;
    }

    public Future<FGEvolveResultData> evolveRemote(List<Keyword> targetCellIdPath, Object evolveReason)
    {
        Future<FGEvolveResultData> future = getEvolverExecutorService().submit(() -> {
            evolveImpl((List)targetCellIdPath, evolveReason);
            Set<Integer> changedComponentUids = getResultCollector().getChangedComponentsForRemote();
            Set<List> changedComponentPaths = new HashSet<>(changedComponentUids.size());
            for (Integer changedComponentUid : changedComponentUids)
            {
                List changedComponentPath = ((Container.ComponentAccessor)getContainer().getComponent(changedComponentUid)).getComponentPath();
                changedComponentPaths.add(changedComponentPath);
            }
            getResultCollector().clearChangedComponentsForWeb();
            return new FGLegacyGlueEvolveResultData(changedComponentPaths);
        });
        return future;
    }

    public <T> Future<T> submitTask(Callable<T> callable)
    {
        return getEvolverExecutorService().submit(callable);
    }

    public int getQueueSizeWaiting()
    {
        return (int) (getEvolverExecutorService().getTaskCount() -
                getEvolverExecutorService().getCompletedTaskCount());
    }

    public List<Object> getPaintAllSequence()
    {
        if (getContainer() == null)
        {
            // TODO this is called by new result collector during container initialization, needs to be refactored
            return null;
        }

        // TODO 1 can know list size in advance
        // TODO 2 no need to do this on each cycle
        List<Object> sequence = new ArrayList<>();
        Container.IContainerAccessor containerAccessor = getContainer().getContainerAccessor();
        getResultCollector().collectPaintAllSequence(
                sequence,
                containerAccessor,
                Integer.valueOf(0));
        //System.out.println("PaintAllSeq: " + sequence);
        return sequence;
    }

    public Map<Object, Map<Keyword, Object>> getComponentIdPathToComponent(Collection<List<Keyword>> paths)
    {
        Map m = new HashMap<>();

        if (paths == null)
        {
            Collection<List<Objects>> allIdPaths = (Collection) getContainer().getAllIdPaths();
            paths = new HashSet<>();
            for (List<Objects> p : allIdPaths)
            {
                List pWithoutProperty  = new ArrayList<>(p.size());
                pWithoutProperty.addAll(p);
                pWithoutProperty.remove(pWithoutProperty.size()-1);
                paths.add(pWithoutProperty);
            }
        }

        for (List path : paths)
        {
            Integer index = getContainer().getComponentUid(path);
            Map component = getContainer().getComponent(index);
            m.put(index, component);
        }

        return m;
    }

    @Override
    public void evolve(List<Object> targetPath, Object evolveReason)
    {
        super.evolve(targetPath, evolveReason);
        // This is called explicitly and not as a result of remote client user activity. So need to trigger
        // transmitting results to the remote explicitly
        if (unsolicitedEvloveCallback_ != null)
        {
            unsolicitedEvloveCallback_.actionPerformed(null);
        }
    }

    // getEvolveReasonToTargetPath was used only for transmitting cursor changes and only to
    // find out if there was mouse event among evolve reasons
    private static class FGLegacyGlueEvolveResultData extends FGEvolveResultData
    {
        public FGLegacyGlueEvolveResultData(Set changedPaths)
        {
            super(null, changedPaths);
        }

        @Override
        public Map<Object, List<Keyword>> getEvolveReasonToTargetPath()
        {
            throw new UnsupportedOperationException();
        }
    }


    //
    //
    //
}
