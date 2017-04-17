/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import clojure.lang.Keyword;

import java.util.List;

/**
 * @author Denis Lebedev
 */
public interface IEvolverWrapper
{
    GetPropertyDelegate getDelegateById(int getterId);

    GetPropertyDelegate getDelegateByIdAndPath(int getterId, List<Object> path);

    GetPropertyDelegate getDelegateByIdAndProperty(int getterId, Keyword property);

    GetPropertyDelegate getDelegateByIdPathAndProperty(int getterId, List<Object> path, Keyword property);
}
