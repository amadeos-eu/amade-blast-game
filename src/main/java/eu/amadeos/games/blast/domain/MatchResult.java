package eu.amadeos.games.blast.domain;

/** How a match ended: the winner, 0 to 3, and the rounds everybody won. */
public record MatchResult(int winner, int[] wins) {
}
