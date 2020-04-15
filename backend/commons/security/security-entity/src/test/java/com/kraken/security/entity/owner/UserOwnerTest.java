package com.kraken.security.entity.owner;

import com.kraken.security.entity.owner.UserOwner;
import com.kraken.tests.utils.TestUtils;
import org.junit.Test;

public class UserOwnerTest {

  public static final UserOwner USER_OWNER = UserOwner.builder()
      .userId("userId")
      .applicationId("applicationId")
      .build();

  @Test
  public void shouldPassEquals() {
    TestUtils.shouldPassEquals(USER_OWNER.getClass());
  }

  @Test
  public void shouldPassNPE() {
    TestUtils.shouldPassNPE(USER_OWNER.getClass());
  }

  @Test
  public void shouldPassToString() {
    TestUtils.shouldPassToString(USER_OWNER);
  }

}