package com.kraken.storage.server;

import com.kraken.security.authentication.api.UserProvider;
import com.kraken.storage.entity.StorageNode;
import com.kraken.storage.entity.StorageWatcherEvent;
import com.kraken.storage.file.StorageService;
import com.kraken.storage.file.StorageWatcherService;
import com.kraken.tools.sse.SSEService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Strings.nullToEmpty;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@Slf4j
@RestController()
@RequestMapping("/files")
@AllArgsConstructor(access = PACKAGE)
@FieldDefaults(level = PRIVATE, makeFinal = true)
class StorageController {

  @NonNull StorageService service;
  @NonNull StorageWatcherService watcher;
  @NonNull SSEService sse;
  @NonNull UserProvider userProvider;

  @GetMapping("/list")
  public Flux<StorageNode> list(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId) {
    log.info("List all nodes");
    return this.userProvider.getOwner(applicationId).flatMapMany(this.service::list);
  }

  @GetMapping("/get")
  public Mono<StorageNode> get(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                               @RequestParam(value = "path") final String path) {
    log.info(String.format("Get file %s", path));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> this.service.get(owner, path));
  }

  @PostMapping("/delete")
  public Flux<Boolean> delete(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                              @RequestBody() final List<String> paths) {
    log.info(String.format("Delete files %s", String.join(", ", paths)));
    return this.userProvider.getOwner(applicationId).flatMapMany(owner -> service.delete(owner, paths));
  }

  @PostMapping("/rename")
  public Mono<StorageNode> rename(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                  @RequestParam(value = "directoryPath", required = false) final String directoryPath,
                                  @RequestParam("oldName") final String oldName,
                                  @RequestParam("newName") final String newName) {
    log.info(String.format("Rename in folder %s from %s to %s", directoryPath, oldName, newName));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> service.rename(owner, nullToEmpty(directoryPath), oldName, newName));
  }

  @PostMapping("/set/directory")
  public Mono<StorageNode> setDirectory(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                        @RequestParam("path") final String path) {
    log.info(String.format("Set directory %s", path));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> service.setDirectory(owner, path));
  }

  @PostMapping(value = "/set/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Mono<StorageNode> setZip(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                  @RequestParam(value = "path", required = false) final String path,
                                  @RequestPart("file") final Mono<FilePart> file) {
    log.info(String.format("Set zip %s", path));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> service.setZip(owner, nullToEmpty(path), file));
  }

  @PostMapping(value = "/set/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Mono<StorageNode> setFile(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                   @RequestParam(value = "path", required = false) final String path,
                                   @RequestPart("file") final Mono<FilePart> file) {
    log.info(String.format("Set file %s", path));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> service.setFile(owner, nullToEmpty(path), file));
  }

  @GetMapping("/extract/zip")
  public Mono<StorageNode> extractZip(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                      @RequestParam("path") final String path) {
    log.info(String.format("Extract zip at %s", path));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> service.extractZip(owner, path));
  }

  @GetMapping("/get/file")
  public Mono<ResponseEntity<InputStreamResource>> getFile(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                                           @RequestParam(value = "path", required = false) final String path) {
    log.info(String.format("Get file %s", path));
    final var optionalPath = nullToEmpty(path);
    return this.userProvider.getOwner(applicationId).flatMap(owner -> service.getFile(owner, optionalPath).map(is -> ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"%s\"", service.getFileName(owner, optionalPath)))
        .body(new InputStreamResource(is)))
    );
  }

  @GetMapping("/find")
  public Flux<StorageNode> find(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                @RequestParam(value = "rootPath", required = false) final String rootPath,
                                @RequestParam(value = "maxDepth", required = false) final Integer maxDepth,
                                @RequestParam(value = "matcher", required = false) final String matcher) {
    log.info(String.format("Find rootPath %s, maxDepth %d, matcher %s", rootPath, maxDepth, matcher));
    final var optionalPath = nullToEmpty(rootPath);
    final var optionalMaxDepth = Optional.ofNullable(maxDepth).orElse(Integer.MAX_VALUE);
    final var optionalMatcher = Optional.ofNullable(matcher).orElse(".*");
    return this.userProvider.getOwner(applicationId).flatMapMany(owner -> service.find(owner, optionalPath, optionalMaxDepth, optionalMatcher));
  }

  @PostMapping(value = "/set/content", consumes = TEXT_PLAIN_VALUE)
  public Mono<StorageNode> setContent(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                      @RequestParam("path") final String path,
                                      @RequestBody(required = false) final String content) {
    log.debug(String.format("Set content for %s :%n%s", path, content));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> service.setContent(owner, path, nullToEmpty(content)));
  }

  @GetMapping(value = "/get/content", produces = TEXT_PLAIN_VALUE)
  public Mono<String> getContent(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                 @RequestParam(value = "path") final String path) {
    log.debug(String.format("Get content of %s", path));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> this.service.getContent(owner, path));
  }

  @GetMapping(value = "/get/json", produces = APPLICATION_JSON_VALUE)
  public Mono<String> getJSON(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                              @RequestParam(value = "path") final String path) {
    log.info(String.format("Get JSON for %s", path));
    return this.userProvider.getOwner(applicationId).flatMap(owner -> this.service.getContent(owner, path));
  }

  @PostMapping(value = "/list/json", produces = APPLICATION_JSON_VALUE)
  public Mono<String> listJSON(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                               @RequestBody() final List<String> paths) {
    log.info(String.format("List JSON for %s", String.join(", ", paths)));
    final var list = this.userProvider.getOwner(applicationId).flatMapMany(owner -> this.service.getContent(owner, paths)).collectList().map(strings -> String.join(", ", strings));
    return Flux.concat(Flux.just("["), Flux.from(list), Flux.just("]")).reduce((s, s2) -> s + s2);
  }

  @GetMapping(value = "/watch")
  public Flux<ServerSentEvent<StorageWatcherEvent>> watch(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                                          @RequestParam(value = "root", required = false) final String root) {
    log.info("Watch storage events");
    return this.userProvider.getOwner(applicationId).flatMapMany(owner -> this.sse.keepAlive(this.watcher.watch(owner, nullToEmpty(root))).map(event -> {
      log.debug(event.toString());
      return event;
    }));
  }

  @PostMapping("/copy")
  public Flux<StorageNode> copy(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                @RequestBody() final List<String> paths,
                                @RequestParam("destination") final String destination) {
    log.info(String.format("Copy %s to %s", String.join(", ", paths), destination));
    return this.userProvider.getOwner(applicationId).flatMapMany(owner -> service.copy(owner, paths, destination));
  }

  @PostMapping("/move")
  public Flux<StorageNode> move(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                @RequestBody() final List<String> paths,
                                @RequestParam("destination") final String destination) {
    log.info(String.format("Move %s to %s", String.join(", ", paths), destination));
    return this.userProvider.getOwner(applicationId).flatMapMany(owner -> service.move(owner, paths, destination));
  }

  @PostMapping("/filter/existing")
  public Flux<StorageNode> filterExisting(@RequestHeader("ApplicationId") @Pattern(regexp = "[a-z0-9]*") final String applicationId,
                                          @RequestBody() final List<StorageNode> nodes) {
    log.info(String.format("Filter %d existing nodes", nodes.size()));
    return this.userProvider.getOwner(applicationId).flatMapMany(owner -> service.filterExisting(owner, nodes));
  }
}
