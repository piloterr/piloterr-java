package com.piloterr;

/** The request failed before a complete HTTP response was received (network error, timeout). */
public final class PiloterrTransportException extends PiloterrException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public PiloterrTransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
