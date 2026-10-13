package ca.gc.tbs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PrefetchingIteratorTest {

  @Test
  void deliversEveryElementInOrder() {
    List<Integer> source = IntStream.range(0, 50_000).boxed().toList();
    List<Integer> received = new ArrayList<>();

    try (PrefetchingIterator<Integer> elements = new PrefetchingIterator<>(source.stream(), 16)) {
      elements.forEachRemaining(received::add);
    }

    assertThat(received).isEqualTo(source);
  }

  @Test
  void deliversAFailureAfterTheElementsThatPrecededIt() {
    AtomicBoolean sourceClosed = new AtomicBoolean();
    Stream<Integer> source =
        Stream.<Supplier<Integer>>of(
                () -> 1,
                () -> 2,
                () -> {
                  throw new IllegalStateException("cursor failed");
                })
            .map(Supplier::get)
            .onClose(() -> sourceClosed.set(true));

    try (PrefetchingIterator<Integer> elements = new PrefetchingIterator<>(source, 16)) {
      assertThat(elements.next()).isEqualTo(1);
      assertThat(elements.next()).isEqualTo(2);
      assertThatThrownBy(elements::hasNext)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("cursor failed");
      assertThat(sourceClosed).isTrue();
    }
  }

  @Test
  void deliversErrorsInsteadOfLeavingTheConsumerWaiting() {
    Stream<Integer> source =
        Stream.<Supplier<Integer>>of(
                () -> {
                  throw new OutOfMemoryError("simulated");
                })
            .map(Supplier::get);

    try (PrefetchingIterator<Integer> elements = new PrefetchingIterator<>(source, 16)) {
      assertThatThrownBy(elements::hasNext).isInstanceOf(OutOfMemoryError.class);
    }
  }

  @Test
  void closesTheSourceOnceItIsExhausted() {
    AtomicBoolean sourceClosed = new AtomicBoolean();

    try (PrefetchingIterator<Integer> elements =
        new PrefetchingIterator<>(Stream.of(1, 2).onClose(() -> sourceClosed.set(true)), 16)) {
      elements.forEachRemaining(element -> {});
      assertThat(sourceClosed).isTrue();
    }
  }

  @Test
  void closingEarlyStopsReadingAndClosesTheSource() throws Exception {
    CountDownLatch sourceClosed = new CountDownLatch(1);
    AtomicInteger produced = new AtomicInteger();
    Stream<Integer> endless =
        Stream.generate(produced::incrementAndGet).onClose(sourceClosed::countDown);

    PrefetchingIterator<Integer> elements = new PrefetchingIterator<>(endless, 4);
    assertThat(elements.next()).isEqualTo(1);
    elements.close();

    assertThat(sourceClosed.await(5, TimeUnit.SECONDS)).isTrue();
    int producedWhenClosed = produced.get();
    Thread.sleep(200);
    assertThat(produced.get()).isEqualTo(producedWhenClosed);
  }
}
