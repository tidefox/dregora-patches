package io.github.tidefox.dregorapatches.levelup2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;

import org.junit.jupiter.api.Test;

class CandidateCacheTest {

    @Test
    void subclassesOfBasesAreCandidates() {
        CandidateCache c = new CandidateCache(AbstractList.class);
        assertTrue(c.isCandidate(ArrayList.class));
        assertTrue(c.isCandidate(LinkedList.class));
        assertFalse(c.isCandidate(String.class));
        assertTrue(c.isCandidate(ArrayList.class)); // cached, not counted twice
        assertEquals(2, c.candidateClasses());
        assertEquals(0, c.learnedClasses());
    }

    @Test
    void learningAddsClassesOnce() {
        CandidateCache c = new CandidateCache(AbstractList.class);
        assertFalse(c.isCandidate(String.class));
        assertTrue(c.learn(String.class));
        assertTrue(c.isCandidate(String.class));
        assertFalse(c.learn(String.class));
        assertFalse(c.learn(ArrayList.class)); // already a candidate via its base
        assertEquals(1, c.learnedClasses());
        assertEquals(2, c.candidateClasses());
    }
}
