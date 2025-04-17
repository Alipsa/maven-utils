package se.alipsa.mavenutils;

/**
 * Custom exception class for handling errors during dependency resolution.
 * This class extends Exception and provides constructors for creating exceptions
 * with a message and/or cause.
 */
public class DependenciesResolveException extends Exception {

  static final long serialVersionUID = 1L;

  public DependenciesResolveException(String message, Throwable cause) {
    super(message, cause);
  }
}
