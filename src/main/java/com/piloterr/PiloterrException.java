package com.piloterr;

/** Base class for every SDK-defined exception. */
public class PiloterrException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public PiloterrException(String message, Throwable cause) {
    super(message, cause);
  }
}
