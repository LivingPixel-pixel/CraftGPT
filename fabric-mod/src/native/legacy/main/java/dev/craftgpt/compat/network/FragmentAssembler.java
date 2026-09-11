package dev.craftgpt.compat.network;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Ordered, bounded reassembly. One transfer per connection, with expiry and a global memory cap. */
public final class FragmentAssembler<K> {
    public static final int MAX_MESSAGE_BYTES = 2_100_000;
    private static final int MAX_BUFFERED_BYTES = 32 * 1024 * 1024;
    private static final long EXPIRY_NANOS = 30_000_000_000L;
    private final Map<K, Pending> pending = new HashMap<>();
    private final LongSupplier clock;
    public FragmentAssembler() { this(System::nanoTime); }
    FragmentAssembler(LongSupplier clock) { this.clock = clock; }

    public synchronized byte[] accept(K peer, WireFrame frame, int maximum) {
        long now = clock.getAsLong();
        pending.values().removeIf(value -> now - value.started > EXPIRY_NANOS);
        if (frame.total() > maximum) { pending.remove(peer); throw new IllegalArgumentException("Packet exceeds its registered limit"); }
        Pending value = pending.get(peer);
        if (frame.offset() == 0) {
            if (value != null) { pending.remove(peer); throw new IllegalArgumentException("Overlapping packet transfer"); }
            long used = pending.values().stream().mapToLong(item -> item.bytes.length).sum();
            if (used + frame.total() > MAX_BUFFERED_BYTES) throw new IllegalArgumentException("CraftGPT transfer capacity reached");
            value = new Pending(frame, now);
            pending.put(peer, value);
        }
        if (value == null || value.offset != frame.offset() || !value.first.transfer().equals(frame.transfer())
            || !value.first.channel().equals(frame.channel()) || value.bytes.length != frame.total()) {
            pending.remove(peer);
            throw new IllegalArgumentException("Out-of-order CraftGPT frame");
        }
        byte[] bytes = frame.bytes();
        System.arraycopy(bytes, 0, value.bytes, value.offset, bytes.length);
        value.offset += bytes.length;
        if (value.offset == value.bytes.length) { pending.remove(peer); return value.bytes; }
        return null;
    }
    public synchronized void clear(K peer) { pending.remove(peer); }
    public synchronized void clear() { pending.clear(); }
    private static final class Pending {
        final WireFrame first;
        final long started;
        final byte[] bytes;
        int offset;
        Pending(WireFrame first, long started) { this.first = first; this.started = started; this.bytes = new byte[first.total()]; }
    }
}
