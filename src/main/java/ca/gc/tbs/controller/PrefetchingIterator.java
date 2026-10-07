package ca.gc.tbs.controller;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Iterates a stream that a background thread reads ahead into a bounded buffer, so a slow source
 * such as a database cursor keeps fetching while the caller is still processing earlier elements.
 *
 * <p>Elements, and any exception thrown by the source, are delivered in source order. Closing the
 * iterator stops the background thread, which then closes the source.
 */
final class PrefetchingIterator<T> implements Iterator<T>, AutoCloseable {

  private static final Object END = new Object();

  private record Failure(Throwable cause) {}

  private final BlockingQueue<Object> buffer;
  private volatile boolean closed;
  private Object next;

  PrefetchingIterator(Stream<T> source, int capacity) {
    this.buffer = new ArrayBlockingQueue<>(capacity);
    Thread.ofVirtual().name("export-prefetch").start(() -> fill(source));
  }

  private void fill(Stream<T> source) {
    Object last = END;
    try (source) {
      Iterator<T> elements = source.iterator();
      while (elements.hasNext()) {
        if (!offer(elements.next())) {
          return; // Closed by the consumer; leaving the block closes the source
        }
      }
    } catch (RuntimeException | Error e) {
      last = new Failure(e);
    }
    // The source is already closed here, so a consumer that sees the end or a failure can rely on it
    offer(last);
  }

  /** Waits while the buffer is full; returns false once the consumer has closed the iterator. */
  private boolean offer(Object element) {
    try {
      while (!closed) {
        if (buffer.offer(element, 100, TimeUnit.MILLISECONDS)) {
          return true;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  @Override
  public boolean hasNext() {
    if (next == null) {
      try {
        next = buffer.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for the next element", e);
      }
    }
    if (next instanceof Failure failure) {
      if (failure.cause() instanceof Error error) {
        throw error;
      }
      throw (RuntimeException) failure.cause();
    }
    return next != END;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    T element = (T) next;
    next = null;
    return element;
  }

  @Override
  public void close() {
    closed = true;
    buffer.clear();
  }
}
