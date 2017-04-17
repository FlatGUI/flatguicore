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

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Lebedev
 */
public class ObjectTreeTest
{
    @Test
    public void testRoot1()
    {
        ObjectTree<String> t = new ObjectTree<>("x");
        int[] code = t.addPath(Arrays.asList("x", "y"));
        Assert.assertArrayEquals(new int[]{0, 0}, code);
        assertEquals("x", t.getRoot().getObject());
        assertEquals("y", t.getRoot().getChildAt(0).getObject());

        code = t.addPath(Arrays.asList("x", "y", "z", "w"));
        Assert.assertArrayEquals(new int[]{0, 0, 0, 0}, code);
        assertEquals(1, t.getRoot().getChildCount());
        assertEquals(1, t.getRoot().getChildAt(0).getChildCount());
        assertEquals(1, t.getRoot().getChildAt(0).getChildAt(0).getChildCount());
        assertEquals(0, t.getRoot().getChildAt(0).getChildAt(0).getChildAt(0).getChildCount());
        assertEquals("x", t.getRoot().getObject());
        assertEquals("y", t.getRoot().getChildAt(0).getObject());
        assertEquals("z", t.getRoot().getChildAt(0).getChildAt(0).getObject());
        assertEquals("w", t.getRoot().getChildAt(0).getChildAt(0).getChildAt(0).getObject());

        code = t.addPath(Arrays.asList("x", "y"));
        Assert.assertArrayEquals(new int[]{0, 0}, code);
        assertEquals(1, t.getRoot().getChildCount());
        assertEquals(1, t.getRoot().getChildAt(0).getChildCount());
        assertEquals(1, t.getRoot().getChildAt(0).getChildAt(0).getChildCount());
        assertEquals(0, t.getRoot().getChildAt(0).getChildAt(0).getChildAt(0).getChildCount());
        assertEquals("x", t.getRoot().getObject());
        assertEquals("y", t.getRoot().getChildAt(0).getObject());
        assertEquals("z", t.getRoot().getChildAt(0).getChildAt(0).getObject());
        assertEquals("w", t.getRoot().getChildAt(0).getChildAt(0).getChildAt(0).getObject());

        code = t.addPath(Arrays.asList("x", "y", "a", "b"));
        Assert.assertArrayEquals(new int[]{0, 0, 1, 0}, code);
        assertEquals(1, t.getRoot().getChildCount());//x
        assertEquals(2, t.getRoot().getChildAt(0).getChildCount());//y
        assertEquals(1, t.getRoot().getChildAt(0).getChildAt(0).getChildCount());//z
        assertEquals(0, t.getRoot().getChildAt(0).getChildAt(0).getChildAt(0).getChildCount());//w
        assertEquals(1, t.getRoot().getChildAt(0).getChildAt(1).getChildCount());//a
        assertEquals(0, t.getRoot().getChildAt(0).getChildAt(1).getChildAt(0).getChildCount());//b
        assertEquals("x", t.getRoot().getObject());
        assertEquals("y", t.getRoot().getChildAt(0).getObject());
        assertEquals("z", t.getRoot().getChildAt(0).getChildAt(0).getObject());
        assertEquals("a", t.getRoot().getChildAt(0).getChildAt(1).getObject());
        assertEquals("w", t.getRoot().getChildAt(0).getChildAt(0).getChildAt(0).getObject());
        assertEquals("b", t.getRoot().getChildAt(0).getChildAt(1).getChildAt(0).getObject());

        code = t.addPath(Arrays.asList("x", "y", "a", "c"));
        Assert.assertArrayEquals(new int[]{0, 0, 1, 1}, code);
        assertEquals(1, t.getRoot().getChildCount());//x
        assertEquals(2, t.getRoot().getChildAt(0).getChildCount());//y
        assertEquals(1, t.getRoot().getChildAt(0).getChildAt(0).getChildCount());//z
        assertEquals(0, t.getRoot().getChildAt(0).getChildAt(0).getChildAt(0).getChildCount());//w
        assertEquals(2, t.getRoot().getChildAt(0).getChildAt(1).getChildCount());//a
        assertEquals(0, t.getRoot().getChildAt(0).getChildAt(1).getChildAt(0).getChildCount());//b
        assertEquals(0, t.getRoot().getChildAt(0).getChildAt(1).getChildAt(1).getChildCount());//c
        assertEquals("x", t.getRoot().getObject());
        assertEquals("y", t.getRoot().getChildAt(0).getObject());
        assertEquals("z", t.getRoot().getChildAt(0).getChildAt(0).getObject());
        assertEquals("a", t.getRoot().getChildAt(0).getChildAt(1).getObject());
        assertEquals("w", t.getRoot().getChildAt(0).getChildAt(0).getChildAt(0).getObject());
        assertEquals("b", t.getRoot().getChildAt(0).getChildAt(1).getChildAt(0).getObject());
        assertEquals("c", t.getRoot().getChildAt(0).getChildAt(1).getChildAt(1).getObject());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRoot2()
    {
        ObjectTree<String> t = new ObjectTree<>("u");
        t.addPath(Arrays.asList("x", "y"));
    }
}
