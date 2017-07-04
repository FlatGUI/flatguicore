/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author Denis Lebedev
 */
public class AppContainer<ContainerParser extends Container.IContainerParser, ResultCollector extends IResultCollector>
{
    private final String containerId_;

    // TODO These two are mutually exclusive, so need refactoring
    private final Map<Object, Object> containerMap_;
    private final Container containerSource_;

    private ContainerParser containerParser_;
    private ResultCollector resultCollector_;
    private Container container_;

    private final InputEventParser reasonParser_;
    private ThreadPoolExecutor evolverExecutorService_;
    private ThreadPoolExecutor notifierExecutorService_;

    private boolean active_ = false;

    public AppContainer(String containerId, ContainerParser containerParser, ResultCollector resultCollector, Map<Object, Object> container, Container containerSource)
    {
        containerId_ = containerId;
        containerParser_ = containerParser;
        resultCollector_ = resultCollector;

        containerMap_ = container;
        containerSource_ = containerSource;

        reasonParser_ = new InputEventParser();
    }

    public final String getContainerId()
    {
        return containerId_;
    }

    public void initialize()
    {
        evolverExecutorService_ = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new FGExecutorThreadFactory("FlatGUI Evolver ", containerId_));
        notifierExecutorService_ = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new FGExecutorThreadFactory("FlatGUI Ev.Notifier ", containerId_));
        Future<Container> containerFuture =
                evolverExecutorService_.submit(() -> {
                    if (containerSource_ != null)
                    {
                        return new Container(containerSource_, containerId_, containerParser_, resultCollector_, this::submitNotifierTask);
                    }
                    else
                    {
                        return new Container(containerId_, containerParser_, resultCollector_, containerMap_, this::submitNotifierTask);
                    }
                });
        try
        {
            container_ = containerFuture.get();
        }
        catch (Throwable ex)
        {
            ex.printStackTrace();
        }
        active_ = true;
    }

    public void unInitialize()
    {
        active_ = false;
        evolverExecutorService_.shutdown();
    }

    public boolean isActive()
    {
        return active_;
    }

    public final Container.IContainerAccessor getContainerAccessor()
    {
        return container_.getContainerAccessor();
    }

    public final Integer getComponentUid(List<Object> componentPath)
    {
        return container_.getComponentUid(componentPath);
    }

    public void evolve(List<Object> targetPath, Object evolveReason)
    {
        evolverExecutorService_.submit(() -> container_.evolve(targetPath, evolveReason));
    }

    public void evolve(Integer componentUid, Object evolveReason)
    {
        evolverExecutorService_.submit(() -> container_.evolve(componentUid, evolveReason));
    }

    public Future<?> evolve(Object evolveReason)
    {
        return evolverExecutorService_.submit(() -> evolveImpl(evolveReason));
    }

    public Object getProperty(List<Object> path, Object property) throws ExecutionException, InterruptedException
    {
        return getContainer().getPropertyValue(path, property);
    }

    protected void evolveImpl(Object evolveReason)
    {
        Map<Object, Integer> eventsToTargetIndex;
        try
        {
            eventsToTargetIndex = reasonParser_.parseInputEvent(getContainer(), evolveReason);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            eventsToTargetIndex = Collections.emptyMap();
        }

        try
        {
            for (Object event : eventsToTargetIndex.keySet())
            {
                container_.evolve(eventsToTargetIndex.get(event), event);
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    protected void evolveImpl(List<Object> targetPath, Object evolveReason)
    {
        container_.evolve(targetPath, evolveReason);
    }

    protected final ThreadPoolExecutor getEvolverExecutorService()
    {
        return evolverExecutorService_;
    }

    protected final InputEventParser getInputEventParser()
    {
        return reasonParser_;
    }

    protected final Container getContainer()
    {
        return container_;
    }

    protected final ContainerParser getContainerParser()
    {
        return containerParser_;
    }

    protected final ResultCollector getResultCollector()
    {
        return resultCollector_;
    }

    private void submitNotifierTask(Runnable r)
    {
        try
        {
            notifierExecutorService_.submit(r).get();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
