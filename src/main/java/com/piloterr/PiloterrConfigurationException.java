package com.piloterr;

/** The client configuration is missing or unusable. */
public final class PiloterrConfigurationException extends PiloterrException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public PiloterrConfigurationException(String message) {
    super(message, null);
  }

  /** Creates the exception with a cause. */
  public PiloterrConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
