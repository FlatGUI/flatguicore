/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine;

import java.util.List;

/**
 * One instance per evolver use case (unlike GetPropertyClojureFn that has one instance per evolver function)
 * and per application/thread.
 * Keeps the index of referred property which GetPropertyClojureFn cannot keep because it's different depending
 * on where evolver is used.
 */
public class GetPropertyDelegate
{
    private boolean linked_;
    private Integer accessedPropertyIndex_;

    private final List<Object> evolvedComponentPath_;
    private final Container.IEvolverAccess evolverAccess_;

    public GetPropertyDelegate(List<Object> evolvedComponentPath, Container.IEvolverAccess evolverAccess)
    {
        evolvedComponentPath_ = evolvedComponentPath;
        evolverAccess_ = evolverAccess;
    }

    Object getProperty()
    {
        return evolverAccess_.getPropertyValue(accessedPropertyIndex_);
    }

    boolean isLinked()
    {
        return linked_;
    }

    void link(List<Object> accessedPropertyRelPath, Object accessedProperty)
    {
        List<Object> accessedPropertyAbsPath = ClojureContainerParser.buildAbsPath(evolvedComponentPath_, accessedPropertyRelPath);
        accessedPropertyAbsPath.add(accessedProperty);
        accessedPropertyIndex_ = evolverAccess_.indexOfPath(accessedPropertyAbsPath);
        Container.log(evolvedComponentPath_ + " linked " + accessedPropertyAbsPath + " -> " + accessedPropertyIndex_ + " Delegate: " + this);
        if (accessedPropertyIndex_ != null)
        {
            // accessedPropertyIndex_ may not be resolved if referenced component does not exist. Referenced component
            // may be added later so keeping linked_ == false allows giving it another try
            linked_ = true;
        }
    }

    void unlink()
    {
        linked_ = false;
    }
}
