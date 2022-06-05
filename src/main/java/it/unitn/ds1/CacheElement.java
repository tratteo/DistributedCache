package it.unitn.ds1;

public class CacheElement {
    public final int evictTime;
    private int value;
    private int currentTime;

    public CacheElement(int value, int evictTime) {
        currentTime = 0;
        this.evictTime = evictTime;
        this.value = value;
    }

    public float evictRatio() {
        return 1F - ((float) currentTime / evictTime);
    }

    public boolean isValid() {
        return currentTime < evictTime;
    }

    public void refreshed(int value) {
        currentTime = 0;
        this.value = value;
    }

    public int value() {
        return value;
    }

    public void step(int deltaTime) {
        currentTime += deltaTime;
        currentTime = Math.min(currentTime, evictTime);
    }

}
