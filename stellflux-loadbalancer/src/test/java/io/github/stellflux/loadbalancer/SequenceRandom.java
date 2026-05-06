package io.github.stellflux.loadbalancer;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

final class SequenceRandom extends Random {

    private final Queue<Integer> intValues = new ArrayDeque<>();

    SequenceRandom(int... values) {
        for (int value : values) {
            this.intValues.add(value);
        }
    }

    @Override
    public int nextInt(int bound) {
        if (this.intValues.isEmpty()) {
            return super.nextInt(bound);
        }
        return Math.floorMod(this.intValues.remove(), bound);
    }
}
