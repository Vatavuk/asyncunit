package hr.com.vgv.asyncunit;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class ResultsTest
{
    private Results results;

    @BeforeEach
    public void setup()
    {
        results = new Results.Synced();
    }

    @Test
    public void waitsForAllThreadToFinish() throws InterruptedException
    {
        Queue<Long> queue = new ConcurrentLinkedDeque<>();

        for (int i = 0; i < 4; i++)
        {
            new Thread(() -> {
                Sleep.now();
                queue.add(Thread.currentThread().getId());
                results.addSuccess();
            }).start();
        }

        results.await(4000, 4);

        Assertions.assertEquals(4, queue.size());
    }

    @Test
    public void threadsFinishedBeforeAwait() throws InterruptedException
    {
        Queue<Long> queue = new ConcurrentLinkedDeque<>();

        for (int i = 0; i < 4; i++)
        {
            new Thread(() -> {
                queue.add(Thread.currentThread().getId());
                results.addSuccess();
            }).start();
        }

        Sleep.now(300);

        results.await(1000, 4);

        Assertions.assertEquals(4, queue.size());
    }

    @Test
    public void failsOnMissingExecutions()
    {
        for (int i = 0; i < 3; i++)
        {
            new Thread(() -> {
                Sleep.now();
                results.addSuccess();
            }).start();
        }

        assertThatThrownBy(() -> results.await(1000, 4))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Number of flow executions was 3 instead of 4");
    }

    @Test
    public void failsOnMissingExecutionsAfterThreadFinished()
    {
        for (int i = 0; i < 3; i++)
        {
            new Thread(() -> results.addSuccess()).start();
        }

        Sleep.now(300);

        assertThatThrownBy(() -> results.await(100, 4))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Number of flow executions was 3 instead of 4");
    }

    @Test
    public void failsOnMissingExecutionsDueToTimeout()
    {
        for (int i = 0; i < 4; i++)
        {
            new Thread(() -> {
                Sleep.now(300);
                results.addSuccess();
            }).start();
        }
        Assertions.assertThrows(AssertionError.class, () -> results.await(100, 4));
    }

    @Test
    public void failsOnFailureInSingleThread()
    {
        new Thread(() -> {
            Sleep.now();
            results.addFailure(new IllegalStateException(""));
        }).start();

        Assertions.assertThrows(IllegalStateException.class, results::await);
    }

    @Test
    public void failsOnFailureInAnyThread()
    {
        new Thread(() -> results.addFailure(new IllegalStateException(""))).start();

        new Thread(() -> results.addSuccess()).start();

        Assertions.assertThrows(IllegalStateException.class, () -> results.await(100, 1));
    }

    @Test
    public void failsOnWaitingResultsFromDifferentThread() throws InterruptedException
    {
        new Thread(() -> {
            Sleep.now();
            awaitResults();
            results.addSuccess();
        }).start();

        try
        {
            results.await();
            fail("Exception not raised");
        }
        catch (IllegalStateException e)
        {
            assertThat(e.getMessage()).contains("Cannot wait for results, some other thread is already awaiting.");
        }
    }

    private void awaitResults()
    {
        try
        {
            results.await();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }
}
