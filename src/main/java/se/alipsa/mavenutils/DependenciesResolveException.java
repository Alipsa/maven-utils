package se.alipsa.mavenutils;

/**
 * Custom exception class for handling errors during dependency resolution.
 * This class extends Exception and provides constructors for creating exceptions
 * with a message and/or cause.
 */
public class DependenciesResolveException extends Exception {

  static final long serialVersionUID = 1L;

  /**
   * Constructs a DependenciesResolveException with the specified detail message.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public DependenciesResolveException(String message, Throwable cause) {
    super(message, cause);
  }
}
