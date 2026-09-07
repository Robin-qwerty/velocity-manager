package com.avesclub.network.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which Velocity backend names participate in network sync.
 * An empty list means all servers are linked.
 */
public final class LinkedServerFilter {

    private final Set<String> linkedServers;

    private LinkedServerFilter(Set<String> linkedServers) {
        this.linkedServers = linkedServers;
    }

    public static LinkedServerFilter all() {
        return new LinkedServerFilter(Set.of());
    }

    public static LinkedServerFilter of(List<String> serverNames) {
        if (serverNames == null || serverNames.isEmpty()) {
            return all();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : serverNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            normalized.add(name.trim().toLowerCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) {
            return all();
        }
        return new LinkedServerFilter(Collections.unmodifiableSet(normalized));
    }

    /** {@code true} when no explicit list was configured (every server is linked). */
    public boolean linksAll() {
        return linkedServers.isEmpty();
    }

    public boolean isLinked(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return false;
        }
        if (linksAll()) {
            return true;
        }
        return linkedServers.contains(serverName.trim().toLowerCase(Locale.ROOT));
    }

    /** Names as configured (lowercase), empty when {@link #linksAll()}. */
    public List<String> configuredNames() {
        if (linksAll()) {
            return List.of();
        }
        return List.copyOf(linkedServers);
    }
}
