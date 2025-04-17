package se.alipsa.mavenutils;

/**
 * Custom exception class for handling not found errors.
 * This class extends RuntimeException and provides constructors
 * for creating exceptions with or without a message and/or cause.
 */
public class NotFoundException extends RuntimeException {
  public NotFoundException() {
  }

  public NotFoundException(String message) {
    super(message);
  }

  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
