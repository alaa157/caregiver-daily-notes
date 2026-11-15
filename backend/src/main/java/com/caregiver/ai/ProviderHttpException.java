package com.caregiver.ai;

import java.io.IOException;

public class ProviderHttpException extends IOException {

  private final int statusCode;

  public ProviderHttpException(int statusCode) {
    super("HTTP " + statusCode);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
