/*
 * Copyright (c) 2017 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package flatgui.util;

import java.util.List;

/**
 * @author Denis Lebedev
 */
public interface IObjectListCoder<T>
{
    /**
     * Encodes incoming list so that each element receives its index
     * @param path
     * @return array of path element indices
     */
    int[] addPath(List<T> path);
}
