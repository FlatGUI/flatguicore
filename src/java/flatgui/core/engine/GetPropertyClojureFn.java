/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import clojure.lang.AFunction;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Denis Lebedev
 */
public abstract class GetPropertyClojureFn extends AFunction
{
    private static final AtomicInteger counter_ = new AtomicInteger(0);

    protected final int getterId_;

    protected static final ThreadLocal<IEvolverWrapper> currentEvolverWrapper_ = new ThreadLocal<>();

    public GetPropertyClojureFn()
    {
        getterId_ = getNewId();
    }

    @Override
    public Object invoke(Object path, Object property)
    {
        GetPropertyDelegate delegate = getDelegate(path, property);
        if (!delegate.isLinked())
        {
            delegate.link((List<Object>) path, property);
        }
        return delegate.getProperty();
    }

    @Override
    public Object invoke(Object component, Object path, Object property)
    {
        return invoke(path, property);
    }

    static void visit(IEvolverWrapper evolverWrapper)
    {
        currentEvolverWrapper_.set(evolverWrapper);
    }

    private static int getNewId()
    {
        return counter_.incrementAndGet();
    }

    protected abstract GetPropertyDelegate getDelegate(Object path, Object property);
}
