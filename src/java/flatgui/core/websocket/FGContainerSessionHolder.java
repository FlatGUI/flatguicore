/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package flatgui.core.websocket;

import flatgui.core.*;
import flatgui.core.engine.remote.FGLegacyCoreGlue;
import flatgui.core.engine.remote.FGLegacyGlueTemplate;
import flatgui.core.engine.ui.FGRemoteAppContainer;
import flatgui.core.engine.ui.FGRemoteClojureResultCollector;

import java.awt.event.ActionListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author Denis Lebedev
 */
class FGContainerSessionHolder
{
    private static final String SPECIAL_CHARS = ".:/=";

    private static final int MEGABYTE = 1024 * 1024;
    private static final int SECOND = 1000;

    private static final long LONG_IDLE_SESSION_TIMEOUT = 15 * 60 * SECOND;
    private static final long SHORT_IDLE_SESSION_TIMEOUT = 30 * SECOND;

    private static final double CANNOT_ACCEPT_MEMORY_RATIO = 0.9d;
    private static final double VERY_LOW_MEMORY_RATIO = 0.75d;
    private static final double LOW_MEMORY_RATIO = 0.6d;

    private final IFGContainerHost<FGContainerSession> sessionHost_;
    private final Map<Object, FGContainerSession> sessionMap_;

    private int sessionNumWhenCannotStateLastOccured_ = -1;

    enum HeapMemoryState {Ok, Low, VeryLow, CannotAccept}

    FGContainerSessionHolder(IFGContainerHost<FGContainerSession> sessionHost)
    {
        sessionHost_ = sessionHost;
        sessionMap_ = new ConcurrentHashMap<>();

        Timer longIdleTimer = new Timer("FlatGUI web container long idle session cleaner", true);
        longIdleTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                synchronized (FGContainerSessionHolder.this)
                {
                    cleanIdleSessions(LONG_IDLE_SESSION_TIMEOUT, "long cleaner");
                }
            }
        }, LONG_IDLE_SESSION_TIMEOUT, LONG_IDLE_SESSION_TIMEOUT);

        Timer shortIdleTimer = new Timer("FlatGUI web container short idle session cleaner", true);
        shortIdleTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                synchronized (FGContainerSessionHolder.this)
                {
                    HeapMemoryState memoryState = getHeapMemoryState(false);
                    if (memoryState != HeapMemoryState.Ok)
                    {
                        cleanIdleSessions(SHORT_IDLE_SESSION_TIMEOUT, "short cleaner");
                    }
                }
            }
        }, SHORT_IDLE_SESSION_TIMEOUT, SHORT_IDLE_SESSION_TIMEOUT);
    }

    private void cleanIdleSessions(long timeout, String urgencyComment)
    {
        int sessionCount = sessionMap_.size();
        sessionMap_.entrySet().removeIf(e -> e.getValue().isIdle(timeout));
        int newSessionCount = sessionMap_.size();
        if (newSessionCount != sessionCount)
        {
            FGAppServer.getFGLogger().info(toString() +
                    " cleaned sessions ("+ urgencyComment +"). Before: " + sessionCount + "; after: " + newSessionCount + ".");
        }
    }

    FGContainerSession getSession(IFGTemplate template, InetAddress remoteAddress,
                                  List<byte[]> initialFontMetricsTransmissions,
                                  Set<String> fontCollector,
                                  ActionListener unsolicitedEvolveCallback)
    {

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long maxMemory = heapUsage.getMax() / MEGABYTE;
        long usedMemory = heapUsage.getUsed() / MEGABYTE;
        FGAppServer.getFGLogger().info("[creating session] " + usedMemory + " of " + maxMemory + " MB of heap used, " +
            sessionMap_.size() + " sessions");

        Object sessionId = getSessionId(template, remoteAddress);

        // TODO always create new since font metrics may change? -- re-request metrics?

        FGContainerSession s = sessionMap_.computeIfAbsent(
                sessionId,
                k -> {
                    if (template instanceof FGLegacyGlueTemplate)
                    {
// TODO this was creating new instance, probably remove
//                        Var containerVar = clojure.lang.RT.var(template.getContainerNamespace(), template.getContainerVarName());
//                        Map<Object, Object> container = (Map<Object, Object>) containerVar.get();
//
//                        FGLegacyCoreGlue.GlueModule glueModule = new FGLegacyCoreGlue.GlueModule(sessionId.toString());
//
//                        FGWebContainerWrapper.KeyCache keyCache = FGWebContainerWrapper.KeyCache.INSTANCE;
//                        Set<String> fontsWithMetricsAlreadyReceived = new HashSet<>();
//
//                        FGRemoteClojureResultCollector resultCollector =
//                                new FGRemoteClojureResultCollector(FGAppContainer.DFLT_UNIT_SIZE_PX,
//                                        keyCache, glueModule, fontsWithMetricsAlreadyReceived);
//
//                        FGRemoteAppContainer fgContainer = new FGRemoteAppContainer(sessionId.toString(), container, resultCollector);

                        FGLegacyCoreGlue.GlueModule glueModule = new FGLegacyCoreGlue.GlueModule(sessionId.toString());
                        Set<String> fontsWithMetricsAlreadyReceived = new HashSet<>();
                        FGRemoteClojureResultCollector resultCollector =
                                new FGRemoteClojureResultCollector(((FGLegacyGlueTemplate) template).getPreInitResultCollector(),
                                        glueModule, fontsWithMetricsAlreadyReceived);
                        FGRemoteAppContainer fgContainer = new FGRemoteAppContainer(sessionId.toString(),
                            ((FGLegacyGlueTemplate) template).getPreInitAppContainer(), resultCollector, unsolicitedEvolveCallback);

                        FGLegacyCoreGlue glueContainer = new FGLegacyCoreGlue(fgContainer, glueModule);
                        //glueContainer.initialize();
                        FGWebInteropUtil interop = fgContainer.getInteropUtil();
                        initialFontMetricsTransmissions.forEach(t -> fontCollector.add(interop.setMetricsTransmission(t)));

                        FGContainerSession session = sessionHost_.hostContainer(glueContainer, fontsWithMetricsAlreadyReceived);
                        return session;
                    }
                    else
                    {
                        FGWebInteropUtil interop = new FGWebInteropUtil(IFGContainer.UNIT_SIZE_PX);
                        initialFontMetricsTransmissions.forEach(t -> fontCollector.add(interop.setMetricsTransmission(t)));
                        FGContainer container = new FGContainer(template, sessionId.toString(), interop);
                        Set<String> fontsWithMetricsAlreadyReceived = new HashSet<>();
                        return sessionHost_.hostContainer(container, fontsWithMetricsAlreadyReceived);
                    }
                });

        FGAppServer.getFGLogger().debug(toString() + " state:");
        FGAppServer.getFGLogger().debug(sessionMap_.toString());
        FGAppServer.getFGLogger().debug(toString() +
            " returning for remoteAddress=" + remoteAddress + " session: " + s);

        return s;
    }

    void stopSession(Object sessionId)
    {
        HeapMemoryState memoryState = getHeapMemoryState(true);
        if (memoryState == HeapMemoryState.VeryLow || memoryState == HeapMemoryState.CannotAccept)
        {
            FGAppServer.getFGLogger().info("Killing stopped session '" + sessionId +"' because memory state is: " + memoryState);
            sessionMap_.remove(sessionId);
        }
    }

    synchronized long getActiveOrIdleSessionCount()
    {
        return sessionMap_.size();
    }

    synchronized long getActiveSessionCount()
    {
        return sessionMap_.values().stream().filter(FGContainerSession::isActive).count();
    }

    synchronized void forEachActiveSession(Consumer<FGContainerSession> sessionConsumer)
    {
        sessionMap_.values().forEach(s -> {
            synchronized (s.getContainerLock())
            {
                if (s.isActive())
                {
                    sessionConsumer.accept(s);
                }
            }
        });
    }

    synchronized void forEachActiveSession(BiConsumer<Object, FGContainerSession> sessionConsumer)
    {
        sessionMap_.entrySet().forEach(e -> {
            synchronized (e.getValue().getContainerLock())
            {
                if (e.getValue().isActive())
                {
                    sessionConsumer.accept(e.getKey(), e.getValue());
                }
            }
        });
    }

    synchronized <R> Stream<R> mapSessions(Function<FGContainerSession, R> sessionProcessor)
    {
        return sessionMap_.values().stream().map(sessionProcessor);
    }

    private synchronized HeapMemoryState getHeapMemoryState(boolean logUsage)
    {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double maxMemory = heapUsage.getMax();
        double usedMemory = heapUsage.getUsed();
        double ratio = usedMemory / maxMemory;
        if (logUsage)
        {
            FGAppServer.getFGLogger().info("Memory state: " + ((int) (usedMemory / MEGABYTE)) + " of " + ((int) (maxMemory / MEGABYTE)) + " MB of heap used, ratio = " + ratio);
        }
        if (ratio > CANNOT_ACCEPT_MEMORY_RATIO)
        {
            if (sessionNumWhenCannotStateLastOccured_ == -1)
            {
                sessionNumWhenCannotStateLastOccured_ = sessionMap_.size();
            }
            return HeapMemoryState.CannotAccept;
        }
        else if (ratio > VERY_LOW_MEMORY_RATIO)
        {
            sessionNumWhenCannotStateLastOccured_ = -1;
            return HeapMemoryState.VeryLow;
        }
        else if (ratio > LOW_MEMORY_RATIO)
        {
            sessionNumWhenCannotStateLastOccured_ = -1;
            return HeapMemoryState.Low;
        }
        else
        {
            sessionNumWhenCannotStateLastOccured_ = -1;
            return HeapMemoryState.Ok;
        }
    }

    synchronized boolean memoryStateAllowsAcceptingNewSessions()
    {
        FGContainerSessionHolder.HeapMemoryState memoryState = getHeapMemoryState(true);
        if (memoryState == FGContainerSessionHolder.HeapMemoryState.CannotAccept)
        {
            return sessionMap_.size() < sessionNumWhenCannotStateLastOccured_;
        }
        else
        {
            return true;
        }
    }

    // TODO turn off counter - string pool is broken
    private static long counter_ = 0;
    private static Object getSessionId(IFGTemplate template, InetAddress remoteAddress)
    {
        String name = template.getContainerNamespace() + "_" +
                template.getContainerVarName() + "_" +
                remoteAddress.getHostAddress().toString() + String.valueOf(counter_);
        counter_++;
        for (int i=0; i<SPECIAL_CHARS.length(); i++)
        {
            name = name.replace(SPECIAL_CHARS.charAt(i), '_');
        }
        return name;
    }
}
