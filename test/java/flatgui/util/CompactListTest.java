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

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Lebedev
 */
public class CompactListTest
{
    @Test
    public void testSlots()
    {
        CompactList<String, ?> l = new CompactList<>(null);

        l.setSlot(4, 4002);
        l.setSlot(3, 5);
        l.setSlot(9, 4095);
        l.setSlot(2, 4095);
        l.setSlot(0, 123);
        l.setSlot(7, 5);
        l.setSlot(1, 111);
        l.setSlot(6, 2222);
        l.setSlot(5, 3333);
        l.setSlot(8, 4000);

        assertEquals(4002, l.getSlot(4));
        assertEquals(5, l.getSlot(3));
        assertEquals(4095, l.getSlot(9));
        assertEquals(4095, l.getSlot(2));
        assertEquals(123, l.getSlot(0));
        assertEquals(5, l.getSlot(7));
        assertEquals(111, l.getSlot(1));
        assertEquals(2222, l.getSlot(6));
        assertEquals(3333, l.getSlot(5));
        assertEquals(4000, l.getSlot(8));
    }

    @Test
    public void testSlotBitsErased()
    {
        CompactList<String, ?> l = new CompactList<>(null);

        l.setSlot(0, 1);
        l.setSlot(1, 2);
        l.setSlot(0, 2);
        l.setSlot(1, 4);

        assertEquals(2, l.getSlot(0));
        assertEquals(4, l.getSlot(1));
    }

    @Test
    public void testList1()
    {
        ObjectMatrix<String> m = new ObjectMatrix<>();
        int[] indices = m.addPath(Arrays.asList("x", "y", "z"));
        List<String> l = new CompactList<>(m, indices);
        assertEquals(3, l.size());
        assertEquals("x", l.get(0));
        assertEquals("y", l.get(1));
        assertEquals("z", l.get(2));
        StringBuilder sb = new StringBuilder();
        l.forEach(sb::append);
        assertEquals("xyz", sb.toString());
        l.remove(2);
        assertEquals(2, l.size());
        assertEquals("x", l.get(0));
        assertEquals("y", l.get(1));
        sb = new StringBuilder();
        l.forEach(sb::append);
        assertEquals("xy", sb.toString());
    }

    @Test
    public void testListCopy()
    {
        ObjectMatrix<String> m = new ObjectMatrix<>();
        int[] indices = m.addPath(Arrays.asList("x", "y", "z"));
        List<String> l = new CompactList<>(m, indices);

        List<String> l2 = new CompactList<>(m, l);

        assertEquals(l, l2);

        assertEquals(3, l2.size());
        assertEquals("x", l2.get(0));
        assertEquals("y", l2.get(1));
        assertEquals("z", l2.get(2));
        StringBuilder sb = new StringBuilder();
        l2.forEach(sb::append);
        assertEquals("xyz", sb.toString());

        List<String> al = Arrays.asList("x", "y", "w");

        List<String> l3 = new CompactList<>(m, al);

        assertEquals(3, l3.size());
        assertEquals("x", l3.get(0));
        assertEquals("y", l3.get(1));
        assertEquals("w", l3.get(2));
        sb = new StringBuilder();
        l3.forEach(sb::append);
        assertEquals("xyw", sb.toString());

        assertEquals("z", m.get(2, 0));
        assertEquals("w", m.get(2, 1));
    }
}
