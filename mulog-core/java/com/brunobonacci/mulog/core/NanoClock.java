package com.brunobonacci.mulog.core;

/**
 *
 * NanoClock uses the wall clock and a high-precision monotonic timer to
 * **estimate** a high granularity wall clock with monotonic properties.
 *
 * The idea is that we use the wall clock (System.currentTimeMillis()) to
 * mark a point in time called `tw0`, at the same time we start a
 * high-precision monotonic timer `tm0` (using System.nanoTime()) which
 * we use for computing a offset to the `tw0` with nanoseconds precision.
 *
 * With this method we can estimated a current wall-clock time with nanoseconds
 * granularity.
 *
 * ATTENTION: please be aware that this is *not* a High-Precision Wall Clock.
 *
 * The wall clock is just estimated and while the real wall clock is
 * dynamically adjusted and kept in sync with services like NTP deamon,
 * there is no such thing for monotonic clock.
 *
 * **However the monotonic clock pauses when process suspended
 * therefore over time they will diverge.**
 *
 * It is not to be used as a replacement for the wall clock.
 *
 * Another consideration is that buy storing such high precision clock into
 * a Java long field, we reduce the capacity by 1 million.
 * So the latest date that this clock support it will be:
 *
 * ```
 *  new java.util.Date(Long.MAX_VALUE / 1_000_000)
 *  => Sat Apr 12 00:47:16 BST 2262
 * ```
 *
 * This date is reasonably far out in the future for the purpose of this
 * implementation that it shouldn't be a problem.
 *
 */
public class NanoClock {

    private final static NanoClock _clock;

    static {
        _clock = new NanoClock();
    }

    // Wall clock set at initialization time
    private final long tw0;
    // monotonic clock start to calculate offset
    private final long tm0;

    private NanoClock(){
        tw0 = System.currentTimeMillis() * 1000000;
        // typically 36 nanos, between these two lines.
        tm0 = System.nanoTime();
    }

    public static final long currentTimeNanos(){
        return _clock.tw0 + (System.nanoTime() - _clock.tm0);
    }

    public static final long currentTimeMicros(){
        return currentTimeNanos() / 1000;
    }

    public static final long currentTimeMillis(){
        return currentTimeNanos() / 1000000;
    }

}
