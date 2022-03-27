package se.alipsa.mavenutils;

import org.apache.maven.shared.invoker.InvocationOutputHandler;

public class WarningInvocationOutputHandler implements InvocationOutputHandler {

  @Override
  public void consumeLine(String line) {
    System.err.println(line);
  }
}
