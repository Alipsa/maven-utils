package se.alipsa.mavenutils;

import org.apache.maven.shared.invoker.InvocationOutputHandler;

/**
 * This class implements the InvocationOutputHandler interface to handle
 * output from Maven invocations. It simply prints the output lines to
 * standard output.
 */
public class ConsoleInvocationOutputHandler implements InvocationOutputHandler {

  @Override
  public void consumeLine(String line) {
    System.out.println(line);
  }
}
