/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import clojure.lang.IFn;
import clojure.lang.Keyword;
import flatgui.core.IFGEvolveConsumer;
import flatgui.util.CompactList;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Denis Lebedev
 */
public class EvolvingNode extends Node implements Function<Map<Object, Object>, Object>, IEvolverWrapper
{
    private List<Dependency> dependencyIndices_;
    private Set<IFGEvolveConsumer> evolveConsumers_;

    private final Set<GetPropertyDelegate> allDelegates_;
    private final Container.IEvolverAccess evolverAccess_;

    public EvolvingNode(Integer componentUid, int parentComponentUid, Container.SourceNode sourceNode, int nodeUid, Container.IEvolverAccess evolverAccess)
    {
        super(componentUid, parentComponentUid, sourceNode, nodeUid);

        allDelegates_ = new HashSet<>();
        evolverAccess_ = evolverAccess;
    }

    @Override
    public Collection<Object> getInputDependencies()
    {
        return sourceNode_.getInputDependencies();
    }

    @Override
    public boolean isHasAmbiguousDependencies()
    {
        return sourceNode_.isHasAmbiguousDependencies();
    }

    private void findNodeIndices(Container.ComponentAccessor c, int pathIndex, Container.DependencyInfo d,
                                 List<Container.ComponentAccessor> components, Predicate<Object> isWildcard,
                                 Consumer<Dependency> dependencyPostprocessor)
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
                Dependency dependency = new Dependency(propertyIndex, d.getRelPath());
                dependencyIndices_.add(dependency);
                if (dependencyPostprocessor != null)
                {
                    dependencyPostprocessor.accept(dependency);
                }
            }
        }
    }

    @Override
    public void resolveDependencyIndices(List<Container.ComponentAccessor> components, Predicate<Object> isWildCard)
    {
        Collection<Container.DependencyInfo> relAndAbsDependencyPaths = sourceNode_.getRelAndAbsDependencyPaths();
        dependencyIndices_ = new ArrayList<>(relAndAbsDependencyPaths.size());
        for (Container.DependencyInfo d : relAndAbsDependencyPaths)
        {
            findNodeIndices(components.get(0), 1, d, components, isWildCard, null);
        }
    }

    @Override
    public Collection<Dependency> reevaluateAmbiguousDependencies(List<Container.ComponentAccessor> components, Predicate<Object> isWildCard)
    {
        Collection<Dependency> newlyAddedDependencies = new ArrayList<>();
        for (Container.DependencyInfo d : sourceNode_.getRelAndAbsDependencyPaths())
        {
            findNodeIndices(components.get(0), 1, d, components, isWildCard, newlyAddedDependencies::add);
        }
        return newlyAddedDependencies;
    }

    @Override
    public Collection<Dependency> getDependencyIndices()
    {
        return dependencyIndices_;
    }

    @Override
    public Object getEvolverCode()
    {
        return sourceNode_.getEvolverCode();
    }

    @Override
    public Function<Map<Object, Object>, Object> getEvolver()
    {
        return this;
    }

    @Override
    void forgetDependency(Integer nodeIndex)
    {
        for (int i=0; i<dependencyIndices_.size(); i++)
        {
            if (dependencyIndices_.get(i).getNodeIndex() == nodeIndex)
            {
                dependencyIndices_.remove(i);
                break;
            }
        }

        unlinkAllDelegates();
    }

    @Override
    void addEvolveConsumer(IFGEvolveConsumer evolveConsumer)
    {
        if (evolveConsumers_ == null)
        {
            evolveConsumers_ = new HashSet<>();
        }
        evolveConsumers_.add(evolveConsumer);
    }

    @Override
    Collection<IFGEvolveConsumer> getEvolveConsumers()
    {
        return evolveConsumers_;
    }

    // Evolver fn functionality

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
        GetPropertyDelegate delegate = new GetPropertyDelegate(dropLast(getNodePath()), evolverAccess_);
        allDelegates_.add(delegate);
        return delegate;
    }

    private Integer getDelegateKey(Integer getterId)
    {
        return Integer.valueOf((getNodeIndex() << 14) + getterId.intValue());
    }

    private List<Object> dropLast(List<Object> path)
    {
        List<Object> list  = new CompactList<>(evolverAccess_.getKeyMatrix(), path);
        list.remove(list.size()-1);
        return list;
    }
}
