package com.brunobonacci.mulog.core;
import clojure.lang.IDeref;
import java.lang.ThreadLocal;

/**
 * A ThreadLocal extension that works nicely with Clojure.
 *
 * I support initialisation of a value and the IDeref interface
 * which allows for the use of `@tl-var`.
 */
public class ClojureThreadLocal extends ThreadLocal implements IDeref {

    final Object initialValue;

    public ClojureThreadLocal() {
        this.initialValue = null;
    }

    public ClojureThreadLocal(Object initialValue) {
        this.initialValue = initialValue;
    }

    @Override
    public Object initialValue() {
        return this.initialValue;
    }

    @Override
    public Object deref() {
        return this.get();
    }

}
