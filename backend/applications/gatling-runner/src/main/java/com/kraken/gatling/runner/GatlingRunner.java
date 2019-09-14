package com.kraken.gatling.runner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.kraken.runtime.client.RuntimeClient;
import com.kraken.runtime.command.Command;
import com.kraken.runtime.command.CommandService;
import com.kraken.runtime.container.properties.RuntimeContainerProperties;
import com.kraken.runtime.entity.ContainerStatus;
import com.kraken.storage.client.StorageClient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.function.Supplier;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
final class GatlingRunner {

  @NonNull StorageClient storageClient;
  @NonNull RuntimeClient runtimeClient;
  @NonNull CommandService commandService;
  @NonNull RuntimeContainerProperties containerProperties;
  @NonNull GatlingRunnerProperties gatlingRunnerProperties;
  @NonNull Supplier<Command> commandSupplier;

  @PostConstruct
  public void init() {
    final var setStatusStarting = runtimeClient.setStatus(containerProperties.getContainerId(), ContainerStatus.STARTING);
    final var downloadConfiguration = storageClient.downloadFolder(gatlingRunnerProperties.getLocalConf(), gatlingRunnerProperties.getRemoteConf());
    final var downloadUserFiles = storageClient.downloadFolder(gatlingRunnerProperties.getLocalUserFiles(), gatlingRunnerProperties.getRemoteUserFiles());
    final var setStatusReady = runtimeClient.setStatus(containerProperties.getContainerId(), ContainerStatus.READY);
    final var waitForStatusReady = runtimeClient.waitForStatus(containerProperties.getTaskId(), ContainerStatus.READY);
    final var listFiles = commandService.execute(Command.builder()
        .path(gatlingRunnerProperties.getGatlingHome().toString())
        .command(ImmutableList.of("ls", "-lR"))
        .environment(ImmutableMap.of())
        .build());
    final var setStatusRunning = runtimeClient.setStatus(containerProperties.getContainerId(), ContainerStatus.RUNNING);
    final var startGatling = commandService.execute(commandSupplier.get());
    final var setStatusStopping = runtimeClient.setStatus(containerProperties.getContainerId(), ContainerStatus.STOPPING);
    final var waitForStatusStopping = runtimeClient.waitForStatus(containerProperties.getTaskId(), ContainerStatus.STOPPING);
    final var uploadResult = storageClient.uploadFile(gatlingRunnerProperties.getLocalResult(), gatlingRunnerProperties.getRemoteResult());
    final var setStatusDone = runtimeClient.setStatus(containerProperties.getContainerId(), ContainerStatus.DONE);

    setStatusStarting.map(Object::toString).subscribe(log::info);
    downloadConfiguration.subscribe();
    downloadUserFiles.subscribe();
    setStatusReady.map(Object::toString).subscribe(log::info);
    waitForStatusReady.map(Object::toString).subscribe(log::info);
    listFiles.subscribe(log::debug);
    setStatusRunning.map(Object::toString).subscribe(log::info);
    startGatling.subscribe(log::info);
    setStatusStopping.map(Object::toString).subscribe(log::info);
    waitForStatusStopping.map(Object::toString).subscribe(log::info);
    uploadResult.subscribe();
    setStatusDone.map(Object::toString).subscribe(log::info);
  }

}