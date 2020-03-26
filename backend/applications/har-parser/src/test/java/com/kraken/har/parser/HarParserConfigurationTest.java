package com.kraken.har.parser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestPropertiesConfig.class, ImmutableHarParserProperties.class})
public class HarParserConfigurationTest {
  @Autowired
  HarParserProperties properties;

  @Test
  public void shouldCreateProperties() {
    assertThat(properties.getLocal()).isNotNull();
    assertThat(properties.getRemote()).isNotNull();
  }
}


