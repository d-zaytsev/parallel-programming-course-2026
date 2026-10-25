package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    enum ScheduledThreadState {
        WORKING,
        WAITING,
        WAITING_LOCK,
        WAITING_CONDITION,
        FINISHED
    }

    final class ScheduledTask {
        /** Буква потока: A, B, … в порядке spawn. */
        final char id;
        final Runnable body;
        final Semaphore sem;
        final Scheduler mySched;

        Thread thread;
        ScheduledThreadState state;
        BooleanSupplier condition;
        boolean touched;

        ScheduledTask(char id, Runnable body, Scheduler sched) {
            this.id = id;
            this.mySched = sched;
            this.touched = true;
            this.condition = () -> false;
            this.state = ScheduledThreadState.WAITING;
            this.body = body;
            this.sem = new Semaphore(0);
        }
    }

    boolean logging;
    final List<String> log = new ArrayList<>();

    String deadlock;

    final StringBuilder trace;
    final List<ScheduledTask> runnables = new ArrayList<>();
    final List<SchedLock> locks = new ArrayList<>();
    final Semaphore sem = new Semaphore(0);

    private static final ThreadLocal<ScheduledTask> CURRENT = new ThreadLocal<>();

    public Scheduler() {
        this.trace = new StringBuilder();
    }

    public void spawn(Runnable body) {
        runnables.add(new ScheduledTask((char) ('A' + runnables.size()), body, this));
    }

    /**
     * Запустить потоки и вести их по одному: на каждой паузе спросить
     * strategy.pick(enabled, last).
     */
    public void run(Strategy strategy) {
        for (ScheduledTask t : runnables) {
            t.thread = new Thread(() -> {
                CURRENT.set(t);
                try {
                    t.body.run();
                } finally {
                    t.state = ScheduledThreadState.FINISHED;
                    sem.release();
                }
            });
            t.thread.setDaemon(true);
            t.thread.start();
            sem.acquireUninterruptibly();
        }

        int last = -1;

        do {
            List<Integer> enabled = new ArrayList<>();
            List<Integer> liveThreads = new ArrayList<>();

            for (int i = 0; i < runnables.size(); i++) {
                var t = this.runnables.get(i);

                if (t.state == ScheduledThreadState.FINISHED)
                    continue;
                liveThreads.add(i);

                switch (t.state) {
                    case WAITING_LOCK:
                    case WAITING_CONDITION:
                        if (t.condition.getAsBoolean())
                            enabled.add(i);
                        break;
                    case FINISHED:
                        break;
                    default:
                        enabled.add(i);
                        break;
                }
            }

            if (liveThreads.isEmpty())
                break;

            if (enabled.isEmpty()) {
                this.deadlock = buildHeldString();
                for (ScheduledTask t : runnables)
                    if (t.state != ScheduledThreadState.FINISHED)
                        t.sem.release();
                break;
            }

            last = strategy.pick(enabled, last);
            var task = this.runnables.get(last);
            this.trace.append(task.id);

            task.state = ScheduledThreadState.WORKING;
            task.sem.release();
            sem.acquireUninterruptibly();
        } while (true);

        for (ScheduledTask t : runnables) {
            try {
                t.thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private String buildHeldString() {
        var held = new ArrayList<String>();
        for (SchedLock l : locks)
            if (l.owner != null)
                held.add(l.name + ":" + l.owner.id);
        return "held={" + String.join(", ", held) + "}";
    }

    public ScheduledTask getCurrentTask() {
        return CURRENT.get();
    }

    public String traceString() {
        return this.trace.toString();
    }

    public static void point() {
        var current = CURRENT.get();
        log("window");
        current.touched = true;
    }

    static void yield(ScheduledThreadState newState, Boolean force) {
        var cur = CURRENT.get();

        if (cur.touched || force) {
            cur.state = newState != null ? newState : ScheduledThreadState.WAITING;
            cur.touched = false;
            cur.mySched.sem.release();
            cur.sem.acquireUninterruptibly();
        }

        if (cur.mySched.deadlock != null)
            throw new RuntimeException();

        cur.touched = true;
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        var cur = CURRENT.get();
        cur.condition = cond;
        if (!cond.getAsBoolean()) {
            Scheduler.yield(ScheduledThreadState.WAITING_CONDITION, true);
        }
        log("await done");
    }

    public static void await_lock(BooleanSupplier lockCond) {
        var cur = CURRENT.get();
        cur.condition = lockCond;
        Scheduler.yield(ScheduledThreadState.WAITING_LOCK, true);
    }

    public static void log(String msg) {
        var current = CURRENT.get();
        if (current.mySched.logging) {
            current.mySched.log.add(current.id + "  " + msg);
        }
    }
}
