/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import flatgui.core.IFGEvolveConsumer;
import flatgui.core.util.Tuple;

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

    // Source
//        private final boolean childrenProperty_;
//        private final boolean childOrderProperty_;
//
//        private final List<Object> nodePath_;
//        private final Object propertyId_;
//        private final Collection<DependencyInfo> relAndAbsDependencyPaths_;
//        private final boolean hasAmbiguousDependencies_;
//        private final Collection<Object> inputDependencies_;
//        private Object evolverCode_;
    //
    protected final Container.SourceNode sourceNode_;
    //
    // TODO It should be enough to have 1 instance of SourceNode per app (not for container) and per cell prototype, not for each cell
    // TODO Looks like 3rd (isAmbiguous element is not needed in this Tuple)

    // TODO Optimize:
    // remove dependents covered by longer chains. Maybe not remove but just hide since longer chains may be provided
    // by components that may be removed
    private final Map<Integer, List<Object>> dependentIndexToRelPath_;

    // TODO 8 joindesks resulted in 66 threads - why so many?
    // TODO closed sessions do not seem to be removed from memory
    // TODO 1 extra initialization of app happens when system starts?

    public Node(
            Integer componentUid,
            Object propertyId,
            int parentComponentUid,

            //Container container,
            Container.SourceNode sourceNode,

            //boolean childrenProperty,
            //boolean childOrderProperty,

            List<Object> nodePath,
            int nodeUid,
            Collection<Container.DependencyInfo> relAndAbsDependencyPaths,
            Collection<Object> inputDependencies,
            Object evolverCode)
    {
        componentUid_ = componentUid;

        //propertyId_ = propertyId;

        parentComponentUid_ = parentComponentUid;

        sourceNode_ = sourceNode;
        //childrenProperty_ = childrenProperty;
        //childOrderProperty_ = childOrderProperty;
        //container_ = container;

        //nodePath_ = nodePath;

        nodeUid_ = nodeUid;

        //relAndAbsDependencyPaths_ = relAndAbsDependencyPaths;

//            boolean hasAmbiguousDependencies = false;
//            for (DependencyInfo dependencyInfo : relAndAbsDependencyPaths_)
//            {
//                if (dependencyInfo.isAmbiguous())
//                {
//                    hasAmbiguousDependencies = true;
//                    break;
//                }
//            }
//            hasAmbiguousDependencies_ = hasAmbiguousDependencies;
//            inputDependencies_ = inputDependencies;
        dependentIndexToRelPath_ = new HashMap<>();
//            evolverCode_ = evolverCode;
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
        //return childrenProperty_;
        return sourceNode_.isChildrenProperty();
    }

    public boolean isChildOrderProperty()
    {
        //return childOrderProperty_;
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

    public void addDependent(Integer nodeIndex, List<Object> nodeAbsPath, List<Object> relPath)
    {
        List<Object> actualRef = new ArrayList<>(relPath);
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

    public Collection<Tuple> reevaluateAmbiguousDependencies(List<Container.ComponentAccessor> components, Predicate<Object> isWildCard)
    {
        throw new IllegalStateException(NO_EVOLVER_MSG);
    }

    public Collection<Tuple> getDependencyIndices()
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

    public void setEvolver(Function<Map<Object, Object>, Object> evolver)
    {
        throw new IllegalStateException(NO_EVOLVER_MSG);
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
}
