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

import clojure.lang.*;
import flatgui.core.FGClipboardEvent;
import flatgui.core.FGHostStateEvent;
import flatgui.core.FGTimerEvent;
import flatgui.core.awt.FGMouseEvent;
import flatgui.core.awt.FGMouseWheelEvent;
import flatgui.util.CompactList;
import flatgui.util.ObjectMatrix;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Denis Lebedev
 */
public class ClojureContainerParser implements Container.IContainerParser
{
    private static final Keyword ID_KEY = Keyword.intern("id");
    private static final Keyword CHILDREN_KEY = Keyword.intern("children");
    private static final Keyword EVOLVERS_KEY = Keyword.intern("evolvers");

    private static final Keyword SOURCE_META_KEY = Keyword.intern("source");
    private static final Keyword SYMBOL_META_KEY = Keyword.intern("ns-qualified-sym");
    private static final Keyword INPUT_DEPENDENCIES_META_KEY = Keyword.intern("input-channel-dependencies");
    private static final Keyword RELATIVE_DEPENDENCIES_META_KEY = Keyword.intern("relative-dependencies");

    private static final Keyword THIS_KW = Keyword.intern("this");
    private static final Keyword UP_LEVEL_KW = Keyword.intern("_");

    private static final String FG_NS = "flatgui.core";
    private static final String FG_DEP_NS = "flatgui.dependency"; // TODO move to core?

    private static final Var collectAllEvolverDependencies_ = clojure.lang.RT.var(FG_NS, "collect-all-evolver-dependencies");
    private static final Var compileEvolver_ = clojure.lang.RT.var(FG_NS, "compile-evolver");
    private static final Var getInputDependencies_ = clojure.lang.RT.var(FG_DEP_NS, "get-input-dependencies");

    private static final Keyword WILDCARD_KEY = Keyword.intern("*");

    private static final Map<Class<?>, Keyword> INPUT_EVENT_KEYS;
    static
    {
        Map<Class<?>, Keyword> m = new HashMap<>();
        m.put(MouseEvent.class, Keyword.intern("mouse"));
        m.put(FGMouseEvent.class, Keyword.intern("mouse"));
        m.put(MouseWheelEvent.class, Keyword.intern("mousewheel"));
        m.put(FGMouseWheelEvent.class, Keyword.intern("mousewheel"));
        m.put(KeyEvent.class, Keyword.intern("keyboard"));
        m.put(FGHostStateEvent.class, Keyword.intern("host"));
        m.put(FGClipboardEvent.class, Keyword.intern("clipboard"));
        m.put(FGTimerEvent.class, Keyword.intern("timer"));
        INPUT_EVENT_KEYS = Collections.unmodifiableMap(m);
    }

    private ObjectMatrix<Object> keys_;

    @Override
    public void setKeyMatrix(ObjectMatrix<Object> keys)
    {
        keys_ = keys;
    }

    @Override
    public Object getComponentId(Map<Object, Object> container)
    {
        return container.get(getIdKey());
    }

    public static Object getIdKey()
    {
        return ID_KEY;
    }

    @Override
    public Object getChildOrderPropertyName()
    {
        // This impl does not support particular child order
        return null;
    }

    @Override
    public Object getChildrenPropertyName()
    {
        return CHILDREN_KEY;
    }

    @Override
    public Collection<Container.SourceNode> processComponent(List<Object> componentPath, Map<Object, Object> component)
    {
        Map<Object, Object> evolvers = (Map<Object, Object>) component.get(EVOLVERS_KEY);

// TODO(IND)
//        Map<Object, Collection<List<Object>>> propertyIdToDependencies =
//                (Map<Object, Collection<List<Object>>>) collectAllEvolverDependencies_.invoke(component);

        Collection<Container.SourceNode> result = new ArrayList<>(component.size());

        Set<Object> allPropertyIds = new HashSet<>();
        allPropertyIds.addAll(component.keySet());
        if (evolvers != null)
        {
            allPropertyIds.addAll(evolvers.keySet());
        }
        for (Object propertyId : allPropertyIds)
        {
            List<Object> nodePath = new CompactList<>(keys_, componentPath);
            nodePath.add(propertyId);

            boolean hasEvolver = evolvers != null && evolvers.get(propertyId) != null;

            Object evolverCode = evolvers != null ? evolvers.get(propertyId) : null;

            //List<Object> evolverInputDependencies = evolvers != null ? (List<Object>) getInputDependencies_.invoke(evolverCode) : null;
            List<Object> evolverInputDependencies = null;
            Collection<List<Object>> dependencyPaths = Collections.emptySet(); // TODO(IND) evolver's meta should have relative dependencies
            Collection<Container.DependencyInfo> relAndAbsDependencyPaths = Collections.emptySet();
            // TODO(IND)
            if (hasEvolver)
            {
                IFn evolverFn = (IFn) evolverCode;
                if (evolverFn instanceof Var.Unbound)
                {
                    throw new IllegalStateException("unbound evolver fn: " + componentPath + " " + propertyId);
                }
                Map<Object, Object> evolverFnMeta = (Map<Object, Object>) ((IMeta)evolverFn).meta();
                //Symbol symbol = (Symbol) evolverFnMeta.get(SYMBOL_META_KEY);
                if (evolverFnMeta == null)
                {
                    throw new IllegalStateException("null meta of evolver fn: " + componentPath + " " + propertyId);
                }
                evolverInputDependencies = (List<Object>) evolverFnMeta.get(INPUT_DEPENDENCIES_META_KEY);
                dependencyPaths = (Collection<List<Object>>) evolverFnMeta.get(RELATIVE_DEPENDENCIES_META_KEY);
                boolean processingRoot = componentPath.size() == 1;
                relAndAbsDependencyPaths = dependencyPaths.stream()
                        .filter(relDep -> !processingRoot || relDep.size() > 1)
                        .map(relDep -> new Container.DependencyInfo(
                                relDep,
                                buildAbsPath(keys_, componentPath, relDep),
                                relDep.stream().anyMatch(e -> WILDCARD_KEY.equals(e))))
                        .collect(Collectors.toList());
                //dependencyPaths = GetPropertyClojureFnRegistry.attachToInstance(symbol, componentPath, propertyValueAccessor);
            }

            result.add(new Container.SourceNode(
                    propertyId,
                    getChildrenPropertyName().equals(propertyId),
                    getChildOrderPropertyName() != null && getChildOrderPropertyName().equals(propertyId),
                    nodePath,
                    hasEvolver
                            ? /*propertyIdToDependencies.get(propertyId)*/relAndAbsDependencyPaths
                            : Collections.emptySet(),
                    hasEvolver
                            ? evolverCode
                            : null,
                    hasEvolver
                            ? evolverInputDependencies
                            : null));
        }

        return result;
    }

    @Override
    public boolean isInterestedIn(Collection<Object> inputDependencies, Class<?> evolveReasonClass)
    {
        // For all input channel events (kw != null), check if depends on; otherwise just accept
        Keyword kw = INPUT_EVENT_KEYS.get(evolveReasonClass);
        return kw != null ? inputDependencies.contains(kw) : true;
    }

    @Override
    public boolean isWildcardPathElement(Object e)
    {
        return WILDCARD_KEY.equals(e);
    }

    @Override
    public void processComponentAfterIndexing(Container.IComponent component)
    {
    }

    @Override
    public int getTotalNodeCount(Map<Object, Object> component)
    {
        int n = component.size() - 1; // Minus :evolvers

        Map<Object, Map<Object, Object>> children = (Map<Object, Map<Object, Object>>) component.get(CHILDREN_KEY);
        if (children != null)
        {
            n--; // Minus :children
            for (Map<Object, Object> child : children.values())
            {
                n += getTotalNodeCount(child);
            }
        }

        return n;
    }

    @Override
    public int getTotalComponentCount(Map<Object, Object> container)
    {
        int n = 1;

        Map<Object, Map<Object, Object>> children = (Map<Object, Map<Object, Object>>) container.get(CHILDREN_KEY);
        if (children != null)
        {
            for (Map<Object, Object> child : children.values())
            {
                n += getTotalComponentCount(child);
            }
        }

        return n;
    }

    static List<Object> buildAbsPath(ObjectMatrix<Object> keyMatrix, List<Object> componentPath, List<Object> relPath)
    {
        try
        {
            List<Object> absPath;
            if (relPath.isEmpty())
            {
                absPath = new CompactList<>(keyMatrix, componentPath);
                absPath.remove(absPath.size() - 1);
            }
            else if (relPath.get(0).equals(THIS_KW))
            {
//            List<Object> next = new ArrayList<>(relPath);
//            next.remove(0);
//            absPath.addAll(next);
                absPath = new CompactList<>(keyMatrix, componentPath);
                for (int i = 1; i < relPath.size(); i++)
                {
                    absPath.add(relPath.get(i));
                }
            }
            else if (relPath.get(0).equals(UP_LEVEL_KW))
            {
                int upLevelCount = 0;
                while (upLevelCount < relPath.size() && relPath.get(upLevelCount).equals(UP_LEVEL_KW))
                {
                    upLevelCount++;
                }
                int componentPathCountToTake = componentPath.size() - upLevelCount - 1;
                absPath = new CompactList<>(keyMatrix);
                //absPath = absPath.subList(0, componentPathCountToTake);
                for (int i = 0; i < componentPathCountToTake; i++)
                {
                    absPath.add(componentPath.get(i));
                }
                //absPath.addAll(relPath.subList(upLevelCount, relPath.size()));
                for (int i = upLevelCount; i < relPath.size(); i++)
                {
                    absPath.add(relPath.get(i));
                }
            }
            else
            {
                absPath = new CompactList<>(keyMatrix, componentPath);
                absPath.remove(absPath.size() - 1);
                //absPath.addAll(relPath);
                for (int i = 0; i < relPath.size(); i++)
                {
                    absPath.add(relPath.get(i));
                }
            }
            return absPath;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Can't build path (path too long) " +
                componentPath + " + " + relPath, ex);
        }
    }
}
