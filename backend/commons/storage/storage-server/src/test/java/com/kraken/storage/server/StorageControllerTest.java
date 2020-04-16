package com.kraken.storage.server;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.kraken.security.entity.owner.PublicOwner;
import com.kraken.security.entity.owner.UserOwnerTest;
import com.kraken.storage.entity.StorageNode;
import com.kraken.storage.entity.StorageNodeTest;
import com.kraken.storage.entity.StorageWatcherEvent;
import com.kraken.storage.file.StorageService;
import com.kraken.storage.file.StorageWatcherService;
import com.kraken.tests.security.AuthControllerTest;
import com.kraken.tools.sse.SSEService;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static com.kraken.storage.entity.StorageNodeType.DIRECTORY;
import static com.kraken.storage.entity.StorageWatcherEventTest.STORAGE_WATCHER_EVENT;
import static com.kraken.tests.utils.TestUtils.shouldPassNPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class StorageControllerTest extends AuthControllerTest {

  @MockBean
  StorageService service;

  @MockBean
  StorageWatcherService watcher;

  @MockBean
  SSEService sse;

  @Test
  public void shouldPassTestUtils() {
    shouldPassNPE(StorageController.class);
  }

  @Test
  public void shouldListNodes() {
    final var path = "path";
    given(service.list(userOwner))
        .willReturn(Flux.just(StorageNode.builder().path(path).depth(0).length(0L).lastModified(0L).type(DIRECTORY).build()));

    webTestClient.get()
        .uri("/files/list")
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$[0].path").isEqualTo(path);
  }

  @Test
  public void shouldListNodesForbidden() {
    webTestClient.get()
        .uri("/files/list")
        .header("Authorization", "Bearer no-role-token")
        .exchange()
        .expectStatus().is4xxClientError();
  }

  @Test
  public void shouldGetNode() {
    final var filename = "file.txt";
    given(service.get(userOwner, filename))
        .willReturn(Mono.just(StorageNode.builder().path(filename).depth(0).length(0L).lastModified(0L).type(DIRECTORY).build()));

    webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/files/get")
            .queryParam("path", filename)
            .build())
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.path").isEqualTo(filename);
  }

  @Test
  public void shouldDelete() {
    final var paths = Arrays.asList("toto/file.txt");
    given(service.delete(userOwner, paths))
        .willReturn(Flux.just(true));

    webTestClient.post()
        .uri("/files/delete")
        .body(BodyInserters.fromValue(paths))
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  public void shouldDeleteFail() {
    final var paths = Arrays.asList("toto/file.txt");
    given(service.delete(userOwner, paths))
        .willReturn(Flux.error(new IllegalArgumentException("Failed to delete")));

    webTestClient.post()
        .uri("/files/delete")
        .header("Authorization", "Bearer user-token")
        .body(BodyInserters.fromValue(paths))
        .exchange()
        .expectStatus().is5xxServerError();
  }

  @Test
  public void shouldSetFile() throws IOException {
    final var path = "toto";
    given(service.setFile(eq(userOwner), any(), any()))
        .willReturn(Mono.just(StorageNodeTest.STORAGE_NODE));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/set/file")
            .queryParam("path", path)
            .build())
        .header("Authorization", "Bearer user-token")
        .body(BodyInserters.fromMultipartData("file", new UrlResource("file", "testDir/testupload.txt")))
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(StorageNode.class)
        .isEqualTo(StorageNodeTest.STORAGE_NODE);
  }

  @Test
  public void shouldSetZip() throws IOException {
    final var path = "toto";
    given(service.setZip(eq(userOwner), any(), any()))
        .willReturn(Mono.just(StorageNodeTest.STORAGE_NODE));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/set/zip")
            .queryParam("path", path)
            .build())
        .header("Authorization", "Bearer user-token")
        .body(BodyInserters.fromMultipartData("file", new UrlResource("file", "testDir/kraken.zip")))
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(StorageNode.class)
        .isEqualTo(StorageNodeTest.STORAGE_NODE);
  }

  @Test
  public void shouldSetDirectory() {
    final var path = "someDir";
    given(service.setDirectory(userOwner, path))
        .willReturn(Mono.just(StorageNodeTest.STORAGE_NODE));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/set/directory")
            .queryParam("path", path)
            .build())
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(StorageNode.class)
        .isEqualTo(StorageNodeTest.STORAGE_NODE);
  }

  @Test
  public void shouldGetFile() {
    final var content = "File getContent";
    given(service.getFile(userOwner, ""))
        .willReturn(Mono.just(new ByteArrayInputStream(content.getBytes(Charsets.UTF_8))));
    given(service.getFileName(userOwner, ""))
        .willReturn("test.txt");

    webTestClient.get()
        .uri("/files/get/file")
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.txt\"")
        .expectBody(String.class)
        .isEqualTo(content);
  }

  @Test
  public void shouldRenameFile() {
    final var oldName = "oldName.txt";
    final var newName = "newName.txt";
    final var node = StorageNode.builder().path(newName).depth(0).length(0L).lastModified(0L).type(DIRECTORY).build();
    given(service.rename(userOwner, "", oldName, newName))
        .willReturn(Mono.just(node));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/rename")
            .queryParam("oldName", oldName)
            .queryParam("newName", newName)
            .build())
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(StorageNode.class)
        .isEqualTo(node);
  }

  @Test
  public void shouldGetDirectory() {
    final var content = "File getContent";
    given(service.getFile(userOwner, ""))
        .willReturn(Mono.just(new ByteArrayInputStream(content.getBytes(Charsets.UTF_8))));
    given(service.getFileName(userOwner, ""))
        .willReturn("test.zip");

    webTestClient.get()
        .uri("/files/get/file?path=")
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.zip\"")
        .expectBody(String.class)
        .isEqualTo(content);
  }

  @Test
  public void shouldSetContent() {
    final var path = "toto/file.txt";
    final var content = "Test content";
    given(service.setContent(userOwner, path, content))
        .willReturn(Mono.just(StorageNodeTest.STORAGE_NODE));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/set/content")
            .queryParam("path", path)
            .build())
        .header("Authorization", "Bearer user-token")
        .body(BodyInserters.fromValue(content))
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(StorageNode.class)
        .isEqualTo(StorageNodeTest.STORAGE_NODE);
  }

  @Test
  public void shouldGetContent() {
    final var path = "toto/file.txt";
    given(service.getContent(userOwner, path))
        .willReturn(Mono.just("some getContent"));

    webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/files/get/content")
            .queryParam("path", path)
            .build())
        .accept(MediaType.TEXT_PLAIN)
        .header("Content-type", MediaType.TEXT_PLAIN_VALUE)
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("text/plain;charset=UTF-8")
        .expectBody(String.class)
        .isEqualTo("some getContent");
  }


  @Test
  public void shouldGetJson() {
    final var path = "toto/file.json";
    given(service.getContent(userOwner, path))
        .willReturn(Mono.just("{}"));

    webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/files/get/json")
            .queryParam("path", path)
            .build())
        .accept(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer user-token")
        .header("Content-type", MediaType.APPLICATION_JSON_VALUE)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("application/json;charset=UTF-8")
        .expectBody(String.class)
        .isEqualTo("{}");
  }

  @Test
  public void shouldGetContents() {
    final var paths = ImmutableList.of("toto/file1.txt", "toto/file2.txt");
    given(service.getContent(userOwner, paths))
        .willReturn(Flux.just("{\"key1\": \"value1\"}", "{\"key2\": \"value2\"}"));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/list/json")
            .build())
        .body(BodyInserters.fromValue(paths))
        .header("Authorization", "Bearer user-token")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("application/json;charset=UTF-8")
        .expectBody(String.class)
        .isEqualTo("[{\"key1\": \"value1\"}, {\"key2\": \"value2\"}]");
  }


  @Test
  public void shouldWatch() {
    final var flux = Flux.just(STORAGE_WATCHER_EVENT, STORAGE_WATCHER_EVENT);
    final var sseFlux = Flux.just(ServerSentEvent.builder(STORAGE_WATCHER_EVENT).build(), ServerSentEvent.<StorageWatcherEvent>builder().comment("keep alive").build(), ServerSentEvent.builder(STORAGE_WATCHER_EVENT).build());

    given(watcher.watch(userOwner, ""))
        .willReturn(flux);

    given(sse.keepAlive(flux)).willReturn(sseFlux);

    final var result = webTestClient.get()
        .uri("/files/watch")
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
        .expectBody()
        .returnResult();

    final var body = new String(Optional.ofNullable(result.getResponseBody()).orElse(new byte[0]), Charsets.UTF_8);
    Assertions.assertThat(body).isEqualTo("data:{\"node\":{\"path\":\"path\",\"type\":\"DIRECTORY\",\"depth\":0,\"length\":0,\"lastModified\":0},\"event\":\"Event\"}\n" +
        "\n" +
        ":keep alive\n" +
        "\n" +
        "data:{\"node\":{\"path\":\"path\",\"type\":\"DIRECTORY\",\"depth\":0,\"length\":0,\"lastModified\":0},\"event\":\"Event\"}\n" +
        "\n");
  }

  @Test
  public void shouldFindFiles() {
    given(service.find(userOwner, "", Integer.MAX_VALUE, ".*"))
        .willReturn(Flux.just(StorageNodeTest.STORAGE_NODE));

    webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/files/find")
            .build())
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$[0].path").isEqualTo(StorageNodeTest.STORAGE_NODE.getPath());
  }

  @Test
  public void shouldCopyFiles() {
    final var paths = Arrays.asList("path1", "path2");
    final var destination = "destination";
    given(service.copy(userOwner, paths, destination))
        .willReturn(Flux.just(StorageNodeTest.STORAGE_NODE));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/copy")
            .queryParam("destination", destination)
            .build())
        .header("Authorization", "Bearer user-token")
        .body(BodyInserters.fromValue(paths))
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$[0].path").isEqualTo(StorageNodeTest.STORAGE_NODE.getPath());
  }

  @Test
  public void shouldMoveFiles() {
    final var paths = Arrays.asList("path1", "path2");
    final var destination = "destination";
    given(service.move(userOwner, paths, destination))
        .willReturn(Flux.just(StorageNodeTest.STORAGE_NODE));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/move")
            .queryParam("destination", destination)
            .build())
        .header("Authorization", "Bearer user-token")
        .body(BodyInserters.fromValue(paths))
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$[0].path").isEqualTo(StorageNodeTest.STORAGE_NODE.getPath());
  }

  @Test
  public void shouldFilterExisting() {
    final var nodes = ImmutableList.of(
        StorageNode.builder()
            .path("visitorTest/dir1")
            .type(DIRECTORY)
            .depth(1)
            .lastModified(0L)
            .length(0L)
            .build(),
        StorageNode.builder()
            .path("visitorTest/dir2")
            .type(DIRECTORY)
            .depth(1)
            .lastModified(0L)
            .length(0L)
            .build()
    );

    given(service.filterExisting(userOwner, nodes))
        .willReturn(Flux.fromIterable(nodes));

    webTestClient.post()
        .uri(uriBuilder -> uriBuilder.path("/files/filter/existing")
            .build())
        .header("Authorization", "Bearer user-token")
        .body(BodyInserters.fromValue(nodes))
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$[0].path").isEqualTo(nodes.get(0).getPath())
        .jsonPath("$[1].path").isEqualTo(nodes.get(1).getPath())
        .jsonPath("$.length()").isEqualTo(2);
  }

  @Test
  public void shouldExtractZip() {
    final var path = "path/archive.zip";
    given(service.extractZip(userOwner, path))
        .willReturn(Mono.just(StorageNodeTest.STORAGE_NODE));

    webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/files/extract/zip")
            .queryParam("path", path)
            .build())
        .header("Authorization", "Bearer user-token")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.path").isEqualTo(StorageNodeTest.STORAGE_NODE.getPath());
  }
}
