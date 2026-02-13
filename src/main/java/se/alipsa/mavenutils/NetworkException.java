package se.alipsa.mavenutils;

/**
 * Exception thrown when a network error occurs while communicating with a Maven repository.
 * This class extends RuntimeException and provides constructors
 * for creating exceptions with or without a message and/or cause.
 */
public class NetworkException extends RuntimeException {

  /**
   * Constructs a NetworkException with no message.
   */
  public NetworkException() {
  }

  /**
   * Constructs a NetworkException with a specified message.
   *
   * @param message the detail message
   */
  public NetworkException(String message) {
    super(message);
  }

  /**
   * Constructs a NetworkException with a specified message and cause.
   *
   * @param message the detail message
   * @param cause   the cause of the exception
   */
  public NetworkException(String message, Throwable cause) {
    super(message, cause);
  }
}
