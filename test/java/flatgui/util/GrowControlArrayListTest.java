/*
 * Copyright Denys Lebediev
 */
package flatgui.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Denis Lebedev
 */
public class GrowControlArrayListTest
{
    @Test
    public void testGrow1()
    {
        GrowAdd add2 = new GrowAdd(2);

        GrowControlArrayList<String> list = new GrowControlArrayList<>(3, add2);
        Assert.assertEquals(3, list.getCapacity());
        Assert.assertEquals(0, list.size());

        list.add("a");
        Assert.assertEquals(3, list.getCapacity());
        Assert.assertEquals(1, list.size());

        list.add("b");
        Assert.assertEquals(3, list.getCapacity());
        Assert.assertEquals(2, list.size());

        list.add("c");
        Assert.assertEquals(3, list.getCapacity());
        Assert.assertEquals(3, list.size());

        list.add("d");
        Assert.assertEquals(5, list.getCapacity());
        Assert.assertEquals(4, list.size());
        Assert.assertEquals(Arrays.asList(new GrowCallLog(3, 1)), add2.getLog());
        Assert.assertEquals(Arrays.asList("a", "b", "c", "d"), list);
    }

    @Test
    public void testGrow3()
    {
        GrowAdd add3 = new GrowAdd(3);

        GrowControlArrayList<String> list = new GrowControlArrayList<>(0, add3);
        Assert.assertEquals(0, list.getCapacity());
        Assert.assertEquals(0, list.size());

        list.add("a");
        Assert.assertEquals(3, list.getCapacity());
        Assert.assertEquals(1, list.size());

        list.add("b");
        Assert.assertEquals(3, list.getCapacity());
        Assert.assertEquals(2, list.size());

        list.add("c");
        Assert.assertEquals(3, list.getCapacity());
        Assert.assertEquals(3, list.size());

        list.add("d");
        Assert.assertEquals(6, list.getCapacity());
        Assert.assertEquals(4, list.size());

        list.add("e");
        Assert.assertEquals(6, list.getCapacity());
        Assert.assertEquals(5, list.size());

        list.add("f");
        Assert.assertEquals(6, list.getCapacity());
        Assert.assertEquals(6, list.size());

        Assert.assertEquals(Arrays.asList(new GrowCallLog(0, 1), new GrowCallLog(3, 1)), add3.getLog());
        Assert.assertEquals(Arrays.asList("a", "b", "c", "d", "e", "f"), list);
    }

    @Test
    public void testClear()
    {
        GrowControlArrayList<String> list = new GrowControlArrayList<>(2, null);
        Assert.assertEquals(2, list.getCapacity());
        Assert.assertEquals(0, list.size());
        list.add("a");
        list.add("b");
        list.clear();
        Assert.assertEquals(0, list.size());
    }

    private static class GrowCallLog
    {
        private int oldSize_;
        private int increment_;

        public GrowCallLog(int oldSize, int increment)
        {
            oldSize_ = oldSize;
            increment_ = increment;
        }

        int getOldSize()
        {
            return oldSize_;
        }

        int getIncrement()
        {
            return increment_;
        }

        @Override
        public String toString()
        {
            return "{" +
                    "oldSize_=" + oldSize_ +
                    ", increment_=" + increment_ +
                    '}';
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GrowCallLog that = (GrowCallLog) o;
            if (oldSize_ != that.oldSize_) return false;
            return increment_ == that.increment_;
        }
    }

    private static class GrowAdd implements GrowControlArrayList.GrowFunction
    {
        private final int inc_;
        private List<GrowCallLog> log_;

        GrowAdd(int inc)
        {
            inc_ = inc;
            log_ = new ArrayList<>();
        }

        List<GrowCallLog> getLog()
        {
            return log_;
        }

        void clearLog()
        {
            log_.clear();
        }

        @Override
        public int apply(int oldCapacity, int increment)
        {
            int actualIncrement = Math.max(inc_, increment);
            log_.add(new GrowCallLog(oldCapacity, increment));
            return oldCapacity + actualIncrement;
        }
    }
}
