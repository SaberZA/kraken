package com.kraken.storage.client.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraken.config.storage.client.api.StorageClientProperties;
import com.kraken.security.authentication.api.ExchangeFilterFactory;
import com.kraken.security.authentication.client.spring.AbstractAuthenticatedClientBuilder;
import com.kraken.storage.client.api.StorageClient;
import com.kraken.storage.client.api.StorageClientBuilder;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

@Component
@FieldDefaults(level = PRIVATE, makeFinal = true)
final class WebStorageClientBuilder extends AbstractAuthenticatedClientBuilder<StorageClient, StorageClientProperties> implements StorageClientBuilder {

  ObjectMapper mapper;
  ObjectMapper yamlMapper;

  public WebStorageClientBuilder(final List<ExchangeFilterFactory> exchangeFilterFactories,
                                 final StorageClientProperties properties,
                                 final ObjectMapper mapper,
                                 @Qualifier("yamlObjectMapper") final ObjectMapper yamlMapper) {
    super(exchangeFilterFactories, properties);
    this.mapper = requireNonNull(mapper);
    this.yamlMapper = requireNonNull(yamlMapper);
  }

  @Override
  public StorageClient build() {
    return new WebStorageClient(webClientBuilder.build(), mapper, yamlMapper);
  }

}
