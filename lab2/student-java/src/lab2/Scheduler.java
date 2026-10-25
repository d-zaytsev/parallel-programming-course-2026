package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    private enum ScheduledThreadState {
        WORKING,
        WAITING,
        WAITING_LOCK,
        WAITING_CONDITION,
        FINISHED
    }

    private final class ScheduledTask {
        final Runnable body;
        final Semaphore sem;
        final Scheduler mySched;

        Thread thread;
        ScheduledThreadState state;
        BooleanSupplier condition;
        boolean touched;

        ScheduledTask(Runnable body, Scheduler sched) {
            this.mySched = sched;
            this.touched = true;
            this.condition = () -> false;
            this.state = ScheduledThreadState.WAITING;
            this.body = body;
            this.sem = new Semaphore(0);
        }
    }

    /**
     * Если true, каждая операция с общими данными добавляет строку в log.
     * Выставляет runner.
     */
    boolean logging;
    /**
     * Лог прогона: строки "<поток> <операция>", например "A lock mu", "A balance ->
     * 100".
     */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    final StringBuilder trace;
    final List<ScheduledTask> runnables = new ArrayList<>();
    final Semaphore sem = new Semaphore(0);

    private static final ThreadLocal<ScheduledTask> CURRENT = new ThreadLocal<>();

    public Scheduler() {
        this.trace = new StringBuilder();
    }

    public void spawn(Runnable body) {
        runnables.add(new ScheduledTask(body, this));
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
            for (int i = 0; i < runnables.size(); i++) {
                var t = this.runnables.get(i);

                switch (t.state) {
                    case FINISHED -> {
                    }
                    case WAITING_CONDITION -> {
                        if (t.condition.getAsBoolean())
                            enabled.add(i);
                    }
                    case WAITING_LOCK -> {
                    }
                    default -> enabled.add(i);
                }
            }

            if (enabled.size() == 0)
                break;

            last = strategy.pick(enabled, last);
            this.trace.append((char) ('A' + last));

            var task = this.runnables.get(last);
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

    /**
     * Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например
     * "AAAABB".
     */
    public String traceString() {
        return this.trace.toString();
    }

    /**
     * window() из тестовых программ: пауза перед следующей операцией с общими
     * данными обязательна.
     */
    public static void point() {
        var current = CURRENT.get();
        log("window");
        current.touched = true;
    }

    static void yield(ScheduledThreadState newState) {
        var cur = CURRENT.get();
        if (cur.touched) {
            cur.state = newState != null ? newState : ScheduledThreadState.WAITING;
            cur.touched = false;
            cur.mySched.sem.release();
            cur.sem.acquireUninterruptibly();
        }
        cur.touched = true;
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        var cur = CURRENT.get();
        cur.condition = cond;
        if (!cond.getAsBoolean())
        {
            cur.touched = true; // засыпает пока условие не выполнится
            Scheduler.yield(ScheduledThreadState.WAITING_CONDITION);
        }
        log("await done");
    }

    public static void log(String msg) {
        var current = CURRENT.get();
        if (current.mySched.logging) {
            var letter = (char) ('A' + current.mySched.runnables.indexOf(current));
            current.mySched.log.add(letter + "  " + msg);
        }
    }
}
