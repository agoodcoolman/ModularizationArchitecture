package com.spinytech.macore;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Administrator
 *         Date 2017/3/10
 *         线程池管理
 */
public class Threads {

    private static ExecutorService executorService;

    private static ExecutorService getExecutor() {
        if (executorService == null) {
            synchronized (Threads.class) {
                if (executorService == null)
                    executorService = Executors.newFixedThreadPool(5);
            }
        }
        return executorService;
    }

    public static void submit(Thread t) {
        getExecutor().submit(t);
    }
    public static void submit(Runnable r) {
        getExecutor().submit(r);
    }
    public static <T>Future submit(Callable<T> r) {
       return getExecutor().submit(r);
    }
}
