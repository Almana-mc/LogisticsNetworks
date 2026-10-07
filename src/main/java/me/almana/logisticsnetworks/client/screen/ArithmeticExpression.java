package me.almana.logisticsnetworks.client.screen;

import java.math.BigDecimal;

public final class ArithmeticExpression {
    private final String text;
    private int pos;

    private ArithmeticExpression(String text) {
        this.text = text.replace(" ", "");
    }

    // ponytail: double math, exact below 2^53
    public static int evaluate(String text) {
        ArithmeticExpression parser = new ArithmeticExpression(text);
        double value = parser.sum();
        if (parser.pos != parser.text.length() || !Double.isFinite(value))
            throw new NumberFormatException(text);
        return Math.clamp(Math.round(value), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static boolean accepts(char ch) {
        return Character.isDigit(ch) || "+-*/(). ".indexOf(ch) >= 0;
    }

    private double sum() {
        double value = product();
        while (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
            char op = text.charAt(pos++);
            value = op == '+' ? value + product() : value - product();
        }
        return value;
    }

    private double product() {
        double value = factor();
        while (pos < text.length() && (text.charAt(pos) == '*' || text.charAt(pos) == '/')) {
            char op = text.charAt(pos++);
            value = op == '*' ? value * factor() : value / factor();
        }
        return value;
    }

    private double factor() {
        if (pos >= text.length())
            throw new NumberFormatException(text);
        char ch = text.charAt(pos);
        if (ch == '+' || ch == '-') {
            pos++;
            return ch == '-' ? -factor() : factor();
        }
        if (ch == '(') {
            pos++;
            double value = sum();
            if (pos >= text.length() || text.charAt(pos) != ')')
                throw new NumberFormatException(text);
            pos++;
            return value;
        }
        int start = pos;
        while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.'))
            pos++;
        return new BigDecimal(text.substring(start, pos)).doubleValue();
    }
}
