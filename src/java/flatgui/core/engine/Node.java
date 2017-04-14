/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import flatgui.core.IFGEvolveConsumer;
import flatgui.util.CompactList;
import flatgui.util.ObjectMatrix;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents a property of a component (internal indexed)
 */
public class Node
{
    private static final String NO_EVOLVER_MSG = "Should not be called since this node declares no evolver/dependencies";

    // TODO most places that call getComponentUid will be good with primitive, revisit
    private final int componentUid_;

    private final int parentComponentUid_;

    private final int nodeUid_;

    // TODO It should be enough to have 1 instance of SourceNode per app (not for container) and per cell prototype, not for each cell
    protected final Container.SourceNode sourceNode_;

    // TODO Optimize:
    // remove dependents covered by longer chains. Maybe not remove but just hide since longer chains may be provided
    // by components that may be removed
    private final Map<Integer, List<Object>> dependentIndexToRelPath_;

    // TODO closed sessions do not seem to be removed from memory

    public Node(
            Integer componentUid,
            int parentComponentUid,
            Container.SourceNode sourceNode,
            int nodeUid)
    {
        componentUid_ = componentUid;
        parentComponentUid_ = parentComponentUid;
        sourceNode_ = sourceNode;
        nodeUid_ = nodeUid;
        dependentIndexToRelPath_ = new HashMap<>();
    }

    public Integer getComponentUid()
    {
        return componentUid_;
    }

    public Object getPropertyId()
    {
        return sourceNode_.getPropertyId();
    }

    public int getParentComponentUid()
    {
        return parentComponentUid_;
    }

    public boolean isChildrenProperty()
    {
        return sourceNode_.isChildrenProperty();
    }

    public boolean isChildOrderProperty()
    {
        return sourceNode_.isChildOrderProperty();
    }

    public int getNodeIndex()
    {
        return nodeUid_;
    }

    public List<Object> getNodePath()
    {
        return sourceNode_.getNodePath();
    }


    public Map<Integer, List<Object>> getDependentIndices()
    {
        return dependentIndexToRelPath_;
    }

    public void addDependent(Integer nodeIndex, List<Object> nodeAbsPath, List<Object> relPath, ObjectMatrix<Object> keyMatrix)
    {
        List<Object> actualRef = new CompactList<>(keyMatrix, relPath);
        int nodePathSize = sourceNode_.getNodePath().size();
        if (nodeAbsPath.size() < nodePathSize)
        {
            // Replace wildcards (:*) with actual child ids. Start from 1 not to replace *this
            for (int i=1; i<relPath.size(); i++)
            {
                actualRef.set(i, sourceNode_.getNodePath().get(nodePathSize - relPath.size() + i));
            }
        }

        if (Container.debug_) Container.logDebug(nodeUid_ + " " + sourceNode_.getNodePath() + " added dependent: " + nodeIndex + " " + nodeAbsPath + " referenced as " + relPath + " actual ref " + actualRef);
        dependentIndexToRelPath_.put(nodeIndex, actualRef);
    }

    public void removeDependent(Integer nodeIndex)
    {
        dependentIndexToRelPath_.remove(nodeIndex);
    }

    public Collection<Object> getInputDependencies()
    {
        return Collections.emptyList();
    }

    public boolean isHasAmbiguousDependencies()
    {
        return false;
    }

    public void resolveDependencyIndices(List<Container.ComponentAccessor> components, Predicate<Object> isWildCard)
    {
        // No dependencies here, so do nothing
    }

    public Collection<Dependency> reevaluateAmbiguousDependencies(List<Container.ComponentAccessor> components, Predicate<Object> isWildCard)
    {
        throw new IllegalStateException(NO_EVOLVER_MSG);
    }

    public Collection<Dependency> getDependencyIndices()
    {
        return Collections.emptyList();
    }

    public Object getEvolverCode()
    {
        return null;
    }

    public Function<Map<Object, Object>, Object> getEvolver()
    {
        return null;
    }

    void forgetDependency(Integer nodeIndex)
    {
        throw new IllegalStateException(NO_EVOLVER_MSG);
    }

    void addEvolveConsumer(IFGEvolveConsumer evolveConsumer)
    {
        throw new IllegalStateException(NO_EVOLVER_MSG);
    }

    Collection<IFGEvolveConsumer> getEvolveConsumers()
    {
        return Collections.emptyList();
    }

    public static class Dependency
    {
        private int nodeIndex_;
        private List<Object> relPath_;

        public Dependency(int nodeIndex, List<Object> relPath)
        {
            nodeIndex_ = nodeIndex;
            relPath_ = relPath;
        }

        public int getNodeIndex()
        {
            return nodeIndex_;
        }

        public List<Object> getRelPath()
        {
            return relPath_;
        }
    }
}
