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

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * @author Denis Lebedev
 */
public class ObjectMatrixTest
{
    @Test
    public void testMatrix()
    {
        ObjectMatrix<String> m = new ObjectMatrix<>();

        int[] code = m.addPath(Arrays.asList("x", "y"));
        Assert.assertArrayEquals(new int[]{0, 0}, code);

        code = m.addPath(Arrays.asList("x", "y", "z", "w"));
        Assert.assertArrayEquals(new int[]{0, 0, 0, 0}, code);

        code = m.addPath(Arrays.asList("x", "y"));
        Assert.assertArrayEquals(new int[]{0, 0}, code);

        code = m.addPath(Arrays.asList("x", "y", "a", "b"));
        Assert.assertArrayEquals(new int[]{0, 0, 1, 1}, code);

        code = m.addPath(Arrays.asList("x", "y", "a", "c"));
        Assert.assertArrayEquals(new int[]{0, 0, 1, 2}, code);
    }
}
