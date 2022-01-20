package se.alipsa.maven;

import org.apache.maven.shared.invoker.InvocationOutputHandler;

public class ConsoleInvocationOutputHandler implements InvocationOutputHandler {

  @Override
  public void consumeLine(String line) {
    System.out.println(line);
  }
}
