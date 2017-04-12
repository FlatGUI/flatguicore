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

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * @author Denis Lebedev
 */
public class CompactList<T, Data extends IObjectListCoder<T> & IMatrix<T>> extends AbstractList<T> implements RandomAccess
{                                                     // 0   1   2   3   4 | 6   7   8   9  10  Size
    private static final int[] DISTRIBUTION = new int[]{10, 13, 13, 14, 14, 12, 12, 12, 12, 12, 4};
    private static final int[] INDICES;
    private static final int[] SHIFTS;
    private static final int[] MASKS;
    private static final int SIZE_PLACE;
    private static final int LONG_CAPACITY;
    static
    {
        INDICES = new int[DISTRIBUTION.length];
        SHIFTS = new int[DISTRIBUTION.length];
        MASKS = new int[DISTRIBUTION.length];
        int totalBits = 0;
        int i;
        for (i=0; i<DISTRIBUTION.length; i++)
        {
            INDICES[i] = totalBits / Long.SIZE;
            SHIFTS[i] = totalBits % Long.SIZE;
            MASKS[i] = (1 << DISTRIBUTION[i])-1;
            totalBits += DISTRIBUTION[i];
        }
        LONG_CAPACITY = totalBits / Long.SIZE;
        SIZE_PLACE = DISTRIBUTION.length-1;
    }

    private final Data objectData_;
    private final long[] numData_;

    public CompactList(Data data)
    {
        objectData_ = data;
        numData_ = new long[LONG_CAPACITY];
    }

    public CompactList(Data data, int[] indices)
    {
        objectData_ = data;
        numData_ = new long[LONG_CAPACITY];
        addNumData(indices);
    }

    public CompactList(Data data, List<T> list)
    {
        if (list instanceof CompactList)
        {
            objectData_ = data;
            numData_ = new long[LONG_CAPACITY];
            for (int i=0; i<numData_.length; i++)
            {
                numData_[i] = ((CompactList) list).numData_[i];
            }
        }
        else
        {
            objectData_ = data;
            numData_ = new long[LONG_CAPACITY];
            int[] indices = data.addPath(list);
            addNumData(indices);
        }
    }

    @Override
    public T get(int index)
    {
        int col = getSlot(index);
        return objectData_.get(index, col);
    }

    @Override
    public int size()
    {
        return getSlot(SIZE_PLACE);
    }

    @Override
    public void forEach(Consumer<? super T> action)
    {
        int size = size();
        for (int i=0; i<size; i++)
        {
            action.accept(get(i));
        }
    }

    @Override
    public boolean add(T t)
    {
        int size = size();
        int mxindex = objectData_.add(size, t);
        setSlot(size, mxindex);
        setSlot(SIZE_PLACE, size+1);
        return true;
    }

    @Override
    public T remove(int index)
    {
        int size = size();
        if (index == size-1)
        {
            T elem = get(index);
            setSlot(SIZE_PLACE, size-1);
            return elem;
        }
        else
        {
            throw new UnsupportedOperationException("Cannot remove element at " +
                index + " index in the list of size " + size + ". Only last element removal is supported.");
        }
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sort(Comparator<? super T> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Spliterator<T> spliterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<T> stream()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<T> parallelStream()
    {
        throw new UnsupportedOperationException();
    }

    final int getSlot(int index)
    {
        int shift = SHIFTS[index];
        int ind = INDICES[index];
        return (int) ((numData_[ind] >> shift) & MASKS[index]);
    }

    final void setSlot(int index, int value)
    {
        int shift = SHIFTS[index];
        int ind = INDICES[index];
        numData_[ind] &= ~((long)(MASKS[index]) << shift);
        numData_[ind] |= ((long)(value & MASKS[index]) << shift);
    }

    private final void addNumData(int[] indices)
    {
        for (int i=0; i<indices.length; i++)
        {
            setSlot(i, indices[i]);
        }
        setSlot(SIZE_PLACE, indices.length);
    }
}
