package se.alipsa.maven;

public class DependenciesResolveException extends Exception {

  static final long serialVersionUID = 1L;

  public DependenciesResolveException(String message, Throwable cause) {
    super(message, cause);
  }
}
