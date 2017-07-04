/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.remote;

import clojure.lang.Var;
import flatgui.core.FGTemplate;
import flatgui.core.FGWebContainerWrapper;
import flatgui.core.engine.ui.FGAppContainer;
import flatgui.core.engine.ui.FGRemoteAppContainer;
import flatgui.core.engine.ui.FGRemoteClojureResultCollector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Temp. for refactoring
 *
 * @author Denis Lebedev
 */
public class FGLegacyGlueTemplate extends FGTemplate
{
    private final FGRemoteClojureResultCollector preInitResultCollector_;
    private final FGRemoteAppContainer preInitAppContainer_;

    public FGLegacyGlueTemplate(String sourceCode, String containerNamespace, String containerVarName) throws Exception
    {
        super(sourceCode, containerNamespace, containerVarName);

        String dummySessionId = containerNamespace + "_" + containerVarName + "_preinit";

        FGLegacyCoreGlue.GlueModule glueModule = new FGLegacyCoreGlue.GlueModule(dummySessionId);
        Var containerVar = clojure.lang.RT.var(containerNamespace, containerVarName);
        Map<Object, Object> container = (Map<Object, Object>) containerVar.get();
        FGWebContainerWrapper.KeyCache keyCache = FGWebContainerWrapper.KeyCache.INSTANCE;
        Set<String> fontsWithMetricsAlreadyReceived = new HashSet<>();

        preInitResultCollector_ =
                new FGRemoteClojureResultCollector(FGAppContainer.DFLT_UNIT_SIZE_PX,
                        keyCache, glueModule, fontsWithMetricsAlreadyReceived);

        preInitAppContainer_ = new FGRemoteAppContainer(dummySessionId, container, preInitResultCollector_);
        preInitAppContainer_.initialize();
    }

    public final FGRemoteClojureResultCollector getPreInitResultCollector()
    {
        return preInitResultCollector_;
    }

    public final FGRemoteAppContainer getPreInitAppContainer()
    {
        return preInitAppContainer_;
    }
}
