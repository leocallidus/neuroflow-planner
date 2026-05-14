package com.example.neuroflowplanner.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.MDC;

public final class AsyncContext {

    public static final String REQUEST_ID_KEY = "requestId";

    private AsyncContext() {
    }

    public static String ensureRequestId() {
        String requestId = MDC.get(REQUEST_ID_KEY);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        String generated = "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        MDC.put(REQUEST_ID_KEY, generated);
        return generated;
    }

    public static Map<String, String> capture() {
        String requestId = ensureRequestId();
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        Map<String, String> copy = snapshot == null ? new HashMap<>() : new HashMap<>(snapshot);
        copy.putIfAbsent(REQUEST_ID_KEY, requestId);
        return copy;
    }

    public static Runnable withMdc(Runnable runnable) {
        Map<String, String> context = capture();
        return () -> runWithContext(context, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> Supplier<T> withMdcSupplier(Supplier<T> supplier) {
        Map<String, String> context = capture();
        return () -> runWithContext(context, supplier);
    }

    public static <T> Callable<T> withMdcCallable(Callable<T> callable) {
        Map<String, String> context = capture();
        return () -> runWithContext(context, () -> {
            try {
                return callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    public static <T> Consumer<T> withMdcConsumer(Consumer<T> consumer) {
        Map<String, String> context = capture();
        return value -> runWithContext(context, () -> {
            consumer.accept(value);
            return null;
        });
    }

    public static <T, U> BiConsumer<T, U> withMdcBiConsumer(BiConsumer<T, U> consumer) {
        Map<String, String> context = capture();
        return (left, right) -> runWithContext(context, () -> {
            consumer.accept(left, right);
            return null;
        });
    }

    public static <T, R> Function<T, R> withMdcFunction(Function<T, R> function) {
        Map<String, String> context = capture();
        return value -> runWithContext(context, () -> function.apply(value));
    }

    public static <T, U, R> BiFunction<T, U, R> withMdcBiFunction(BiFunction<T, U, R> function) {
        Map<String, String> context = capture();
        return (left, right) -> runWithContext(context, () -> function.apply(left, right));
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(withMdcSupplier(supplier));
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(withMdcSupplier(supplier), executor);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(withMdc(runnable));
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        return CompletableFuture.runAsync(withMdc(runnable), executor);
    }

    public static ThreadFactory namedThreadFactory(String namePrefix, boolean daemon) {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(withMdc(runnable), namePrefix + "-" + sequence.getAndIncrement());
            thread.setDaemon(daemon);
            return thread;
        };
    }

    public static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return current;
    }

    private static <T> T runWithContext(Map<String, String> context, Supplier<T> supplier) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        replaceContext(context);
        try {
            return supplier.get();
        } finally {
            replaceContext(previous);
        }
    }

    private static void replaceContext(Map<String, String> context) {
        MDC.clear();
        if (context != null && !context.isEmpty()) {
            MDC.setContextMap(context);
        }
    }
}
