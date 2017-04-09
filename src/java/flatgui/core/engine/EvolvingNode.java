/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import flatgui.core.IFGEvolveConsumer;
import flatgui.core.util.Tuple;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Denis Lebedev
 */
public class EvolvingNode extends Node
{
    private Map<Integer, Tuple> dependencyIndices_;

    private Function<Map<Object, Object>, Object> evolver_;

    private Set<IFGEvolveConsumer> evolveConsumers_;

    public EvolvingNode(Integer componentUid, Object propertyId, Integer parentComponentUid, Container.SourceNode sourceNode, List<Object> nodePath, Integer nodeUid, Collection<Container.DependencyInfo> relAndAbsDependencyPaths, Collection<Object> inputDependencies, Object evolverCode)
    {
        super(componentUid, propertyId, parentComponentUid, sourceNode, nodePath, nodeUid, relAndAbsDependencyPaths, inputDependencies, evolverCode);
    }

    public Collection<Object> getInputDependencies()
    {
        return sourceNode_.getInputDependencies();
    }

    public boolean isHasAmbiguousDependencies()
    {
        return sourceNode_.isHasAmbiguousDependencies();
    }

    private void findNodeIndices(Container.ComponentAccessor c, int pathIndex, Container.DependencyInfo d,
                                 List<Container.ComponentAccessor> components, Predicate<Object> isWildcard,
                                 Consumer<Tuple> dependencyPostprocessor)
    {
        List<Object> absPath = d.getAbsPath();
        int absPathSize = absPath.size();
        if (absPathSize == 1)
        {
            return;
        }
        Object e = absPath.get(pathIndex);
        if (pathIndex < absPathSize-1)
        {
            if (isWildcard.test(e))
            {
                List<Integer> allChildIndices = c.getChildIndices();
                for (Integer childIndex : allChildIndices)
                {
                    findNodeIndices(components.get(childIndex), pathIndex+1, d, components, isWildcard, dependencyPostprocessor);
                }
            }
            else
            {
                Integer childIndex = c.getChildIndex(e);
                if (childIndex != null)
                {
                    findNodeIndices(components.get(childIndex), pathIndex+1, d, components, isWildcard, dependencyPostprocessor);
                }
            }
        }
        else
        {
            Integer propertyIndex = c.getPropertyIndex(e);
            if (propertyIndex != null)
            {
                Tuple dependency = Tuple.triple(propertyIndex, d.getRelPath(), d.isAmbiguous());
                dependencyIndices_.put(propertyIndex, dependency);
                if (dependencyPostprocessor != null)
                {
                    dependencyPostprocessor.accept(dependency);
                }
            }
        }
    }

    public void resolveDependencyIndices(List<Container.ComponentAccessor> components, Predicate<Object> isWildCard)
    {
        dependencyIndices_ = new HashMap<>();

        for (Container.DependencyInfo d : sourceNode_.getRelAndAbsDependencyPaths())
        {
            findNodeIndices(components.get(0), 1, d, components, isWildCard, null);
        }
    }

    public Collection<Tuple> reevaluateAmbiguousDependencies(List<Container.ComponentAccessor> components, Predicate<Object> isWildCard)
    {
        Collection<Tuple> newlyAddedDependencies = new ArrayList<>();
        for (Container.DependencyInfo d : sourceNode_.getRelAndAbsDependencyPaths())
        {
            findNodeIndices(components.get(0), 1, d, components, isWildCard, newlyAddedDependencies::add);
        }
        return newlyAddedDependencies;
    }

    public Collection<Tuple> getDependencyIndices()
    {
        return dependencyIndices_.values();
    }

    public Object getEvolverCode()
    {
        return sourceNode_.getEvolverCode();
    }

    public Function<Map<Object, Object>, Object> getEvolver()
    {
        return evolver_;
    }

    public void setEvolver(Function<Map<Object, Object>, Object> evolver)
    {
        evolver_ = evolver;
    }

    void forgetDependency(Integer nodeIndex)
    {
        dependencyIndices_.remove(nodeIndex);
        ((ClojureContainerParser.EvolverWrapper)evolver_).unlinkAllDelegates();
    }

    void addEvolveConsumer(IFGEvolveConsumer evolveConsumer)
    {
        if (evolveConsumers_ == null)
        {
            evolveConsumers_ = new HashSet<>();
        }
        evolveConsumers_.add(evolveConsumer);
    }

    Collection<IFGEvolveConsumer> getEvolveConsumers()
    {
        return evolveConsumers_;
    }
}
