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

/**
 * @author Denis Lebedev
 */
public class FGTestAppContainer extends FGAppContainer<FGWebInteropUtil>
{
    public FGTestAppContainer(Map<Object, Object> container)
    {
        this(container, DFLT_UNIT_SIZE_PX);
    }

    public FGTestAppContainer(Map<Object, Object> container, int unitSizePx)
    {
        super(container.get(ClojureContainerParser.getIdKey()).toString(), container, new FGWebInteropUtil(unitSizePx),
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

        return createAndInit(containerNs, containerVarName);
    }

    public static FGTestAppContainer createAndInit(String containerNs, String containerVarName)
    {
        Var containerVar = clojure.lang.RT.var(containerNs, containerVarName);
        return createAndInit(containerVar);
    }

    public static FGTestAppContainer createAndInit(Var containerVar)
    {
        Map<Object, Object> container = (Map<Object, Object>) containerVar.get();

        FGTestAppContainer appContainer = new FGTestAppContainer(container);
        appContainer.initialize();

        return appContainer;
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
}
