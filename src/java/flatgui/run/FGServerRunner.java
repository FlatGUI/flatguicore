/*
 * Copyright Denys Lebediev
 */
package flatgui.run;

import flatgui.core.IFGTemplate;
import flatgui.core.engine.remote.FGLegacyGlueTemplate;
import flatgui.core.engine.ui.FGRemoteAppContainer;
import flatgui.core.websocket.FGAppServer;

import java.io.InputStream;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * @author Denis Lebedev
 */
public class FGServerRunner
{
    public static void runApplication(String containerNs, String containerVarName, int port, Consumer<FGRemoteAppContainer> instanceConsumer)
    {
        InputStream compoundSampleIs = FGServerRunner.class.getClassLoader().getResourceAsStream(containerNsToSrcFileName(containerNs));
        String compoundSampleSourceCode = new Scanner(compoundSampleIs).useDelimiter("\\Z").next();

        try
        {
            IFGTemplate compondSampleTemplate = new FGLegacyGlueTemplate(compoundSampleSourceCode, containerNs, containerVarName);
            FGAppServer server = new FGAppServer(compondSampleTemplate, port, FGAppServer.DEFAULT_MAPPING, null, instanceConsumer);
            server.start();
            server.join();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static String containerNsToSrcFileName(String containerNs)
    {
        return containerNs.replace(".", "/")+".clj";
    }
}
