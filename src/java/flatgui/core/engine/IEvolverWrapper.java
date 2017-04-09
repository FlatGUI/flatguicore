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
    GetPropertyDelegate getDelegateById(Integer getterId);

    GetPropertyDelegate getDelegateByIdAndPath(Integer getterId, List<Object> path);

    GetPropertyDelegate getDelegateByIdAndProperty(Integer getterId, Keyword property);

    GetPropertyDelegate getDelegateByIdPathAndProperty(Integer getterId, List<Object> path, Keyword property);
}
