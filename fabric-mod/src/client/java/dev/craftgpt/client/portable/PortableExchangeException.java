package dev.craftgpt.client.portable;

public final class PortableExchangeException extends RuntimeException {
    public PortableExchangeException(String message) {
        super(message);
    }

    public PortableExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
