package org.aouessar.core.stream;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * -----------------------
 * Thread factory (nice names)
 * -----------------------
 */
public final class NamedThreadFactory implements ThreadFactory {

    private final String base;
    private final ThreadFactory delegate = Executors.defaultThreadFactory();
    private final AtomicInteger idx = new AtomicInteger();

    public NamedThreadFactory(String base) {
        this.base = base;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread t = delegate.newThread(r);
        t.setName(base + "-" + idx.incrementAndGet());
        t.setDaemon(true);
        return t;
    }
}