/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import clojure.lang.IFn;
import clojure.lang.Keyword;
import flatgui.core.IFGEvolveConsumer;
import flatgui.core.util.Tuple;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Denis Lebedev
 */
public class EvolvingNode extends Node implements Function<Map<Object, Object>, Object>, IEvolverWrapper
{
    private Map<Integer, Tuple> dependencyIndices_;

    //private Function<Map<Object, Object>, Object> evolver_;

    private Set<IFGEvolveConsumer> evolveConsumers_;

    public EvolvingNode(Integer componentUid, Object propertyId, int parentComponentUid, Container.SourceNode sourceNode, List<Object> nodePath, int nodeUid, Collection<Container.DependencyInfo> relAndAbsDependencyPaths, Collection<Object> inputDependencies, Object evolverCode, Container.IEvolverAccess evolverAccess)
    {
        super(componentUid, propertyId, parentComponentUid, sourceNode, nodePath, nodeUid, relAndAbsDependencyPaths, inputDependencies, evolverCode);

        allDelegates_ = new HashSet<>();
        evolverAccess_ = evolverAccess;
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
        return this;
    }

    public void setEvolver(Function<Map<Object, Object>, Object> evolver)
    {
        //evolver_ = evolver;
        throw new UnsupportedOperationException();
    }

    void forgetDependency(Integer nodeIndex)
    {
        dependencyIndices_.remove(nodeIndex);
        //((ClojureContainerParser.EvolverWrapper)evolver_).unlinkAllDelegates();
        unlinkAllDelegates();
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

    // Below came from EvolverWrapper

    private final Set<GetPropertyDelegate> allDelegates_;

    //private final IFn evolverFn_;
    //private final List<Object> evolvedComponentPath_;
    //private final int nodeIndex_;
    private final Container.IEvolverAccess evolverAccess_;


    @Override
    public Object apply(Map<Object, Object> component)
    {
        GetPropertyStaticClojureFn.visit(this);
        return ((IFn)getEvolverCode()).invoke(component);
    }

    @Override
    public GetPropertyDelegate getDelegateById(Integer getterId)
    {
        GetPropertyDelegate delegate = evolverAccess_.getDelegateByIdMap().get(getDelegateKey(getterId));
        if (delegate == null)
        {
            delegate = createDelegate();
            evolverAccess_.getDelegateByIdMap().put(getDelegateKey(getterId), delegate);
        }
        return delegate;
    }

    @Override
    public GetPropertyDelegate getDelegateByIdAndPath(Integer getterId, List<Object> path)
    {
        Map<List<Object>, GetPropertyDelegate> pathToDelegate = evolverAccess_.getDelegateByIdAndPathMap().get(getDelegateKey(getterId));
        if (pathToDelegate == null)
        {
            pathToDelegate = new HashMap<>();
            evolverAccess_.getDelegateByIdAndPathMap().put(getDelegateKey(getterId), pathToDelegate);
        }
        GetPropertyDelegate delegate = pathToDelegate.get(path);
        if (delegate == null)
        {
            delegate = createDelegate();
            pathToDelegate.put(path, delegate);
        }
        return delegate;
    }

    @Override
    public GetPropertyDelegate getDelegateByIdAndProperty(Integer getterId, Keyword property)
    {
        Map<Keyword, GetPropertyDelegate> propertyToDelegate = evolverAccess_.getDelegateByIdAndPropertyMap().get(getDelegateKey(getterId));
        if (propertyToDelegate == null)
        {
            propertyToDelegate = new HashMap<>();
            evolverAccess_.getDelegateByIdAndPropertyMap().put(getDelegateKey(getterId), propertyToDelegate);
        }
        GetPropertyDelegate delegate = propertyToDelegate.get(property);
        if (delegate == null)
        {
            delegate = createDelegate();
            propertyToDelegate.put(property, delegate);
        }
        return delegate;
    }

    @Override
    public GetPropertyDelegate getDelegateByIdPathAndProperty(Integer getterId, List<Object> path, Keyword property)
    {
        Map<List<Object>, Map<Keyword, GetPropertyDelegate>> mapByPath = evolverAccess_.getDelegateByIdPathAndPropertyMap().get(getDelegateKey(getterId));
        if (mapByPath == null)
        {
            mapByPath = new HashMap<>();
            evolverAccess_.getDelegateByIdPathAndPropertyMap().put(getDelegateKey(getterId), mapByPath);
        }
        Map<Keyword, GetPropertyDelegate> propertyToDelegate = mapByPath.get(path);
        if (propertyToDelegate == null)
        {
            propertyToDelegate = new HashMap<>();
            mapByPath.put(path, propertyToDelegate);
        }
        GetPropertyDelegate delegate = propertyToDelegate.get(property);
        if (delegate == null)
        {
            delegate = createDelegate();
            propertyToDelegate.put(property, delegate);
        }
        return delegate;
    }

    void unlinkAllDelegates()
    {
        allDelegates_.forEach(d -> d.unlink());
    }

    private GetPropertyDelegate createDelegate()
    {
        GetPropertyDelegate delegate = new GetPropertyDelegate(/*evolvedComponentPath_*/dropLast(getNodePath()), evolverAccess_);
        allDelegates_.add(delegate);
        return delegate;
    }

    private Integer getDelegateKey(Integer getterId)
    {
        return Integer.valueOf((getNodeIndex() << 14) + getterId.intValue());
    }

    private static List<Object> dropLast(List<Object> path)
    {
        List<Object> list = new ArrayList<>(path);
        list.remove(list.size()-1);
        return list;
    }
}
