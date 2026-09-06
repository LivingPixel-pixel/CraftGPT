package dev.craftgpt.client.codex;

public final class CodexRunException extends RuntimeException {
    public enum Code {
        NOT_INSTALLED,
        CHATGPT_LOGIN_REQUIRED,
        PROCESS_FAILED,
        RESULT_MISSING,
        CANCELLED
    }

    private final Code code;

    CodexRunException(Code code, String message) {
        super(message);
        this.code = code;
    }

    CodexRunException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
