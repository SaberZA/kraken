package com.kraken.parser.log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.NullPointerTester;
import com.kraken.analysis.entity.DebugEntryTest;
import com.kraken.config.gatling.api.GatlingLog;
import com.kraken.config.gatling.api.GatlingProperties;
import com.kraken.debug.entry.writer.DebugEntryWriter;
import com.kraken.gatling.log.parser.LogParser;
import com.kraken.runtime.command.Command;
import com.kraken.runtime.command.CommandService;
import com.kraken.runtime.container.executor.AbstractContainerExecutorTest;
import com.kraken.runtime.container.predicate.TaskPredicate;
import com.kraken.runtime.entity.task.TaskTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;

import static com.google.common.testing.NullPointerTester.Visibility.PACKAGE;
import static com.kraken.runtime.entity.task.FlatContainerTest.CONTAINER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GatlingParserTest extends AbstractContainerExecutorTest {

  @Mock
  DebugEntryWriter writer;
  @Mock
  CommandService commandService;
  @Mock
  LogParser logParser;
  @Mock
  TaskPredicate taskPredicate;
  @Mock
  GatlingLog log;
  @Mock
  GatlingProperties gatling;

  GatlingParser parser;

  @Before
  public void before() {
    when(gatling.getHome()).thenReturn(".");
    when(gatling.getLogs()).thenReturn(log);
    when(log.getDebug()).thenReturn(".");
    parser = new GatlingParser(
        runtimeClient,
        logParser,
        writer,
        gatling,
        taskPredicate,
        commandService,
        containerExecutor);
  }

  @Test
  public void shouldInit() {
    given(logParser.parse(any())).willReturn(Flux.empty());
    given(runtimeClient.waitForPredicate(CONTAINER, taskPredicate)).willReturn(Mono.delay(Duration.ofSeconds(1)).map(aLong -> TaskTest.TASK));
    given(commandService.execute(any(Command.class))).willReturn(Flux.just("cmd", "exec", "logs"));
    final var entries = ImmutableList.builder();
    given(writer.write(any())).willReturn(Flux.interval(Duration.ofMillis(400)).map(aLong -> DebugEntryTest.DEBUG_ENTRY).doOnNext(entries::add));
    parser.init();
    verify(runtimeClient).waitForPredicate(eq(CONTAINER), any());
    verify(logParser).parse(any(Path.class));
    verify(writer).write(any());
    verify(commandService).execute(Command.builder()
        .path(gatling.getHome())
        .command(ImmutableList.of("ls", "-lR"))
        .environment(ImmutableMap.of())
        .build());
    assertThat(entries.build().size()).isBetween(38, 40);
  }

  @Test
  public void shouldPassTestUtils() {
    new NullPointerTester().testConstructors(GatlingParser.class, PACKAGE);
  }
}
