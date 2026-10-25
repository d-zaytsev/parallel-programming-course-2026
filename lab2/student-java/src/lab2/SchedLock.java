package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    Scheduler.ScheduledTask owner;
    Scheduler scheduler;

    public SchedLock(Scheduler s, String name) {
        this.name = name;
        this.scheduler = s;
        s.locks.add(this);
    }

    public void lock() {
        var cur = this.scheduler.getCurrentTask();
        Scheduler.await_lock(() -> this.owner == null);
        this.owner = cur;
        Scheduler.log("lock " + this.name);
    }

    public void unlock() {
        Scheduler.yield(null, false);
        this.owner = null;
        Scheduler.log("unlock " + this.name);
    }
}
