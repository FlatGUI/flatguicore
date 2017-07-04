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
import flatgui.core.awt.FGIncomingMouseWheelEvent;
import flatgui.util.CompactList;
import flatgui.util.ObjectMatrix;

import java.awt.event.MouseWheelEvent;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Denis Lebedev
 */
public class Container
{
    private final IResultCollector resultCollector_;

    private final Set<Integer> vacantComponentIndices_;
    private final Set<Integer> vacantNodeIndices_;

    private final ObjectMatrix<Object> keys_;

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

    private final Function<List<Object>, Object> globalIndexToValueProvider_;

    private Node[] reusableNodeBuffer_;
    private Object[] reusableReasonBuffer_;
    private int indexBufferSize_;
    private int currentCycleBufIndex_;
    private int maxIndexBufferSize_;
    private int totalNodeCount_ = 0;

    private Object originalReasonForConsumers_;
    private Set<Integer> nodeIndicesToNotifyConsumers_;

    private Set<Integer> initializedNodes_;

    // Evolver access TODO should not refer Clojure or ClojureContainerParser directly
    private final HashMap<Integer, GetPropertyDelegate> delegateByIdMap_;
    private final HashMap<Integer, Map<List<Object>, GetPropertyDelegate>> delegateByIdAndPathMap_;
    private final HashMap<Integer, Map<Keyword, GetPropertyDelegate>> delegateByIdAndPropertyMap_;
    private final HashMap<Integer, Map<List<Object>, Map<Keyword, GetPropertyDelegate>>> delegateByIdPathAndPropertyMap_;

    private final String containerId_;

    private final Consumer<Runnable> consumerNotifier_;

    public static boolean debug_ = false;

    public Container(String containerId, IContainerParser containerParser, IResultCollector resultCollector, Map<Object, Object> container)
    {
        this(containerId, containerParser, resultCollector, container, r -> new Thread(r, "FlatGUI Evolver Consumer Notifier").start());
    }

    public Container(String containerId, IContainerParser containerParser, IResultCollector resultCollector, Map<Object, Object> container, Consumer<Runnable> consumerNotifier)
    {
        containerId_ = containerId;

        keys_ = new ObjectMatrix<>();
        containerParser_ = containerParser;
        containerParser_.setKeyMatrix(keys_);
        resultCollector_ = resultCollector;
        components_ = new ArrayList<>();
        componentPathToIndex_ = new HashMap<>();
        vacantComponentIndices_ = new HashSet<>();
        vacantNodeIndices_ = new HashSet<>();
        nodes_ = new ArrayList<>();
        nodesWithAmbiguousDependencies_ = new ArrayList<>();
        values_ = new ArrayList<>();
        pathToIndex_ = new HashMap<>();
        nodeIndicesToNotifyConsumers_ = new LinkedHashSet<>();

        delegateByIdMap_ = new HashMap<>();
        delegateByIdAndPathMap_ = new HashMap<>();
        delegateByIdAndPropertyMap_ = new HashMap<>();
        delegateByIdPathAndPropertyMap_ = new HashMap<>();

        reusableNodeBuffer_ = new Node[1048576];
        reusableReasonBuffer_ = new Object[1048576];

        totalNodeCount_ = containerParser.getTotalNodeCount(container);

        indexBufferSize_ = 0;
        maxIndexBufferSize_ = 0;

        containerAccessor_ = components_::get;
        propertyValueAccessor_ = this::getPropertyValue;
        evolverAccess_ = new ConainerEvolverAccess(this);
        containerMutator_ = (nodeIndex, newValue) ->
            {synchronized (Container.this) {values_.set(nodeIndex, newValue);}};

        consumerNotifier_  = consumerNotifier;

        globalIndexToValueProvider_ = path -> getPropertyValue(indexOfPathStrict(path));

        addContainer(null, Collections.emptyList(), container, null);
        finishContainerIndexing();

        initializeContainer();
    }

    // TODO light copy: assuming no components will be added/removed during container lifecycle
    public Container(Container source,
            String containerId, IContainerParser containerParser, IResultCollector resultCollector, Consumer<Runnable> consumerNotifier)
    {
        values_ = new ArrayList<>(source.values_);
        globalIndexToValueProvider_ = path -> getPropertyValue(indexOfPathStrict(path));
        evolverAccess_ = new ConainerEvolverAccess(this);

        containerId_ = containerId;

        keys_ = new ObjectMatrix<>(source.keys_);
        containerParser_ = containerParser;
        containerParser_.setKeyMatrix(keys_);
        resultCollector_ = resultCollector;

        components_ = new ArrayList<>(source.components_.size());
        components_.addAll(source.components_.stream()
                .map(sourceComponentAccessor -> new ComponentAccessor(sourceComponentAccessor, values_, globalIndexToValueProvider_))
                .collect(Collectors.toList()));

        componentPathToIndex_ = new HashMap<>(source.componentPathToIndex_);
        vacantComponentIndices_ = new HashSet<>(source.vacantComponentIndices_);
        vacantNodeIndices_ = new HashSet<>(source.vacantNodeIndices_);

        nodes_ = new ArrayList<>(source.nodes_.size());
        nodes_.addAll(source.nodes_.stream()
                .map(n -> n instanceof EvolvingNode ? new EvolvingNode((EvolvingNode) n, evolverAccess_) : new Node(n))
                .collect(Collectors.toList()));
        nodesWithAmbiguousDependencies_ = new ArrayList<>();
        nodesWithAmbiguousDependencies_.addAll(nodes_.stream()
                .filter(n -> n.isHasAmbiguousDependencies())
                .collect(Collectors.toList()));

        //values_ = new ArrayList<>();
        pathToIndex_ = new HashMap<>(source.pathToIndex_);
        nodeIndicesToNotifyConsumers_ = new LinkedHashSet<>(source.nodeIndicesToNotifyConsumers_);

        delegateByIdMap_ = new HashMap<>();
        delegateByIdAndPathMap_ = new HashMap<>();
        delegateByIdAndPropertyMap_ = new HashMap<>();
        delegateByIdPathAndPropertyMap_ = new HashMap<>();

        reusableNodeBuffer_ = new Node[1048576];
        reusableReasonBuffer_ = new Object[1048576];

        indexBufferSize_ = 0;
        maxIndexBufferSize_ = 0;

        containerAccessor_ = components_::get;
        propertyValueAccessor_ = this::getPropertyValue;
        //evolverAccess_ = new ConainerEvolverAccess(this);
        containerMutator_ = (nodeIndex, newValue) ->
        {synchronized (Container.this) {values_.set(nodeIndex, newValue);}};

        consumerNotifier_  = consumerNotifier;
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

        if (debug_) logDebug("----------------Started evolve cycle ---- for reason: " + valueToString(evolveReason));

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
            if (debug_) logDebug(" Initial component: " + reusableNodeBuffer_[i].getNodePath());
        }

        Set<Integer> addedComponentIds = new HashSet<>();
        currentCycleBufIndex_ = 0;
        nodeIndicesToNotifyConsumers_.clear();
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
            int nodeIndex = node.getNodeIndex();
            Function<Map<Object, Object>, Object> evolver = node.getEvolver();

            if (triggeringReason != null || initializedNodes_ != null && !initializedNodes_.contains(Integer.valueOf(nodeIndex)))
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
                    if (debug_) logDebug(" Evolved: " + nodeIndex + " " + node.getNodePath() + " for reason: " + valueToString(triggeringReason) + ": " + valueToString(oldValue) + " -> " + valueToString(newValue));
                    containerMutator_.setValue(nodeIndex, newValue);
                    Collection<IFGEvolveConsumer> nodeEvolverConsumers = node.getEvolveConsumers();
                    if (nodeEvolverConsumers != null)
                    {
                        originalReasonForConsumers_ = evolveReason;
                        nodeIndicesToNotifyConsumers_.add(Integer.valueOf(nodeIndex));
                    }

                    List<Object> componentPath = component.getComponentPath();

                    addNodeDependentsToEvolvebuffer(node);

                    if (triggeringReason != null && node.isChildrenProperty())
                    {
                        if (debug_) logDebug(" Detected children change");
                        Map<Object, Map<Object, Object>> newChildren = (Map<Object, Map<Object, Object>>) newValue;

                        Set<Object> idsToRemove = new HashSet<>(removedChildIds);
                        idsToRemove.addAll(changedChildIds);
                        Set<Object> idsToAdd = new HashSet<>(addedChildIds);
                        idsToAdd.addAll(changedChildIds);

                        if (debug_) logDebug(" Removing " + removedChildIds.size() + " removed and " + changedChildIds.size() + " changed children...");
                        Set<Integer> removedChildIndices = new HashSet<>(idsToRemove.size());
                        for (Object id : idsToRemove)
                        {
                            List<Object> childPath = new CompactList<>(keys_, componentPath);
                            childPath.add(id);
                            Integer removedChildIndex = getComponentUid(childPath);
                            removeComponent(removedChildIndex);
                            removedChildIndices.add(removedChildIndex);
                            // TODO deep remove
                            addedComponentIds.remove(removedChildIndex);
                        }

                        if (debug_) logDebug(" Adding " + changedChildIds.size() + " changed and " + addedChildIds.size() + " added children...");
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
                            List<Object> childPath = new CompactList<>(keys_, componentPath);
                            childPath.add(newChildIdOrder.get(i));
                            newChildIndices.add(getComponentUid(childPath));
                        }
                        component.changeChildIndicesOrder(newChildIndices);
                    }

                    resultCollector_.appendResult(node.getParentComponentUid(), componentPath, node, newValue);
                }
                else
                {
                    if (debug_) logDebug(" Evolved: " + nodeIndex + " " + node.getNodePath() + " for reason: " + valueToString(triggeringReason) + ": no change (" + oldValue + ").");
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

        if (debug_) logDebug("---Ended evolve cycle");

        if (!addedComponentIds.isEmpty())
        {
            processAllNodesOfComponents(addedComponentIds, this::setupEvolversForNode);
            processAllNodesOfComponents(addedComponentIds, this::resolveDependencyIndicesForNode);

            processAllNodesOfComponents(addedComponentIds, this::markNodeAsDependent);

            for (Node n : nodesWithAmbiguousDependencies_)
            {
                Collection<Node.Dependency> newDependencies = n.reevaluateAmbiguousDependencies(components_, containerParser_::isWildcardPathElement);
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
            Class<?> evolveReasonClass;
            if (evolveReason instanceof FGIncomingMouseWheelEvent)
            {
                // TODO this is a hack
                evolveReasonClass = MouseWheelEvent.class;
            }
            else
            {
                evolveReasonClass = evolveReason.getClass();
            }
            if (node.getEvolver() != null && containerParser_.isInterestedIn(node.getInputDependencies(), evolveReasonClass))
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

    public synchronized <V> V getPropertyValue(Integer index)
    {
        return index != null ? (V) values_.get(index.intValue()) : null;
    }

    public <V> V getPropertyValue(List<Object> path, Object property)
    {
        Integer componentUid = getComponentUid(path);
        if (componentUid == null)
        {
            throw new IllegalArgumentException("Component not found for path: " + path);
        }
        Container.IComponent component = getComponent(componentUid);
        Integer propertyIndex = component.getPropertyIndex(property);
        return getPropertyValue(propertyIndex);
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
            List fullPath = new CompactList(keys_, path);
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
            containerMutator_.setValue(i.intValue(), null);
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

    // TODO check against CompactList element maximum size
    private Integer addContainer(
            Integer parentComponentUid, List<Object> pathToContainer, Map<Object, Object> container, Collection<Integer> addedIndicesCollector)
    {
        // Add and thus index all components/properties

        List<Object> componentPath = new CompactList<>(keys_, pathToContainer);
        componentPath.add(containerParser_.getComponentId(container));

        ComponentAccessor component = new ComponentAccessor(
                componentPath, values_, globalIndexToValueProvider_);
        Integer componentUid = addComponent(parentComponentUid, componentPath, component);
        component.setComponentUid(componentUid);
        if (addedIndicesCollector != null)
        {
            addedIndicesCollector.add(componentUid);
        }
        if (debug_) logDebug("Added and indexed component " + componentPath + ": " + componentUid);
        Collection<SourceNode> componentPropertyNodes = containerParser_.processComponent(componentPath, container);
        for (SourceNode node : componentPropertyNodes)
        {
            Integer nodeIndex = addNode(parentComponentUid, componentUid, node, container.get(node.getPropertyId()));
            if (debug_) logDebug("Indexing " + componentPath + " node " + node.getNodePath() + ": " + nodeIndex);
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

    private List<Object> dropLast(List<Object> path)
    {
        List<Object> list = new CompactList<>(keys_, path);
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

    private void markNodeAsDependent(Node n, Collection<Node.Dependency> dependencies)
    {
        dependencies
            .forEach(d -> nodes_.get(d.getNodeIndex()).addDependent(Integer.valueOf(n.getNodeIndex()), n.getNodePath(), d.getRelPath(), keys_));
    }

    private void unMarkNodeAsDependent(Node n, Collection<Node.Dependency> dependencies)
    {
        dependencies
                .forEach(dependencyTuple -> {
                    // May be null - if node belonged to the component being removed
                    Node dn = nodes_.get(dependencyTuple.getNodeIndex());
                    if (dn != null)
                    {
                        dn.removeDependent(Integer.valueOf(n.getNodeIndex()));
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
                    parentComponentUid != null ? parentComponentUid.intValue() : -1,
                    sourceNode,
                    index,
                    evolverAccess_);
        }
        else
        {
            node = new Node(
                    componentUid,
                    parentComponentUid != null ? parentComponentUid.intValue() : -1,
                    sourceNode,
                    index);
        }
        int indexInt = index.intValue();
        if (index < nodes_.size())
        {
            nodes_.set(indexInt, node);
            containerMutator_.setValue(indexInt, initialValue);
        }
        else
        {
            nodes_.add(node);
            synchronized (this)
            {
                values_.add(initialValue);
            }
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
        recordIndexBufferSize();
    }

    private void recordIndexBufferSize()
    {
        if (indexBufferSize_ > maxIndexBufferSize_)
        {
            maxIndexBufferSize_ = indexBufferSize_;
            System.out.println("[FG tmp] Index buffer size has grown to " + maxIndexBufferSize_ + " est tot = " + totalNodeCount_);
        }
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
            List<Object> invokerRefRelPath = new CompactList<>(keys_, dependents.get(i));
            // By convention, do not include property into what (get-reason) returns
            invokerRefRelPath.remove(invokerRefRelPath.size()-1);

            // TODO delegate reference to Clojure to parser
            invokerRefRelPath = PersistentVector.create(invokerRefRelPath);

            reusableNodeBuffer_[indexBufferSize_] = dependent;
            reusableReasonBuffer_[indexBufferSize_] = invokerRefRelPath;

            if (debug_) logDebug("    Triggered dependent: " + dependent.getNodePath() + " referenced as " + invokerRefRelPath);

            indexBufferSize_ ++;
            recordIndexBufferSize();
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
        if (debug_) logDebug("========================Started initialization cycle================================");
        // New components may be added to, or some may be removed from components_ during initialization.
        // So iterate over the snapshot.
        List<Container.ComponentAccessor> components = new ArrayList<>(components_);
        for (int i=0; i<components.size(); i++)
        {
            evolve(Integer.valueOf(i), null);
        }
        if (debug_) logDebug("=====Ended initialization cycle");
        initializedNodes_.clear();
        initializedNodes_ = null;
    }

    static void logDebug(String message)
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
        for (Integer nodeIndex : nodeIndicesToNotifyConsumers_)
        {
            Node node = nodes_.get(nodeIndex.intValue());
            for (IFGEvolveConsumer evolveConsumer : node.getEvolveConsumers())
            {
                // This copy is safe to let consumer notifier thread read from it
                Map<Object, Object> componentCopy = Collections.unmodifiableMap(getComponent(node.getComponentUid()));
                consumerNotifier_.accept(() -> evolveConsumer.acceptEvolveResult(containerId_, componentCopy, originalReasonForConsumers_));
            }
        }
        nodeIndicesToNotifyConsumers_.clear();
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
        void setKeyMatrix(ObjectMatrix<Object> keys);

        Object getComponentId(Map<Object, Object> container);

        Object getChildrenPropertyName();

        Object getChildOrderPropertyName();

        Collection<SourceNode> processComponent(
                List<Object> componentPath,
                Map<Object, Object> component);

        void processComponentAfterIndexing(IComponent component);

        /**
         * @param inputDependencies
         * @param evolveReasonClass
         * @return true if given inputDependencies list explicitly declares dependency on given class of evolveReason,
         *         or if it is not known; false only if it is known that given inputDependencies does NOT
         *         depend on given class of evolveReason
         */
        boolean isInterestedIn(Collection<Object> inputDependencies, Class<?> evolveReasonClass);

        boolean isWildcardPathElement(Object e);

        int getTotalNodeCount(Map<Object, Object> container);
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

        ObjectMatrix<Object> getKeyMatrix();
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

        ComponentAccessor(ComponentAccessor source, List<Object> values, Function<List<Object>, Object> globalIndexToValueProvider)
        {
            componentPath_ = source.getComponentPath();
            propertyIdToIndex_ = source.getPropertyIdToIndex();
            values_ = Collections.unmodifiableList(values);
            childIndices_ = new ArrayList<>(source.childIndices_);
            childIdToIndex_ = new HashMap<>(source.childIdToIndex_);
            componentUid_ = source.componentUid_;

            customData_ = source.customData_;

            globalIndexToValueProvider_ = globalIndexToValueProvider;
        }

        public Object getId()
        {
            return componentPath_.get(componentPath_.size()-1);
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

    static class ConainerEvolverAccess implements IEvolverAccess
    {
        private final Container container_;

        public ConainerEvolverAccess(Container container)
        {
            container_ = container;
        }

        @Override
        public Integer indexOfPath(List<Object> path)
        {
            return container_.indexOfPath(path);
        }

        @Override
        public Object getPropertyValue(Integer index)
        {
            return container_.getPropertyValue(index);
        }

        @Override
        public Map<Integer, GetPropertyDelegate> getDelegateByIdMap()
        {
            return container_.delegateByIdMap_;
        }

        @Override
        public Map<Integer, Map<List<Object>, GetPropertyDelegate>> getDelegateByIdAndPathMap()
        {
            return container_.delegateByIdAndPathMap_;
        }

        @Override
        public Map<Integer, Map<Keyword, GetPropertyDelegate>> getDelegateByIdAndPropertyMap()
        {
            return container_.delegateByIdAndPropertyMap_;
        }

        @Override
        public Map<Integer, Map<List<Object>, Map<Keyword, GetPropertyDelegate>>> getDelegateByIdPathAndPropertyMap()
        {
            return container_.delegateByIdPathAndPropertyMap_;
        }

        @Override
        public ObjectMatrix<Object> getKeyMatrix()
        {
            return container_.keys_;
        }
    }
}
