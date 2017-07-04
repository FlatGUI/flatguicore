/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */
package flatgui.core.engine.ui;

import clojure.lang.Var;
import flatgui.core.engine.ClojureContainerParser;
import flatgui.core.websocket.FGWebInteropUtil;

import java.io.InputStream;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.Future;

/**
 * @author Denis Lebedev
 */
public class FGTestAppContainer extends FGAppContainer<FGWebInteropUtil>
{
    public FGTestAppContainer(String containerId, Map<Object, Object> container)
    {
        this(containerId != null ? containerId : container.get(ClojureContainerParser.getIdKey()).toString(), container, DFLT_UNIT_SIZE_PX);
    }

    public FGTestAppContainer(String containerId, Map<Object, Object> container, int unitSizePx)
    {
        super(containerId, container, null, new FGWebInteropUtil(unitSizePx),
                new FGClojureResultCollector(unitSizePx), unitSizePx, new FGTestMouseEventParser(unitSizePx));
    }

    public static FGTestAppContainer loadSourceCreateAndInit(String classPathResource, String containerNs, String containerVarName)
    {
        InputStream is = FGTestAppContainer.class.getClassLoader().getResourceAsStream(classPathResource);
        return loadSourceCreateAndInit(is, containerNs, containerVarName);
    }

    public static FGTestAppContainer loadSourceCreateAndInit(InputStream source, String containerNs, String containerVarName)
    {
        String sourceCode = new Scanner(source).useDelimiter("\\Z").next();

        System.out.println(clojure.lang.RT.byteCast(1));
        clojure.lang.Compiler.load(new StringReader(sourceCode));

        return createAndInit(null, containerNs, containerVarName);
    }

    public static FGTestAppContainer createAndInit(String containerId, String containerNs, String containerVarName)
    {
        Var containerVar = clojure.lang.RT.var(containerNs, containerVarName);
        return createAndInit(containerId, containerVar);
    }

    public static FGTestAppContainer createAndInit(String containerId, Var containerVar)
    {
        Map<Object, Object> container = (Map<Object, Object>) containerVar.get();
        return init(containerId, container);
    }

    public static FGTestAppContainer init(String containerId, Map<Object, Object> container)
    {
        FGTestAppContainer appContainer = new FGTestAppContainer(containerId, container);
        appContainer.initialize();

        return appContainer;
    }

    @Override
    public Future<?> evolve(Object evolveReason)
    {
        if (evolveReason instanceof Collection)
        {
            evolveCons((Collection<Object>) evolveReason);
            return null;
        }
        else
        {
            return super.evolve(evolveReason);
        }
    }

    @Override
    public void evolve(List<Object> targetPath, Object evolveReason)
    {
        if (evolveReason instanceof Collection)
        {
            evolveCons(targetPath, (Collection<Object>) evolveReason);
        }
        else
        {
            super.evolve(targetPath, evolveReason);
        }
    }

    private void evolveCons(List<Object> targetPath, Collection<Object> evolveReason)
    {
        getEvolverExecutorService().submit(() -> evolveReason.forEach(r -> getContainer().evolve(targetPath, r)));
    }

    private void evolveCons(Collection<Object> evolveReason)
    {
        evolveReason.forEach(r -> evolve(r));
    }
}
