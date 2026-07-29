package com.example.caculator;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Maximum digits the user can enter for a single number
    private static final int MAX_DIGITS = 9;

    // Animation durations (ms)
    private static final int ANIM_PRESS_MS   = 80;
    private static final int ANIM_RELEASE_MS = 120;
    private static final float SCALE_PRESSED = 0.91f;

    // The four supported arithmetic operations
    private enum Op { NONE, ADD, SUB, MUL, DIV }

    // ---- Core state machine fields ----
    private String currentInput = "0";     // string currently shown on the display
    private double operandOne = 0;         // first operand already committed
    private Op pendingOp = Op.NONE;        // operator waiting to be applied
    private boolean startNewNumber = true; // true if the next digit press should start a fresh number
    private boolean hasError = false;      // true while the calculator is in the Error state

    // ---- Repeat-equals support (pressing "=" repeatedly re-applies the last operation, iOS-style) ----
    private double lastOperand = 0;        // second operand used in the previous "=" press
    private Op lastOp = Op.NONE;           // operator used in the previous "=" press

    // ---- Expression line state (shown above the result, Google Calculator style) ----
    private String expressionText = "";      // completed expression text, e.g. "6 + 3 +"
    private String highlightOpSymbol = null; // symbol of the currently active operator, used for highlighting

    private TextView display;
    private TextView expressionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // App background is white, so use dark status bar icons (otherwise they blend
        // into the white content that Android 15+ draws edge-to-edge behind the status bar).
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        display = findViewById(R.id.display);
        expressionView = findViewById(R.id.expression);

        // Configure display to auto-shrink text for long numbers (API 14+ compat)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                display, 20, 72, 2, TypedValue.COMPLEX_UNIT_SP);

        // Wire digit buttons
        setupDigitButton(R.id.btn_0, "0");
        setupDigitButton(R.id.btn_1, "1");
        setupDigitButton(R.id.btn_2, "2");
        setupDigitButton(R.id.btn_3, "3");
        setupDigitButton(R.id.btn_4, "4");
        setupDigitButton(R.id.btn_5, "5");
        setupDigitButton(R.id.btn_6, "6");
        setupDigitButton(R.id.btn_7, "7");
        setupDigitButton(R.id.btn_8, "8");
        setupDigitButton(R.id.btn_9, "9");

        // Wire function / operator buttons
        ((Button) findViewById(R.id.btn_dot)).setOnClickListener(v -> onDecimalPoint());
        ((Button) findViewById(R.id.btn_ac)).setOnClickListener(v -> onClear());
        ((Button) findViewById(R.id.btn_sign)).setOnClickListener(v -> onToggleSign());
        ((Button) findViewById(R.id.btn_percent)).setOnClickListener(v -> onPercent());
        ((Button) findViewById(R.id.btn_equals)).setOnClickListener(v -> onEquals());

        ((Button) findViewById(R.id.btn_add)).setOnClickListener(v -> onOperator(Op.ADD));
        ((Button) findViewById(R.id.btn_subtract)).setOnClickListener(v -> onOperator(Op.SUB));
        ((Button) findViewById(R.id.btn_multiply)).setOnClickListener(v -> onOperator(Op.MUL));
        ((Button) findViewById(R.id.btn_divide)).setOnClickListener(v -> onOperator(Op.DIV));

        // Apply scale press-animation to every button
        int[] buttonIds = {
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9,
            R.id.btn_dot, R.id.btn_ac, R.id.btn_sign, R.id.btn_percent,
            R.id.btn_equals, R.id.btn_add, R.id.btn_subtract,
            R.id.btn_multiply, R.id.btn_divide
        };
        for (int id : buttonIds) {
            addPressAnimation(findViewById(id));
        }

        updateDisplay();
        updateExpressionDisplay();
    }

    // -------------------------------------------------------------------------
    // Button helpers
    // -------------------------------------------------------------------------

    private void setupDigitButton(int id, String digit) {
        ((Button) findViewById(id)).setOnClickListener((View v) -> onDigit(digit));
    }

    /**
     * Attaches a subtle scale-down animation on press and scale-up on release.
     * Returns false so the click listener still fires normally.
     */
    private void addPressAnimation(View v) {
        v.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate()
                        .scaleX(SCALE_PRESSED)
                        .scaleY(SCALE_PRESSED)
                        .setDuration(ANIM_PRESS_MS)
                        .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(ANIM_RELEASE_MS)
                        .start();
                    break;
            }
            // Return false so click events are NOT consumed — onClick still fires
            return false;
        });
    }

    // -------------------------------------------------------------------------
    // Input handlers
    // -------------------------------------------------------------------------

    /** Handles a digit (0-9) press. Enforces the MAX_DIGITS limit per number. */
    private void onDigit(String d) {
        if (hasError) return;

        if (startNewNumber) {
            if (pendingOp == Op.NONE) {
                // Starting a brand new calculation → clear the old expression line
                clearExpression();
            }
            currentInput = d;
            startNewNumber = false;
        } else if (currentInput.equals("0")) {
            // Replace the leading zero instead of appending (avoid "05")
            currentInput = d;
        } else {
            // Enforce the digit limit (not counting the minus sign or decimal point)
            if (countDigits(currentInput) >= MAX_DIGITS) return;
            currentInput = currentInput + d;
        }
        updateDisplay();
        updateExpressionDisplay();
    }

    /** Handles the decimal point press. Only one decimal point per number is allowed. */
    private void onDecimalPoint() {
        if (hasError) return;

        if (startNewNumber) {
            if (pendingOp == Op.NONE) {
                clearExpression();
            }
            currentInput = "0.";
            startNewNumber = false;
        } else if (!currentInput.contains(".")) {
            // Guard: only add a decimal point if the number doesn't already have one
            if (countDigits(currentInput) < MAX_DIGITS) {
                currentInput = currentInput + ".";
            }
        }
        // If a "." is already present, silently ignore the press (prevents "3.1.4")
        updateDisplay();
        updateExpressionDisplay();
    }

    /** Handles +, −, ×, ÷ presses. Pressing an operator again just swaps the pending one. */
    private void onOperator(Op op) {
        if (hasError) return;
        String opSymbol = symbolFor(op);

        if (pendingOp != Op.NONE && !startNewNumber) {
            // A pending operation exists and the user just finished entering the
            // second operand → evaluate the intermediate result first
            // (chained expressions, e.g. "5 + 3 ×")
            double b = parseDouble(currentInput);
            try {
                double result = calculate(operandOne, b, pendingOp);
                expressionText = expressionText + " " + currentInput + " " + opSymbol;
                operandOne = result;
                currentInput = formatResult(result);
            } catch (ArithmeticException e) {
                setError();
                return;
            }
        } else if (pendingOp != Op.NONE) {
            // User changed their mind right after picking an operator → replace it
            expressionText = replaceLastOperator(expressionText, opSymbol);
        } else {
            // No pending operation yet → start a new expression line
            operandOne = parseDouble(currentInput);
            expressionText = formatResult(operandOne) + " " + opSymbol;
        }

        pendingOp = op;
        highlightOpSymbol = opSymbol;
        startNewNumber = true;
        updateDisplay();
        updateExpressionDisplay();
    }

    /**
     * Handles the "=" press and refreshes the display with the result.
     * First press: evaluates the pending operation.
     * Subsequent presses: repeats the last operation (iOS-style),
     * e.g. 5 + 5 = → 10, = → 15, = → 20.
     */
    private void onEquals() {
        if (hasError) return;

        if (pendingOp == Op.NONE) {
            // No new operation queued — try to repeat the last completed one
            if (lastOp == Op.NONE) {
                // Nothing has been calculated yet → just clear the expression line
                clearExpression();
                updateExpressionDisplay();
                return;
            }
            // Repeat: current result becomes operand one, lastOperand is operand two
            double a = parseDouble(currentInput);
            try {
                double result = calculate(a, lastOperand, lastOp);
                String opSymbol = symbolFor(lastOp);
                expressionText = formatResult(a) + " " + opSymbol
                        + " " + formatResult(lastOperand) + " =";
                currentInput = formatResult(result);
            } catch (ArithmeticException e) {
                setError();
                return;
            }
            highlightOpSymbol = null;
            startNewNumber = true;
            updateDisplay();
            updateExpressionDisplay();
            return;
        }

        // First "=" press: evaluate the pending operation.
        // If the second operand was never entered, currentInput still holds a
        // valid number (the first operand), so this never crashes.
        double b = parseDouble(currentInput);
        try {
            double result = calculate(operandOne, b, pendingOp);
            expressionText = expressionText + " " + currentInput + " =";
            // Remember this operation so a later repeated "=" press can reuse it
            lastOperand = b;
            lastOp = pendingOp;
            currentInput = formatResult(result);
            operandOne = result;
        } catch (ArithmeticException e) {
            setError();
            return;
        }

        pendingOp = Op.NONE;
        highlightOpSymbol = null;
        startNewNumber = true;
        updateDisplay();
        updateExpressionDisplay();
    }

    /** Handles the AC press: resets all state back to the initial values. */
    private void onClear() {
        currentInput = "0";
        operandOne = 0;
        pendingOp = Op.NONE;
        lastOp = Op.NONE;
        lastOperand = 0;
        startNewNumber = true;
        hasError = false;
        clearExpression();
        updateDisplay();
        updateExpressionDisplay();
    }

    /** Handles the +/- press: flips the sign of the number currently on display. */
    private void onToggleSign() {
        if (hasError) return;
        if (currentInput.equals("0")) return;
        if (currentInput.startsWith("-")) {
            currentInput = currentInput.substring(1);
        } else {
            currentInput = "-" + currentInput;
        }
        updateDisplay();
    }

    /** Handles the % press: divides the number currently on display by 100. */
    private void onPercent() {
        if (hasError) return;
        double value = parseDouble(currentInput) / 100.0;
        currentInput = formatResult(value);
        updateDisplay();
    }

    // -------------------------------------------------------------------------
    // Core calculation
    // -------------------------------------------------------------------------

    /**
     * Core two-operand calculation for the given operator.
     * Division by zero throws ArithmeticException; the caller is responsible
     * for switching the calculator into the Error state.
     */
    private double calculate(double a, double b, Op op) {
        switch (op) {
            case ADD: return a + b;
            case SUB: return a - b;
            case MUL: return a * b;
            case DIV:
                // Validate the divisor before dividing to avoid producing Infinity/NaN
                if (b == 0) throw new ArithmeticException("Division by zero");
                return a / b;
            default:  return b;
        }
    }

    /** Switches the calculator into the Error state (e.g. after a division by zero). */
    private void setError() {
        hasError = true;
        currentInput = getString(R.string.error);
        updateDisplay();
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    /**
     * Formats a numeric result for display:
     *  - Integer values → drop the trailing ".0" (e.g. 4.0 → "4")
     *  - Decimal values → up to 9 decimal places, trailing zeros stripped
     *  - Very large/small magnitudes → scientific notation (e.g. "1.23e9")
     *  - NaN / Infinity → "Error"
     */
    private String formatResult(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return getString(R.string.error);
        }

        // Integer result — show without decimal point
        if (value == Math.floor(value) && Math.abs(value) < 1_000_000_000L) {
            return String.valueOf((long) value);
        }

        // Very large integer — use scientific notation to fit on screen
        if (Math.abs(value) >= 1_000_000_000L) {
            String sci = String.format(Locale.US, "%.6e", value);
            // Clean up trailing zeros in the mantissa (e.g. 1.200000e9 → 1.2e9)
            sci = sci.replaceAll("(\\.[0-9]*?)0+(e)", "$1$2")
                     .replaceAll("\\.e", "e");
            return sci;
        }

        // Decimal — up to 9 decimal places, strip trailing zeros
        String s = String.format(Locale.US, "%.9f", value);
        s = s.replaceAll("0+$", "");
        s = s.replaceAll("\\.$", "");
        return s;
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Counts only digit characters (0-9) in a string,
     * ignoring '-' and '.'. Used to enforce the MAX_DIGITS limit.
     */
    private int countDigits(String s) {
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') count++;
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Display update
    // -------------------------------------------------------------------------

    /** Pushes currentInput to the main result TextView. */
    private void updateDisplay() {
        display.setText(currentInput);
    }

    /** Returns the display symbol for the given operator. */
    private String symbolFor(Op op) {
        switch (op) {
            case ADD: return getString(R.string.btn_add);
            case SUB: return getString(R.string.btn_subtract);
            case MUL: return getString(R.string.btn_multiply);
            case DIV: return getString(R.string.btn_divide);
            default:  return "";
        }
    }

    /** Replaces the trailing operator in the expression line when the user changes
     *  their mind (e.g. "6 +" → "6 ×"). */
    private String replaceLastOperator(String text, String newOpSymbol) {
        int lastSpace = text.lastIndexOf(' ');
        if (lastSpace == -1) return newOpSymbol;
        return text.substring(0, lastSpace + 1) + newOpSymbol;
    }

    /** Clears the expression line shown above the main display. */
    private void clearExpression() {
        expressionText = "";
        highlightOpSymbol = null;
    }

    /**
     * Pushes expressionText to the TextView above the main display, highlighting
     * the currently active operator (highlightOpSymbol) in the style of Google Calculator.
     */
    private void updateExpressionDisplay() {
        if (expressionView == null) return;
        if (expressionText.isEmpty()) {
            expressionView.setText("");
            return;
        }
        if (highlightOpSymbol == null) {
            expressionView.setText(expressionText);
            return;
        }
        int idx = expressionText.lastIndexOf(highlightOpSymbol);
        if (idx < 0) {
            expressionView.setText(expressionText);
            return;
        }
        SpannableString spannable = new SpannableString(expressionText);
        int end = idx + highlightOpSymbol.length();
        spannable.setSpan(
            new BackgroundColorSpan(ContextCompat.getColor(this, R.color.calc_expression_op_highlight_bg)),
            idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(
            new ForegroundColorSpan(ContextCompat.getColor(this, R.color.calc_red)),
            idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(
            new StyleSpan(Typeface.BOLD),
            idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        expressionView.setText(spannable);
    }
}
