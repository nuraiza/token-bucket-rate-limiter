package org.example;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test scheduler with no threads and no clock.
 * Periodic tasks only run when the test calls {@link #tick()}, so tests are instant and deterministic.
 */
final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {

    private final List<ManualTask> tasks = new CopyOnWriteArrayList<>();
    private volatile boolean shutdown;

    /** Runs every active periodic task once, as if one period had passed. */
    void tick() {
        for (ManualTask task : tasks) {
            if (!task.isCancelled()) {
                task.command.run();
            }
        }
    }

    void tick(int times) {
        for (int i = 0; i < times; i++) {
            tick();
        }
    }

    long activeTaskCount() {
        return tasks.stream().filter(task -> !task.isCancelled()).count();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        ManualTask task = new ManualTask(command);
        tasks.add(task);
        return task;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduleAtFixedRate(command, initialDelay, delay, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("not needed by these tests");
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("not needed by these tests");
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    private static final class ManualTask implements ScheduledFuture<Void> {

        private final Runnable command;
        private volatile boolean cancelled;

        private ManualTask(Runnable command) {
            this.command = command;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Void get() {
            throw new UnsupportedOperationException("periodic task never completes");
        }

        @Override
        public Void get(long timeout, TimeUnit unit) {
            return get();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
