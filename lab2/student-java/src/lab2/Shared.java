package lab2;

/* Этап 1: общая переменная. Перед каждым get/set — точка переключения (пауза планировщика),
 * после — строка в лог: чтение "<имя> -> <значение>", запись "<имя> = <значение>".
 * getAcquire/setRelease для планировщика ведут себя так же, как get/set. */
public final class Shared<T> {
    private final String name;
    private T value;

    public Shared(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public T get() {
        Scheduler.yield(null);
        Scheduler.log(String.format("%s -> %s", this.name, this.value.toString()));
        return this.value;
    }

    public void set(T v) {
        Scheduler.yield(null);
        Scheduler.log(String.format("%s = %s", this.name, v.toString()));
        this.value = v;
    }

    /** То же, что get(): планировщик не моделирует порядки памяти. */
    public T getAcquire() {
        return this.get();
    }

    /** То же, что set(v). */
    public void setRelease(T v) {
        this.set(v);
    }

    /** Чтение без паузы и без лога: для проверок после прогона и условий await. */
    public T peek() {
        return value;
    }
}
