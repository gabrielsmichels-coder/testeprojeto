/* Adapted from Free My TV, Copyright (c) 2026 Free My TV contributors.
 * MIT License; full notice in res/raw/free_my_tv_license.txt.
 * Upstream 62e883b3179c8cafb7a2b09960eabfe7eea6ef1c. */
package com.example.projectorlauncher.adapted;

/** Damped spring, like macOS animations: response (s) and damping (1 = no bounce). */
public final class Spring {
    public float value, velocity, target;
    private final float response, damping;

    public Spring(float response, float damping, float initial) {
        this.response = response;
        this.damping = damping;
        value = target = initial;
    }

    public void snap(float v) {
        value = target = v;
        velocity = 0;
    }

    /** Advances `dt` seconds; returns true while still moving. */
    public boolean step(float dt) {
        if (settled()) {
            value = target;
            velocity = 0;
            return false;
        }
        float omega = (float) (2 * Math.PI / response);
        float stiffness = omega * omega, friction = 2 * damping * omega;
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float accel = -stiffness * (value - target) - friction * velocity;
            velocity += accel * h;
            value += velocity * h;
        }
        return true;
    }

    public boolean settled() {
        return Math.abs(value - target) < 0.0008f && Math.abs(velocity) < 0.004f;
    }
}
