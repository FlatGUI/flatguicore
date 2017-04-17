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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

        l.add("w");
        assertEquals(3, l.size());
        assertEquals("x", l.get(0));
        assertEquals("y", l.get(1));
        assertEquals("w", l.get(2));
        sb = new StringBuilder();
        l.forEach(sb::append);
        assertEquals("xyw", sb.toString());
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

        l3.remove(l3.size()-1);
        l3.add("w");
        assertEquals("w", m.get(2, 1));
        l3.remove(l3.size()-1);
        l3.add("v");
        assertEquals("v", m.get(2, 2));
    }

    @Test
    public void testEquals()
    {
        ObjectMatrix<String> m = new ObjectMatrix<>();
        CompactList l1 = new CompactList(m);
        CompactList l2 = new CompactList(m);
        assertTrue(l1.equals(l2));

        List<String> check = new ArrayList<>();
        for (int i=0; i<CompactList.DISTRIBUTION.length-1; i++)
        {
            System.out.println("-DLTEMP- CompactListTest.testEquals i=" + i + " l1=" + l1 + " l2=" + l2);
            String s = String.valueOf(i);
            l1.add(s);
            assertTrue("Step "+i+" (not equals)", !l1.equals(l2));
            if (i<CompactList.DISTRIBUTION.length-2)
            {
                l2.add("t");
                assertTrue("Step " + i + " (not equals)", !l1.equals(l2));
                l2.add(s);
                assertTrue("Step " + i + " (not equals)", !l1.equals(l2));
                l2.remove(l2.size() - 1);
                l2.remove(l2.size() - 1);
            }
            l2.add(s);
            assertTrue("Step "+i+" (equals)", l1.equals(l2));
            check.add(s);
            assertTrue("Step "+i+" (l1==check)", l1.equals(check));
            assertTrue("Step "+i+" (l2==check)", l2.equals(check));
        }
    }

    @Test
    public void testEquals1()
    {
        ObjectMatrix<String> m = new ObjectMatrix<>();
        CompactList l1 = new CompactList(m);
        l1.add("x"); l1.add("y"); l1.add("z"); l1.add("w"); l1.add("v"); l1.add("a"); l1.add("b"); l1.add("c");
        CompactList l2 = new CompactList(m);
        l2.add("x"); l2.add("_"); l2.add("z"); l2.add("w"); l2.add("v"); l2.add("a"); l2.add("b"); l2.add("c");
        assertTrue(!l1.equals(l2));
        assertTrue(!l2.equals(l1));
        for (int i=0; i<7; i++) l2.remove(l2.size()-1);
        l2.add("y"); l2.add("z"); l2.add("w"); l2.add("v"); l2.add("a"); l2.add("b"); l2.add("_");
        assertTrue(!l1.equals(l2));
        assertTrue(!l2.equals(l1));
        l2.remove(l2.size()-1);
        l2.add("c");
        assertTrue(l1.equals(l2));
        assertTrue(l2.equals(l1));
    }

    @Test
    public void testSet()
    {
        ObjectMatrix<String> m = new ObjectMatrix<>();
        CompactList l1 = new CompactList(m);
        l1.add("x"); l1.add("y"); l1.add("z");

        CompactList l2 = new CompactList(m);
        l2.add("a"); l2.add("b"); l2.add("c");

        l2.set(1, "y");
        l2.set(2, "m");

        l1.set(2, "m");

        l2.set(0, "x");

        assertEquals("x", l1.get(0));
        assertEquals("y", l1.get(1));
        assertEquals("m", l1.get(2));

        assertEquals("x", l2.get(0));
        assertEquals("y", l2.get(1));
        assertEquals("m", l2.get(2));

        assertEquals(3, l1.size());
        assertEquals(3, l2.size());
        assertTrue(l1.equals(l2));

        l1.set(0, "n");
        assertEquals(3, l1.size());
        assertEquals(3, l2.size());
        assertTrue(!l1.equals(l2));

        assertEquals("n", l1.get(0));
        assertEquals("y", l1.get(1));
        assertEquals("m", l1.get(2));

        assertEquals("x", l2.get(0));
        assertEquals("y", l2.get(1));
        assertEquals("m", l2.get(2));
    }

    @Test
    public void testResizeAndDirtySlots()
    {
        ObjectMatrix<String> m = new ObjectMatrix<>();
        CompactList cls = new CompactList(m);
        cls.add("a"); cls.add("b"); cls.add("c");
        cls.remove(cls.size()-1);
        cls.remove(cls.size()-1);
        cls.remove(cls.size()-1);
        cls.add("x"); cls.add("y"); cls.add("z");

        assertEquals(1, cls.getSlot(0));
        assertEquals(1, cls.getSlot(1));
        assertEquals(1, cls.getSlot(2));

        cls.remove(cls.size()-1);

        assertEquals(1, cls.getSlot(0));
        assertEquals(1, cls.getSlot(1));
        assertEquals(0, cls.getSlot(2));

        CompactList cl1 = new CompactList(m, cls);
        CompactList cl2 = new CompactList(m, new ArrayList<>(cls));

        assertTrue(cl1.equals(cl2));
    }
}
