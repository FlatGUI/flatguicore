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

/**
 * @author Denis Lebedev
 */
public class ObjectTree<T> implements IObjectListCoder<T>
{
    private static final int ROOT_CAPACITY = 8;
    private static final int NODE_CAPACITY = 12;

    private Node<T> root_;

    public ObjectTree(T rootObject)
    {
        root_ = new Node<>(rootObject, ROOT_CAPACITY);
    }

    @Override
    public int[] addPath(List<T> path)
    {
        if (!Objects.equals(root_.getObject(), path.get(0)))
        {
            throw new IllegalArgumentException("Cannot add path that starts with '"
                + path.get(0) + "' to the tree where the root is '" + root_.getObject() + "'");
        }

        int[] indices = new int[path.size()];
        indices[0] = 0;

        Node<T> n = root_;
        for (int i=1; i<path.size(); i++)
        {
            T o = path.get(i);
            int ci = n.getChildIndexByObject(o);
            if (ci >= 0)
            {
                n = n.getChildAt(ci);
            }
            else
            {
                Node newN = new Node<>(o, NODE_CAPACITY);
                ci = n.addChild(newN);
                n = newN;
            }
            indices[i] = ci;
        }

        return indices;
    }

    public final Node<T> getRoot()
    {
        return root_;
    }

    // Private


    // Inner classes

    public static class Node<T>
    {
        private T object_;

        private Node<T>[] children_;
        private int size_;

        public Node(T object, int capacity)
        {
            object_ = object;
            children_ = new Node[capacity];
        }

        public T getObject()
        {
            return object_;
        }

        public int addChild(Node<T> c)
        {
            int i = size_;
            children_[i] = c;
            size_++;
            return i;
        }

        public Node<T> getChildAt(int i)
        {
            return children_[i];
        }

        public Node<T> getChildByObject(T o)
        {
            for (int i=0; i<size_; i++)
            {
                Node<T> c = children_[i];
                if (Objects.equals(c.getObject(), o))
                {
                    return c;
                }
            }
            return null;
        }

        public int getChildIndexByObject(T o)
        {
            for (int i=0; i<size_; i++)
            {
                Node<T> c = children_[i];
                if (Objects.equals(c.getObject(), o))
                {
                    return i;
                }
            }
            return -1;
        }

        public int getChildCount()
        {
            return size_;
        }
    }
}
