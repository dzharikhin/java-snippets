package org.jrx.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Calculator {

  public static void main(String[] args) {
    Calculator calculator = new Calculator(" ");
    Double result = calculator.calculate("( 5 - 4 ) / 3 ^ - 2 + 1 * 10");
    System.out.println(result);
  }

  @FunctionalInterface
  interface Token<T> {
    void process(Deque<T> stack);
  }

  enum Operation implements Token<Double> {
    ADD("\\+", 2, Associativity.LEFT, true) {
      @Override
      public void process(Deque<Double> stack) {
        Double right = stack.pop();
        Double left = stack.pop();
        stack.push(left + right);
      }
    },
    SUBTRACT("-", 2, Associativity.LEFT, true) {
      @Override
      public void process(Deque<Double> stack) {
        Double right = stack.pop();
        Double left = stack.pop();
        stack.push(left - right);
      }
    },
    MULT("\\*", 3, Associativity.LEFT, true) {
      @Override
      public void process(Deque<Double> stack) {
        Double right = stack.pop();
        Double left = stack.pop();
        stack.push(left * right);
      }
    },
    DIVIDE("/",3, Associativity.LEFT, true) {
      @Override
      public void process(Deque<Double> stack) {
        Double right = stack.pop();
        Double left = stack.pop();
        stack.push(left / right);
      }
    },
    POW("\\^", 4, Associativity.RIGHT, true) {
      @Override
      public void process(Deque<Double> stack) {
        Double right = stack.pop();
        Double left = stack.pop();
        stack.push(Math.pow(left, right));
      }
    },
    NEGATE("-", 5, Associativity.RIGHT, false) {
      @Override
      public void process(Deque<Double> stack) {
        Double arg = stack.pop();
        stack.push(-arg);
      }
    },
    //TODO smells ?
    PRECEDENCE_START("\\(", 6, Associativity.RIGHT, false) {
      @Override
      public void process(Deque<Double> stack) {
        Double arg = stack.pop();
        stack.push(-arg);
      }
    },
    //TODO smells ?
    PRECEDENCE_END("\\)", 1, Associativity.LEFT, true) {
      @Override
      public void process(Deque<Double> stack) {
        Double arg = stack.pop();
        stack.push(-arg);
      }
    };

    private enum Associativity {
      LEFT, RIGHT
    }

    private final Pattern pattern;
    private final int precedence;
    private final Associativity associativity;
    private final boolean binary;

    Operation(String pattern, int precedence, Associativity associativity, boolean binary) {
      this.pattern = Pattern.compile(pattern);
      this.precedence = precedence;
      this.associativity = associativity;
      this.binary = binary;
    }

    public static Optional<Operation> getOperation(String previousToken, String currentToken) {
      return Stream.of(Operation.values()).filter(op -> op.pattern.matcher(currentToken).matches())
        .filter(op -> !op.binary == isOperation(previousToken))
        .findFirst();
    }

    private static boolean isOperation(String token) {
      return token == null || Stream.of(Operation.values()).filter(op -> PRECEDENCE_END != op).anyMatch(op -> op.hasMatch(token));
    }

    private boolean hasMatch(String token) {
      return this.pattern.matcher(token).matches();
    }

    public boolean hasToPushOutFromStack(Operation anotherOperation) {
      return Associativity.LEFT == associativity && Integer.compare(precedence, anotherOperation.precedence) <= 0
          || Associativity.RIGHT == associativity && Integer.compare(precedence, anotherOperation.precedence) < 0;
    }
  }

  public static class Operand implements Token<Double> {

    private final Double value;

    public Operand(String value) {
      this.value = Double.valueOf(value);
    }

    @Override
    public void process(Deque<Double> stack) {
      stack.push(value);
    }

    @Override
    public String toString() {
      return "Operand{" +
          "value=" + value +
          '}';
    }
  }

  private final Pattern tokenSeparator;

  public Calculator(String tokenSeparator) {
    this.tokenSeparator = Pattern.compile(tokenSeparator);
  }

  public Double calculate(String expression) {
    List<Token<Double>> result = tokenize(expression);

    Deque<Double> dataStack = new ArrayDeque<>();
    result.forEach(token -> token.process(dataStack));
    return dataStack.pop();
  }

  private List<Token<Double>> tokenize(String expression) {
    String[] tokenStrings = tokenSeparator.split(expression);

    Deque<Operation> operationStack = new ArrayDeque<>();
    List<Token<Double>> result = new ArrayList<>(tokenStrings.length);

    String previousTokenString = null;
    for (String tokenString : tokenStrings) {
      Optional<Operation> operation = Operation.getOperation(previousTokenString, tokenString);
      if (operation.isPresent()) {
        processOperation(result, operation.get(), operationStack);
      } else {
        result.add(new Operand(tokenString));
      }
      previousTokenString = tokenString;
    }
    result.addAll(operationStack);
    return result;
  }

  private static void processOperation(List<Token<Double>> result, Operation currentOperation, Deque<Operation> operationStack) {
    while (!operationStack.isEmpty()) {
      Operation operationOnStack = operationStack.peek();
      if (currentOperation.hasToPushOutFromStack(operationOnStack)) {
        Operation op = operationStack.pop();
        //TODO smells
        if (Operation.PRECEDENCE_START != op) {
          result.add(op);
        }
        continue;
      }
      break;
    }
    //TODO smells
    if (Operation.PRECEDENCE_END != currentOperation) {
      operationStack.push(currentOperation);
    }
  }
}
