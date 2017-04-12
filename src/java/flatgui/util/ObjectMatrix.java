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
import java.util.Objects;
import java.util.RandomAccess;

/**
 * @author Denis Lebedev
 */
public class ObjectMatrix<T> implements RandomAccess, IObjectListCoder<T>, IMatrix<T>
{
    private static final int ROW_CAPACITY = 10;
    private static final int COL_CAPACITY = 4096;

    private final T[][] data_;
    private final int[] rowSizes_;

    public ObjectMatrix()
    {
        data_ = (T[][]) new Object[ROW_CAPACITY][COL_CAPACITY];
        rowSizes_ = new int[ROW_CAPACITY];
    }

    @Override
    public int[] addPath(List<T> path)
    {
        int pathSize = path.size();
        int[] indices = new int[pathSize];
        for (int row=0; row<pathSize; row++)
        {
            T elem = path.get(row);
            int ei = indexOfInRow(elem, row);
            if (ei == -1)
            {
                ei = addToRow(elem, row);
            }
            indices[row] = ei;
        }
        return indices;
    }

    public T get(int row, int col)
    {
        return data_[row][col];
    }

    // Private

    int indexOfInRow(T elem, int row)
    {
        for (int i=0; i<rowSizes_[row]; i++)
        {
            if (Objects.equals(elem, data_[row][i]))
            {
                return i;
            }
        }
        return -1;
    }

    int addToRow(T elem, int row)
    {
        int index = rowSizes_[row];
        data_[row][index] = elem;
        rowSizes_[row]++;
        return index;
    }
}
