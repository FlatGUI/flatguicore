/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import java.util.List;

/**
 * @author Denis Lebedev
 */
public interface IResultCollector
{
    void componentAdded(Integer parentComponentUid, Integer componentUid);

    void componentRemoved(Integer componentUid);

    void componentInitialized(Container container, Integer componentUid);

    void appendResult(int parentComponentUid, List<Object> path, Node node, Object newValue);

    void postProcessAfterEvolveCycle(Container.IContainerAccessor containerAccessor, Container.IContainerMutator containerMutator);
}
