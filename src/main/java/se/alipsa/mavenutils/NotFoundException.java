package se.alipsa.mavenutils;

/**
 * Custom exception class for handling not found errors.
 * This class extends RuntimeException and provides constructors
 * for creating exceptions with or without a message and/or cause.
 */
public class NotFoundException extends RuntimeException {

  /**
   * Constructs a NotFoundException with no message.
   */
  public NotFoundException() {
  }

  /**
   * Constructs a NotFoundException with a specified message.
   *
   * @param message the detail message
   */
  public NotFoundException(String message) {
    super(message);
  }

  /**
   * Constructs a NotFoundException with a specified message and cause.
   *
   * @param message the detail message
   * @param cause   the cause of the exception
   */
  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
