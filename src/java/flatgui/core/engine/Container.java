/*
; Copyright (c) 2016 Denys Lebediev and contributors. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.
 */
package flatgui.core.engine;

import clojure.lang.Keyword;
import clojure.lang.PersistentHashMap;
import clojure.lang.PersistentVector;
import flatgui.core.IFGEvolveConsumer;
import flatgui.core.util.Tuple;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Denis Lebedev
 */
public class Container
{
    private final IResultCollector resultCollector_;

    private final Set<Integer> vacantComponentIndices_;
    private final Set<Integer> vacantNodeIndices_;

    private final List<ComponentAccessor> components_;
    private final Map<List<Object>, Integer> componentPathToIndex_;

    private final List<Node> nodes_;
    private final List<Node> nodesWithAmbiguousDependencies_;
    private final List<Object> values_;
    private final Map<List<Object>, Integer> pathToIndex_;

    private final IContainerAccessor containerAccessor_;
    private final IPropertyValueAccessor propertyValueAccessor_;
    private final IContainerMutator containerMutator_;
    private final IEvolverAccess evolverAccess_;

    private final IContainerParser containerParser_;

    private Node[] reusableNodeBuffer_;
    private Object[] reusableReasonBuffer_;
    private int indexBufferSize_;
    private int currentCycleBufIndex_;
    private Map<Integer, Map<Object, Object>> nodeIndexToComponentCopyForConsumers_;

    private Set<Integer> initializedNodes_;

    // Evolver access TODO should not refer Clojure or ClojureContainerParser directly
    private final HashMap<Integer, GetPropertyDelegate> delegateByIdMap_;
    private final HashMap<Integer, Map<List<Object>, GetPropertyDelegate>> delegateByIdAndPathMap_;
    private final HashMap<Integer, Map<Keyword, GetPropertyDelegate>> delegateByIdAndPropertyMap_;
    private final HashMap<Integer, Map<List<Object>, Map<Keyword, GetPropertyDelegate>>> delegateByIdPathAndPropertyMap_;

    public static boolean debug_ = false;

    public Container(IContainerParser containerParser, IResultCollector resultCollector, Map<Object, Object> container)
    {
        containerParser_ = containerParser;
        resultCollector_ = resultCollector;
        components_ = new ArrayList<>();
        componentPathToIndex_ = new HashMap<>();
        vacantComponentIndices_ = new HashSet<>();
        vacantNodeIndices_ = new HashSet<>();
        nodes_ = new ArrayList<>();
        nodesWithAmbiguousDependencies_ = new ArrayList<>();
        values_ = new ArrayList<>();
        pathToIndex_ = new HashMap<>();
        nodeIndexToComponentCopyForConsumers_ = new LinkedHashMap<>();

        delegateByIdMap_ = new HashMap<>();
        delegateByIdAndPathMap_ = new HashMap<>();
        delegateByIdAndPropertyMap_ = new HashMap<>();
        delegateByIdPathAndPropertyMap_ = new HashMap<>();

        reusableNodeBuffer_ = new Node[1048576];
        reusableReasonBuffer_ = new Object[1048576];
        indexBufferSize_ = 0;

        containerAccessor_ = components_::get;
        propertyValueAccessor_ = this::getPropertyValue;
        evolverAccess_ = new IEvolverAccess()
        {
            @Override
            public Integer indexOfPath(List<Object> path)
            {
                return Container.this.indexOfPath(path);
            }

            @Override
            public Object getPropertyValue(Integer index)
            {
                return Container.this.getPropertyValue(index);
            }

            @Override
            public Map<Integer, GetPropertyDelegate> getDelegateByIdMap()
            {
                return Container.this.delegateByIdMap_;
            }

            @Override
            public Map<Integer, Map<List<Object>, GetPropertyDelegate>> getDelegateByIdAndPathMap()
            {
                return Container.this.delegateByIdAndPathMap_;
            }

            @Override
            public Map<Integer, Map<Keyword, GetPropertyDelegate>> getDelegateByIdAndPropertyMap()
            {
                return Container.this.delegateByIdAndPropertyMap_;
            }

            @Override
            public Map<Integer, Map<List<Object>, Map<Keyword, GetPropertyDelegate>>> getDelegateByIdPathAndPropertyMap()
            {
                return Container.this.delegateByIdPathAndPropertyMap_;
            }
        };
        containerMutator_ = (nodeIndex, newValue) -> values_.set(nodeIndex, newValue);

        addContainer(null, new ArrayList<>(), container, null);
        finishContainerIndexing();

        initializeContainer();
    }

    public Integer addComponent(Integer parentComponentUid, List<Object> componentPath, ComponentAccessor component)
    {
        return addComponentImpl(parentComponentUid, componentPath, component);
    }

    public Integer indexOfPath(List<Object> path)
    {
        return pathToIndex_.get(path);
    }

    public Integer indexOfPathStrict(List<Object> path)
    {
        Integer i = pathToIndex_.get(path);
//        if (i == null)
//        {
//            throw new IllegalArgumentException("No index for path: " + path);
//        }
        return i;
    }

    public void evolve(List<Object> targetPath, Object evolveReason)
    {
        Integer componentUid = getComponentUid(targetPath);
        if (componentUid == null)
        {
            throw new IllegalArgumentException("Component path does not exist: " + targetPath);
        }
        evolve(componentUid, evolveReason);
    }

    public void evolve(Integer componentUid, Object evolveReason)
    {
        long evolveStartTime = System.currentTimeMillis();
        indexBufferSize_ = 0;

        log("----------------Started evolve cycle ---- for reason: " + valueToString(evolveReason));

        ComponentAccessor initialComponentAccessor = components_.get(componentUid);
        Map<Object, Integer> propertyIdToNodeIndex = initialComponentAccessor.getPropertyIdToIndex();
        for (Object propertyId : propertyIdToNodeIndex.keySet())
        {
            Integer nodeIndex = propertyIdToNodeIndex.get(propertyId);
            Node node = nodes_.get(nodeIndex);
            if (evolveReason == null ||
                    (node.getEvolver() != null && containerParser_.isInterestedIn(node.getInputDependencies(), evolveReason.getClass())))
            {
                addNodeToReusableBuffer(node, evolveReason);
            }
        }

        for (int i=0; i<indexBufferSize_; i++)
        {
            log(" Initial component: " + reusableNodeBuffer_[i].getNodePath());
        }

        Set<Integer> addedComponentIds = new HashSet<>();
        currentCycleBufIndex_ = 0;
        nodeIndexToComponentCopyForConsumers_.clear();
        while (currentCycleBufIndex_ < indexBufferSize_)
        {
            Node node = reusableNodeBuffer_[currentCycleBufIndex_];
            if (node == null)
            {
                // Component (with all its nodes) has been removed during this cycle, and its nodes have been
                // removed from reusable buffer to prevent inconsistent calculations
                currentCycleBufIndex_++;
                continue;
            }
            Object triggeringReason = reusableReasonBuffer_[currentCycleBufIndex_];
            int nodeIndex = node.getNodeIndex().intValue();
            Function<Map<Object, Object>, Object> evolver = node.getEvolver();

            if (triggeringReason != null || initializedNodes_ != null && !initializedNodes_.contains(nodeIndex))
            {
                Object oldValue;
                Object newValue;
                ComponentAccessor component = components_.get(node.getComponentUid());
                if (triggeringReason == null)
                {
                    if (evolver == null)
                    {
                        oldValue = null;
                        newValue = values_.get(nodeIndex);
                    }
                    else
                    {
                        component.setEvolveReason(null);
                        oldValue = values_.get(nodeIndex);
                        try
                        {
                            newValue = evolver.apply(component);
                        }
                        catch (Exception ex)
                        {
                            logError(" Error evolving " + node.getNodePath() + " " + node.getPropertyId() + " while initializing ");
                            ex.printStackTrace();
                            throw ex;
                        }
                    }
                }
                else
                {
                    component.setEvolveReason(triggeringReason);
                    oldValue = values_.get(nodeIndex);
                    try
                    {
                        newValue = evolver.apply(component);
                    }
                    catch (Exception ex)
                    {
                        logError(" Error evolving " + node.getNodePath() + " " + node.getPropertyId() + " for reason: " + triggeringReason);
                        ex.printStackTrace();
                        throw ex;
                    }
                }

                boolean changeDetected = initializedNodes_ != null && !initializedNodes_.contains(nodeIndex);
                Set<Object> removedChildIds = null;
                Set<Object> changedChildIds = null;
                Set<Object> addedChildIds = null;
                if (node.isChildrenProperty())
                {
                    removedChildIds = new HashSet<>();
                    changedChildIds = new HashSet<>();
                    addedChildIds = new HashSet<>();

                    Map<Object, Map<Object, Object>> newValueMap = (Map<Object, Map<Object, Object>>)newValue;
                    if (newValueMap == null)
                    {
                        newValueMap = Collections.emptyMap();
                    }
                    newValue = new HashMap<>();
                    for (Object cid : newValueMap.keySet())
                    {
                        // TODO(f) backward compatibility. Non-children maps are there by keys:
                        // :_flexible-childset-added
                        // :_flex-target-id-paths-added
                        if (!cid.toString().contains("_flex"))
                        {
                            ((Map<Object, Map<Object, Object>>) newValue).put(cid, newValueMap.get(cid));
                        }
                    }
                    newValue = PersistentHashMap.create((Map)newValue);

                    Map<Object, Map<Object, Object>> children = (Map<Object, Map<Object, Object>>) oldValue;
                    Map<Object, Map<Object, Object>> oldChildren = children != null ? children : Collections.emptyMap();
                    Set<Object> allIds = new HashSet<>();
                    allIds.addAll(oldChildren.keySet());
                    allIds.addAll(((Map)newValue).keySet());
                    for (Object id : allIds)
                    {
                        if (!oldChildren.containsKey(id))
                        {
                            addedChildIds.add(id);
                        }
                        else if (!((Map)newValue).containsKey(id))
                        {
                            removedChildIds.add(id);
                        }
                        else if (!oldChildren.get(id).equals(((Map)newValue).get(id)))
                        {
                            changedChildIds.add(id);
                        }
                    }
                    if (!changeDetected)
                    {
                        changeDetected = addedChildIds.size() > 0 || removedChildIds.size() > 0 || changedChildIds.size() > 0;
                    }
                }
                else
                {
                    try
                    {
                        if (!changeDetected)
                        {
                            changeDetected = !Objects.equals(oldValue, newValue);
                        }
                    }
                    catch (Exception ex)
                    {
                        throw ex;
                    }
                }

                if (changeDetected)
                {
                    log(" Evolved: " + nodeIndex + " " + node.getNodePath() + " for reason: " + valueToString(triggeringReason) + ": " + valueToString(oldValue) + " -> " + valueToString(newValue));
                    containerMutator_.setValue(nodeIndex, newValue);
                    Collection<IFGEvolveConsumer> nodeEvolverConsumers = node.getEvolveConsumers();
                    if (nodeEvolverConsumers != null)
                    {
                        Map<Object, Object> componentCopy = nodeIndexToComponentCopyForConsumers_.get(node.getNodeIndex());
                        if (componentCopy == null)
                        {
                            componentCopy = new HashMap<>();
                            nodeIndexToComponentCopyForConsumers_.put(node.getNodeIndex(), componentCopy);
                        }
                        componentCopy.put(node.getPropertyId(), newValue);
                    }

                    List<Object> componentPath = component.getComponentPath();

                    addNodeDependentsToEvolvebuffer(node);

                    if (triggeringReason != null && node.isChildrenProperty())
                    {
                        log(" Detected children change");
                        Map<Object, Map<Object, Object>> newChildren = (Map<Object, Map<Object, Object>>) newValue;

                        Set<Object> idsToRemove = new HashSet<>(removedChildIds);
                        idsToRemove.addAll(changedChildIds);
                        Set<Object> idsToAdd = new HashSet<>(addedChildIds);
                        idsToAdd.addAll(changedChildIds);

                        log(" Removing " + removedChildIds.size() + " removed and " + changedChildIds.size() + " changed children...");
                        System.out.println(" Removing " + removedChildIds.size() +
                                " ("+removedChildIds+")"
                                + " removed and " + changedChildIds.size() + " changed children...");
                        Set<Integer> removedChildIndices = new HashSet<>(idsToRemove.size());
                        for (Object id : idsToRemove)
                        {
                            List<Object> childPath = new ArrayList<>(componentPath.size()+1);
                            childPath.addAll(componentPath);
                            childPath.add(id);
                            Integer removedChildIndex = getComponentUid(childPath);
                            removeComponent(removedChildIndex);
                            removedChildIndices.add(removedChildIndex);
                            // TODO deep remove
                            addedComponentIds.remove(removedChildIndex);
                        }

                        log(" Adding " + changedChildIds.size() + " changed and " + addedChildIds.size() + " added children...");
                        System.out.println(" Adding " + changedChildIds.size() + " changed and " + addedChildIds.size() + " added children...");
                        Set<Integer> newChildIndices = new HashSet<>(idsToAdd.size());
                        Map<Object, Integer> newChildIdToIndex = new HashMap<>();

                        idsToAdd
                                .forEach(childId -> {
                                    Map<Object, Object> child = newChildren.get(childId);
                                    Collection<Integer> deepIndices = new HashSet<>();
                                    Integer index = addContainer(node.getComponentUid(), component.getComponentPath(), child, deepIndices);
                                    deepIndices.forEach(addedComponentIds::add);
                                    newChildIndices.add(index);
                                    newChildIdToIndex.put(childId, index);
                                });

                        component.removeChildIndices(removedChildIndices, removedChildIds);
                        component.addChildIndices(newChildIndices, newChildIdToIndex);
                    }
                    if (node.isChildOrderProperty() && newValue != null)
                    {
                        List<Object> newChildIdOrder = (List<Object>) newValue;

                        List<Integer> newChildIndices = new ArrayList<>(newChildIdOrder.size());
                        for (int i=0; i<newChildIdOrder.size(); i++)
                        {
                            List<Object> childPath = new ArrayList<>(componentPath.size()+1);
                            childPath.addAll(componentPath);
                            childPath.add(newChildIdOrder.get(i));
                            newChildIndices.add(getComponentUid(childPath));
                        }
                        component.changeChildIndicesOrder(newChildIndices);
                    }

                    resultCollector_.appendResult(node.getParentComponentUid(), componentPath, node, newValue);
                }
                else
                {
                    log(" Evolved: " + nodeIndex + " " + node.getNodePath() + " for reason: " + valueToString(triggeringReason) + ": no change (" + oldValue + ").");
                }

                if (initializedNodes_ != null)
                {
                    initializedNodes_.add(nodeIndex);
                }
            }

            currentCycleBufIndex_++;
        }

        resultCollector_.postProcessAfterEvolveCycle(containerAccessor_, containerMutator_);

        notifyEvolverConsumers();

        log("---Ended evolve cycle");

        if (!addedComponentIds.isEmpty())
        {
            processAllNodesOfComponents(addedComponentIds, this::setupEvolversForNode);
            processAllNodesOfComponents(addedComponentIds, this::resolveDependencyIndicesForNode);

            processAllNodesOfComponents(addedComponentIds, this::markNodeAsDependent);

            for (Node n : nodesWithAmbiguousDependencies_)
            {
                Collection<Tuple> newDependencies = n.reevaluateAmbiguousDependencies(components_, containerParser_::isWildcardPathElement);
                markNodeAsDependent(n, newDependencies);
            }

            boolean newNodeSet = initializedNodes_ == null;
            if (newNodeSet)
            {
                initializedNodes_ = new HashSet<>();
            }
            addedComponentIds.forEach(this::initializeAddedComponent);
            if (newNodeSet)
            {
                initializedNodes_.clear();
                initializedNodes_ = null;
            }
        }

        long spentEvolving = System.currentTimeMillis() - evolveStartTime;
        //System.out.println("-DLTEMP- Container.evolve spent evolving " + spentEvolving);
    }

    public boolean isInterestedIn(Integer componentUid, Object evolveReason)
    {
        if (evolveReason == null)
        {
            throw new IllegalArgumentException();
        }
        ComponentAccessor initialComponentAccessor = components_.get(componentUid);
        Map<Object, Integer> propertyIdToNodeIndex = initialComponentAccessor.getPropertyIdToIndex();
        for (Object propertyId : propertyIdToNodeIndex.keySet())
        {
            Integer nodeIndex = propertyIdToNodeIndex.get(propertyId);
            Node node = nodes_.get(nodeIndex);
            if (node.getEvolver() != null && containerParser_.isInterestedIn(node.getInputDependencies(), evolveReason.getClass()))
            {
                return true;
            }
        }
        return false;
    }

    public IContainerAccessor getContainerAccessor()
    {
        return containerAccessor_;
    }

    public IPropertyValueAccessor getPropertyValueAccessor()
    {
        return propertyValueAccessor_;
    }

    public final Integer getComponentUid(List<Object> componentPath)
    {
        return componentPathToIndex_.get(componentPath);
    }

    public IComponent getRootComponent()
    {
        return components_.get(0);
    }

    public IComponent getComponent(Integer componentUid)
    {
        return components_.get(componentUid.intValue());
    }

    public Node getNode(Integer nodeId)
    {
        return nodes_.get(nodeId);
    }

    public int getNodeCount()
    {
        return nodes_.size();
    }

    public <V> V getPropertyValue(Integer index)
    {
        //TODO see "strict" return (V) values_.get(index.intValue());
        return index != null ? (V) values_.get(index.intValue()) : null;
    }

    public IResultCollector getResultCollector()
    {
        return resultCollector_;
    }

    public Collection<List<Object>> getAllIdPaths()
    {
        return Collections.unmodifiableSet(pathToIndex_.keySet());
    }

    /**
     * Adds evolve result consumer to the container. Note: consumer is added to the node,
     * and if node is removed then consumer is removed as well and is not re-applied in case
     * new node is added for given path/property
     */
    public void addEvolveConsumer(IFGEvolveConsumer evolveConsumer)
    {
        List<Object> path = evolveConsumer.getTargetPath();
        Collection<Object> targetProperties = evolveConsumer.getTargetProperties();

        for (Object property : targetProperties)
        {
            List fullPath = new ArrayList<>(path.size() + 1);
            fullPath.addAll(path);
            fullPath.add(property);

            Integer nodeIndex = pathToIndex_.get(fullPath);
            if (nodeIndex != null)
            {
                nodes_.get(nodeIndex.intValue()).addEvolveConsumer(evolveConsumer);
            }
            else
            {
                System.out.println("Error: node for " + path + "/" + property + " is not found.");
            }
        }
    }

    // Private

    private Integer addComponentImpl(Integer parentComponentUid, List<Object> componentPath, ComponentAccessor component)
    {
        Integer index;
        if (vacantComponentIndices_.isEmpty())
        {
            index = components_.size();
            components_.add(index, component);
        }
        else
        {
            index = vacantComponentIndices_.stream().findAny().get();
            vacantComponentIndices_.remove(index);
            components_.set(index, component);
        }
        componentPathToIndex_.put(componentPath, index);

        resultCollector_.componentAdded(parentComponentUid, index);

        return index;
    }

    private void removeComponent(Integer componentUid)
    {
        if (componentUid.intValue() >= components_.size())
        {
            throw new IllegalArgumentException("Component does not exist: " + componentUid);
        }
        ComponentAccessor c = components_.get(componentUid.intValue());
        if (c == null)
        {
            throw new IllegalArgumentException("Component already removed: " + componentUid);
        }

        Collection<Integer> propertyIndices = c.getPropertyIndices();
        propertyIndices.forEach(i -> {
            vacantNodeIndices_.add(i);
            Node node = nodes_.set(i.intValue(), null);
            nodesWithAmbiguousDependencies_.remove(node);//TODO there should be indices, not nodes
            values_.set(i.intValue(), null);
            pathToIndex_.remove(node.getNodePath());
            unMarkNodeAsDependent(node, node.getDependencyIndices());
            for (Integer dependentIndex : node.getDependentIndices().keySet())
            {
                Node dependentNode = nodes_.get(dependentIndex.intValue());
                if (dependentNode != null)
                {
                    dependentNode.forgetDependency(i);
                }
            }
            if (initializedNodes_ != null)
            {
                initializedNodes_.remove(i);
            }
            for (int r=currentCycleBufIndex_; r < indexBufferSize_; r++)
            {
                if (reusableNodeBuffer_[r] == node)
                {
                    reusableNodeBuffer_[r] = null;
                }
            }
        });

        components_.set(componentUid.intValue(), null);
        componentPathToIndex_.remove(c.getComponentPath());

        vacantComponentIndices_.add(componentUid);
        resultCollector_.componentRemoved(componentUid);

        List<Integer> children = c.getChildIndices();
        if (children != null)
        {
            children.forEach(this::removeComponent);
        }
    }

    private Integer addContainer(
            Integer parentComponentUid, List<Object> pathToContainer, Map<Object, Object> container, Collection<Integer> addedIndicesCollector)
    {
        // Add and thus index all components/properties

        List<Object> componentPath = new ArrayList<>(pathToContainer.size()+1);
        componentPath.addAll(pathToContainer);
        componentPath.add(containerParser_.getComponentId(container));

        ComponentAccessor component = new ComponentAccessor(
                componentPath, values_, path -> getPropertyValue(indexOfPathStrict(path)));
        Integer componentUid = addComponent(parentComponentUid, componentPath, component);
        component.setComponentUid(componentUid);
        if (addedIndicesCollector != null)
        {
            addedIndicesCollector.add(componentUid);
        }
        log("Added and indexed component " + componentPath + ": " + componentUid);
        Collection<SourceNode> componentPropertyNodes = containerParser_.processComponent(componentPath, container);
        for (SourceNode node : componentPropertyNodes)
        {
            Integer nodeIndex = addNode(parentComponentUid, componentUid, node, container.get(node.getPropertyId()));
            log("Indexing " + componentPath + " node " + node.getNodePath() + ": " + nodeIndex);
            component.putPropertyIndex(node.getPropertyId(), nodeIndex);
        }

        containerParser_.processComponentAfterIndexing(component);

        Map<Object, Map<Object, Object>> children = (Map<Object, Map<Object, Object>>) container.get(containerParser_.getChildrenPropertyName());
        if (children != null)
        {
            List<Integer> childIndices = new ArrayList<>(children.size());
            Map<Object, Integer> childIdToIndex = new HashMap<>();
            for (Map<Object, Object> child : children.values())
            {
                Object childId = containerParser_.getComponentId(child);
                Integer childIndex = addContainer(componentUid, componentPath, child, addedIndicesCollector);
                childIndices.add(childIndex);
                childIdToIndex.put(childId, childIndex);
            }
            component.setChildIndices(childIndices, childIdToIndex);
        }

        return componentUid;
    }

    private void processAllNodesOfComponents(Collection<Integer> addedComponentIds, Consumer<Node> nodeProcessor)
    {
        for (Integer uid : addedComponentIds)
        {
            ComponentAccessor addedComponentAccessor = components_.get(uid);
            Map<Object, Integer> addedComponentPropertyIdToNodeIndex = addedComponentAccessor.getPropertyIdToIndex();
            for (Object propertyId : addedComponentPropertyIdToNodeIndex.keySet())
            {
                Integer nodeIndex = addedComponentPropertyIdToNodeIndex.get(propertyId);
                Node node = nodes_.get(nodeIndex);
                nodeProcessor.accept(node);
            }
        }
    }

    private void initializeAddedComponent(Integer componentUid)
    {
        ComponentAccessor component = components_.get(componentUid);
        if (component == null)
        {
            // Added component may have been removed soon after it had been added
            return;
        }

        evolve(componentUid, null);
    }

    private static List<Object> dropLast(List<Object> path)
    {
        List<Object> list = new ArrayList<>(path);
        list.remove(list.size()-1);
        return list;
    }

    private void setupEvolversForNode(Node n)
    {
//        if (n.getEvolverCode() != null)
//        {
//            n.setEvolver(containerParser_.compileEvolverCode(
//                    n.getPropertyId(), n.getEvolverCode(), dropLast(n.getNodePath()), n.getNodeIndex(), evolverAccess_));
//        }
    }

    private void resolveDependencyIndicesForNode(Node n)
    {
        n.resolveDependencyIndices(components_, containerParser_::isWildcardPathElement);
    }

    private void markNodeAsDependent(Node n)
    {
        markNodeAsDependent(n, n.getDependencyIndices());
    }

    private void markNodeAsDependent(Node n, Collection<Tuple> dependencies)
    {
        dependencies
            .forEach(dependencyTuple -> nodes_.get(dependencyTuple.getFirst()).addDependent(n.getNodeIndex(), n.getNodePath(), dependencyTuple.getSecond()));
    }

    private void unMarkNodeAsDependent(Node n, Collection<Tuple> dependencies)
    {
        dependencies
                .forEach(dependencyTuple -> {
                    // May be null - if node belonged to the component being removed
                    Node dn = nodes_.get(dependencyTuple.getFirst());
                    if (dn != null)
                    {
                        dn.removeDependent(n.getNodeIndex());
                    }
                });
    }

    private void finishContainerIndexing()
    {
        nodes_.forEach(this::setupEvolversForNode);

        // and resolve dependency indices for each property

        nodes_.forEach(this::resolveDependencyIndicesForNode);

        // For each component N, take its dependencies and mark that components that they have N as a dependent

        nodes_.forEach(this::markNodeAsDependent);

        // TODO Optimize:
        // remove dependents covered by longer chains. Maybe not remove but just hide since longer chains may be provided
        // by components that may be removed
    }

    private Integer addNode(Integer parentComponentUid, Integer componentUid, SourceNode sourceNode, Object initialValue)
    {
        Integer index;
        if (vacantNodeIndices_.isEmpty())
        {
             index = nodes_.size();
        }
        else
        {
            index = vacantNodeIndices_.stream().findAny().get();
            vacantNodeIndices_.remove(index);
        }
        Node node;
        if (sourceNode.getEvolverCode() != null)
        {
            node = new EvolvingNode(
                    componentUid,
                    sourceNode.getPropertyId(),
                    parentComponentUid,

                    //this,
                    sourceNode,

                    //sourceNode.isChildrenProperty(),
                    //sourceNode.isChildOrderProperty(),

                    sourceNode.getNodePath(),
                    index,
                    sourceNode.getRelAndAbsDependencyPaths(),
                    sourceNode.getInputDependencies(),
                    sourceNode.getEvolverCode(),
                    evolverAccess_);
        }
        else
        {
            node = new Node(
                    componentUid,
                    sourceNode.getPropertyId(),
                    parentComponentUid,

                    //this,
                    sourceNode,

                    //sourceNode.isChildrenProperty(),
                    //sourceNode.isChildOrderProperty(),

                    sourceNode.getNodePath(),
                    index,
                    sourceNode.getRelAndAbsDependencyPaths(),
                    sourceNode.getInputDependencies(),
                    sourceNode.getEvolverCode());
        }
        int indexInt = index.intValue();
        if (index < nodes_.size())
        {
            nodes_.set(indexInt, node);
            values_.set(indexInt, initialValue);
        }
        else
        {
            nodes_.add(node);
            values_.add(initialValue);
            if (node.isHasAmbiguousDependencies())
            {
                nodesWithAmbiguousDependencies_.add(node);
            }
        }

        pathToIndex_.put(sourceNode.getNodePath(), index);
        return index;
    }

    private void addNodeToReusableBuffer(Node node, Object evolveReason)
    {
        ensureIndexBufferSize(indexBufferSize_ + 1);
        reusableNodeBuffer_[indexBufferSize_] = node;
        reusableReasonBuffer_[indexBufferSize_] = evolveReason;
        indexBufferSize_ ++;
    }

    private void addNodeDependentsToEvolvebuffer(Node node)
    {
        // TODO do not add if it already there for the same reason?

        Map<Integer, List<Object>> dependents = node.getDependentIndices();
        int dependentCollSize = dependents.size();
        ensureIndexBufferSize(indexBufferSize_ + dependentCollSize);
        for (Integer i : dependents.keySet())
        {
            Node dependent = nodes_.get(i.intValue());
            List<Object> invokerRefRelPath = new ArrayList<>(dependents.get(i));
            // By convention, do not include property into what (get-reason) returns
            invokerRefRelPath.remove(invokerRefRelPath.size()-1);

            // TODO delegate reference to Clojure to parser
            invokerRefRelPath = PersistentVector.create(invokerRefRelPath);

            reusableNodeBuffer_[indexBufferSize_] = dependent;
            reusableReasonBuffer_[indexBufferSize_] = invokerRefRelPath;

            log("    Triggered dependent: " + dependent.getNodePath() + " referenced as " + invokerRefRelPath);

            indexBufferSize_ ++;
        }
    }

    private void ensureIndexBufferSize(int requestedSize)
    {
        if (requestedSize >= reusableNodeBuffer_.length)
        {
            reusableNodeBuffer_ = ensureBufferSize(reusableNodeBuffer_, requestedSize);
            reusableReasonBuffer_ = ensureBufferSize(reusableReasonBuffer_, requestedSize);
        }
    }

    private static <T> T[] ensureBufferSize(T[] oldBuffer, int requestedSize)
    {
        if (requestedSize >= oldBuffer.length)
        {
            T[] newBuffer = (T[]) Array.newInstance(oldBuffer.getClass().getComponentType(), oldBuffer.length + 128);
            System.arraycopy(oldBuffer, 0, newBuffer, 0, oldBuffer.length);
            return newBuffer;
        }
        return oldBuffer;
    }

    private void initializeContainer()
    {
        initializedNodes_ = new HashSet<>();
        log("========================Started initialization cycle================================");
        // New components may be added to, or some may be removed from components_ during initialization.
        // So iterate over the snapshot.
        List<Container.ComponentAccessor> components = new ArrayList<>(components_);
        for (int i=0; i<components.size(); i++)
        {
            evolve(Integer.valueOf(i), null);
        }
        log("=====Ended initialization cycle");
        initializedNodes_.clear();
        initializedNodes_ = null;
    }

    static void log(String message)
    {
        if (debug_)
        {
            System.out.println("[FG Eng]: " + message);
        }
    }

    static void logError(String message)
    {
        System.out.println("[FG Eng Error]: " + message);
    }

    static String valueToString(Object v)
    {
        if (v != null)
        {
            String s = v.toString();
            if (s.length() > 100)
            {
                return s.substring(0, 100) + "...";
            }
            else
            {
                return s;
            }
        }
        else
        {
            return null;
        }
    }

    private boolean pathMatches(List<Object> path, List<Object> mapKey)
    {
        if (path.size() == mapKey.size())
        {
            for (int i=0; i<path.size(); i++)
            {
                Object e = path.get(i);
                if (!(e.equals(mapKey.get(i)) || containerParser_.isWildcardPathElement(e) /* TODO(IND) !(path.get(i) instanceof Keyword)*/))
                {
                    return false;
                }
            }
            return true;
        }
        else
        {
            return false;
        }
    }

    private void notifyEvolverConsumers()
    {
        for (Integer nodeIndex : nodeIndexToComponentCopyForConsumers_.keySet())
        {
            Node node = nodes_.get(nodeIndex.intValue());
            for (IFGEvolveConsumer evolveConsumer : node.getEvolveConsumers())
            {
                // TODO container id -> session id
                Map<Object, Object> componentCopy = nodeIndexToComponentCopyForConsumers_.get(nodeIndex);
                Thread t = new Thread(() ->
                        evolveConsumer.acceptEvolveResult(null, componentCopy),
                        "FlatGUI Evolver Consumer Notifier");
                t.start();
            }
        }
        nodeIndexToComponentCopyForConsumers_.clear();
    }

    // // //

    public static class DependencyInfo
    {
        private final List<Object> relPath_;

        private final List<Object> absPath_;

        /**
         * true if contains one or more wildcards (:*)
         */
        private boolean isAmbiguous_;

        public DependencyInfo(List<Object> relPath, List<Object> absPath, boolean isAmbiguous)
        {
            relPath_ = relPath;
            absPath_ = absPath;
            isAmbiguous_ = isAmbiguous;
        }

        public List<Object> getRelPath()
        {
            return relPath_;
        }

        public List<Object> getAbsPath()
        {
            return absPath_;
        }

        public boolean isAmbiguous()
        {
            return isAmbiguous_;
        }
    }


    /**
     * Represents a property of a component (internal indexed)
     */
    public static class SourceNode
    {
        // Path means: last element is property id, all elements before last represent component path

        private final Object propertyId_;

        private final boolean childrenProperty_;

        private final boolean childOrderProperty_;

        private final List<Object> nodePath_;

        private final Collection<DependencyInfo> relAndAbsDependencyPaths_;

        private final boolean hasAmbiguousDependencies_;

        private final Object evolverCode_;

        private final List<Object> inputDependencies_;

        public SourceNode(
                Object propertyId,
                boolean childrenProperty,
                boolean childOrderProperty,
                List<Object> nodePath,
                Collection<DependencyInfo> relAndAbsDependencyPaths,
                Object evolverCode,
                List<Object> inputDependencies)
        {
            propertyId_ = propertyId;
            childrenProperty_ = childrenProperty;
            childOrderProperty_ = childOrderProperty;
            nodePath_ = nodePath;
            relAndAbsDependencyPaths_ = relAndAbsDependencyPaths;
            boolean hasAmbiguousDependencies = false;
            for (DependencyInfo dependencyInfo : relAndAbsDependencyPaths_)
            {
                if (dependencyInfo.isAmbiguous())
                {
                    hasAmbiguousDependencies = true;
                    break;
                }
            }
            hasAmbiguousDependencies_ = hasAmbiguousDependencies;
            evolverCode_ = evolverCode;
            inputDependencies_ = inputDependencies;
        }

        public Object getPropertyId()
        {
            return propertyId_;
        }

        public boolean isChildrenProperty()
        {
            return childrenProperty_;
        }

        public boolean isChildOrderProperty()
        {
            return childOrderProperty_;
        }

        public List<Object> getNodePath()
        {
            return nodePath_;
        }

        public Collection<DependencyInfo> getRelAndAbsDependencyPaths()
        {
            return relAndAbsDependencyPaths_;
        }

        public boolean isHasAmbiguousDependencies()
        {
            return hasAmbiguousDependencies_;
        }

        public Object getEvolverCode()
        {
            return evolverCode_;
        }

        public List<Object> getInputDependencies()
        {
            return inputDependencies_;
        }
    }

    public interface IContainerParser
    {
        Object getComponentId(Map<Object, Object> container);

        Object getChildrenPropertyName();

        Object getChildOrderPropertyName();

        List<Object> getChildOrder(Map<Object, Object> container);

        Collection<SourceNode> processComponent(
                List<Object> componentPath,
                Map<Object, Object> component);

        void processComponentAfterIndexing(IComponent component);

        Function<Map<Object, Object>, Object> compileEvolverCode(
                Object propertyId, Object evolverCode, List<Object> path, int nodeIndex, Container.IEvolverAccess evolverAccess);

        /**
         * @param inputDependencies
         * @param evolveReasonClass
         * @return true if given inputDependencies list explicitly declares dependency on given class of evolveReason,
         *         or if it is not known; false only if it is known that given inputDependencies does NOT
         *         depend on given class of evolveReason
         */
        boolean isInterestedIn(Collection<Object> inputDependencies, Class<?> evolveReasonClass);

        boolean isWildcardPathElement(Object e);
    }

    public interface IComponent extends Map<Object, Object>
    {
        Integer getPropertyIndex(Object key);

        Collection<Integer> getPropertyIndices();

        List<Integer> getChildIndices();

        Integer getChildIndex(Object childId);

        Object getCustomData();

        void setCustomData(Object data);
    }

    public interface IContainerAccessor
    {
        IComponent getComponent(int componentUid);
    }

    public interface IPropertyValueAccessor
    {
        Object getPropertyValue(Integer index);
    }

    public interface IEvolverAccess extends IPropertyValueAccessor
    {
        Integer indexOfPath(List<Object> path);

        Map<Integer, GetPropertyDelegate> getDelegateByIdMap();

        Map<Integer, Map<List<Object>, GetPropertyDelegate>> getDelegateByIdAndPathMap();

        Map<Integer, Map<Keyword, GetPropertyDelegate>> getDelegateByIdAndPropertyMap();

        Map<Integer, Map<List<Object>, Map<Keyword, GetPropertyDelegate>>> getDelegateByIdPathAndPropertyMap();
    }

    public interface IContainerMutator
    {
        void setValue(int nodeIndex, Object newValue);
    }

    public static class ComponentAccessor implements IComponent
    {
        private final List<Object> componentPath_;
        private Map<Object, Integer> propertyIdToIndex_;
        private final List<Object> values_;
        private List<Integer> childIndices_;
        private Map<Object, Integer> childIdToIndex_;

        private Integer componentUid_;

        private final Function<List<Object>, Object> globalIndexToValueProvider_;

        private Object currentEvolveReason_;

        private Object customData_;

        public ComponentAccessor(List<Object> componentPath, List<Object> values, Function<List<Object>, Object> globalIndexToValueProvider)
        {
            componentPath_ = Collections.unmodifiableList(componentPath);
            propertyIdToIndex_ = new HashMap<>();
            values_ = Collections.unmodifiableList(values);
            globalIndexToValueProvider_ = globalIndexToValueProvider;
        }

        @Override
        public Object get(Object key)
        {
            Integer index = getPropertyIndex(key);
            return index != null ? values_.get(index.intValue()) : null;
        }

        @Override
        public int size()
        {
            return propertyIdToIndex_.size();
        }

        @Override
        public boolean isEmpty()
        {
            return propertyIdToIndex_.isEmpty();
        }

        @Override
        public boolean containsKey(Object key)
        {
            return propertyIdToIndex_.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value)
        {
            for (Object key : propertyIdToIndex_.keySet())
            {
                if (Objects.equals(get(key), value))
                {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object put(Object key, Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object remove(Object key)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(Map m)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set keySet()
        {
            return propertyIdToIndex_.keySet();
        }

        @Override
        public Collection values()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Entry<Object, Object>> entrySet()
        {
            Map<Object, Object> propertyIdToVal = new HashMap<>();
            for (Object propId : propertyIdToIndex_.keySet())
            {
                propertyIdToVal.put(propId, get(propId));
            }
            return propertyIdToVal.entrySet();
        }

        @Override
        public Integer getPropertyIndex(Object key)
        {
            return propertyIdToIndex_.get(key);
        }

        @Override
        public Collection<Integer> getPropertyIndices()
        {
            return propertyIdToIndex_.values();
        }

        @Override
        public Object getCustomData()
        {
            return customData_;
        }

        @Override
        public void setCustomData(Object customData)
        {
            customData_ = customData;
        }

        @Override
        public List<Integer> getChildIndices()
        {
            return childIndices_;
        }

        @Override
        public Integer getChildIndex(Object childId)
        {
            return childIdToIndex_.get(childId);
        }

        public Map<Object, Integer> getPropertyIdToIndex()
        {
            return propertyIdToIndex_;
        }

        public Integer getComponentUid()
        {
            return componentUid_;
        }

        void setComponentUid(Integer componentUid)
        {
            componentUid_ = componentUid;
        }

        void putPropertyIndex(Object key, Integer index)
        {
            propertyIdToIndex_.put(key, index);
        }

        void finishInitialization()
        {
            propertyIdToIndex_ = Collections.unmodifiableMap(propertyIdToIndex_);
        }

        public List<Object> getComponentPath()
        {
            return componentPath_;
        }

        void setChildIndices(List<Integer> childIndices, Map<Object, Integer> childIdToIndex)
        {
            childIndices_ = childIndices;
            childIdToIndex_ = childIdToIndex;
        }

        void changeChildIndicesOrder(Collection<Integer> childIndices)
        {
            if (debug_)
            {
                Set<Integer> oldSet = new HashSet<>(childIndices_);
                Set<Integer> newSet = new HashSet<>(childIndices);
                if (!oldSet.equals(newSet))
                {
                    throw new IllegalStateException("Old child indices: " + oldSet + " new: " + newSet);
                }
            }
            childIndices_ = new ArrayList<>(childIndices);
        }

        void addChildIndices(Collection<Integer> childIndices, Map<Object, Integer> childIdToIndex)
        {
            childIndices_.addAll(childIndices);
            childIdToIndex_.putAll(childIdToIndex);
        }

        void removeChildIndices(Collection<Integer> childIndices, Collection<Object> childIds)
        {
            childIndices_.removeAll(childIndices);
            for (Object id : childIds)
            {
                childIdToIndex_.remove(id);
            }
        }

        void setEvolveReason(Object reason)
        {
            currentEvolveReason_ = reason;
        }

        // Methods immediately available for evolvers to implement get-property and get-reason

        public Object getNodeValueByIndex(Integer index)
        {
            return values_.get(index);
        }

        public Object getEvolveReason()
        {
            return currentEvolveReason_;
        }

        public Object getValueByAbsPath(List<Object> asbPath)
        {
            return globalIndexToValueProvider_.apply(asbPath);
        }
    }

}
