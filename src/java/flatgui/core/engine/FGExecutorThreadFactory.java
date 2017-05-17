/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Denis Lebedev
 */
public class FGExecutorThreadFactory implements ThreadFactory
{
    private final ThreadGroup group_;
    private final AtomicInteger threadNumber_ = new AtomicInteger(1);
    private final String namePrefix_;

    FGExecutorThreadFactory(String namePrefix, String containerId)
    {
        SecurityManager s = System.getSecurityManager();
        group_ = s != null ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        namePrefix_ = namePrefix + containerId + " ";
    }

    public Thread newThread(Runnable r)
    {
        Thread t = new Thread(group_, r, namePrefix_ + threadNumber_.getAndIncrement());
        if (t.isDaemon())
            t.setDaemon(false);
        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}
