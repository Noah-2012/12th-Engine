package net.twelfthengine.core.tick;

public class TickEvent {

    private final TickPhase phase;
    private final double deltaTime;

    public TickEvent(TickPhase phase, double deltaTime) {
        this.phase = phase;
        this.deltaTime = deltaTime;
    }

    public TickPhase getPhase() {
        return phase;
    }

    public double getDeltaTime() {
        return deltaTime;
    }
}