/*
 * Copyright Denys Lebediev
 */
package flatgui.util;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * @author Denis Lebedev
 */
public class GrowControlArrayList<T> extends AbstractList<T> implements RandomAccess
{
    private final GrowFunction growFn_;

    private int size_;
    private T[] data_;

    public GrowControlArrayList(int initialCapacity, GrowFunction growFn)
    {
        data_ = (T[]) new Object[initialCapacity];
        size_ = 0;
        growFn_ = growFn;
    }

    public GrowControlArrayList(List<T> source, GrowFunction growFn)
    {
        growFn_ = growFn;
        size_ = source.size();
        int initialCapacity = size_;
        data_ = (T[]) new Object[initialCapacity];
        if (source instanceof GrowControlArrayList)
        {
            System.arraycopy(((GrowControlArrayList) source).data_, 0, data_, 0, size_);
        }
        else
        {
            for (int i=0; i<size_; i++)
            {
                data_[i] = source.get(i);
            }
        }
    }

    @Override
    public T get(int index)
    {
        return data_[index];
    }

    @Override
    public int size()
    {
        return size_;
    }

    @Override
    public T set(int index, T element)
    {
        T oldElement = get(index);
        data_[index] = element;
        return oldElement;
    }

    @Override
    public boolean add(T element)
    {
        ensureCapacity(size_+1);

        data_[size_] = element;
        size_++;

        return true;
    }

    @Override
    public void clear()
    {
        // This implementation does not make all elements null here
        size_ = 0;
    }

    public final void ensureCapacity(int requestedCapacity)
    {
        if (requestedCapacity > getCapacity())
        {
            int newSize = growFn_.apply(getCapacity(), requestedCapacity-getCapacity());
            changeCapacity(newSize);
        }
    }

    public void trimCapacity()
    {
        if (getCapacity() > size_)
        {
            changeCapacity(size_);
        }
    }

    private void changeCapacity(int newSize)
    {
        T[] newData = (T[]) Array.newInstance(data_.getClass().getComponentType(), newSize);
        System.arraycopy(data_, 0, newData, 0, size_);
        data_ = newData;
    }

    int getCapacity()
    {
        return data_.length;
    }

    public interface GrowFunction
    {
        int apply(int oldCapacity, int increment);
    }

    public static class DefaultGrowControl implements GrowFunction
    {
        @Override
        public int apply(int oldCapacity, int increment)
        {
            int actualIncrement = Math.max(oldCapacity >> 1, increment);
            return oldCapacity + actualIncrement;
        }
    }
}
