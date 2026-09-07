package com.mugloar.solver.strategy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbabilityMapperTest {

	@Test
	void mapsKnownProbabilities() {
		assertEquals(100, ProbabilityMapper.score("Piece of cake"));
		assertEquals(10, ProbabilityMapper.score("Impossible"));
		assertEquals(60, ProbabilityMapper.score("Risky"));
	}

	@Test
	void returnsZeroForUnknownOrNull() {
		assertEquals(0, ProbabilityMapper.score("Made up category"));
		assertEquals(0, ProbabilityMapper.score(null));
	}

	@Test
	void mapsRealApiGambleString() {
		assertEquals(50, ProbabilityMapper.score("Gamble"));
		assertEquals(0, ProbabilityMapper.score("Hmmm...."));
	}

	// ProbabilityMapperTest.java — add these, keep the existing ones
	@Test
	void decodesBase64ObfuscatedProbability() {
		assertEquals(40, ProbabilityMapper.score("UmF0aGVyIGRldHJpbWVudGFs")); // "Rather detrimental"
		assertEquals(20, ProbabilityMapper.score("U3VpY2lkZSBtaXNzaW9u"));      // "Suicide mission"
		assertEquals(60, ProbabilityMapper.score("Umlza3k="));                  // "Risky"
	}

	@Test
	void decodesRot13ObfuscatedProbability() {
		assertEquals(10, ProbabilityMapper.score("Vzcbffvoyr"));       // "Impossible"
		assertEquals(20, ProbabilityMapper.score("Fhvpvqr zvffvba")); // "Suicide mission"
	}

	@Test
	void unknownLiteralCategoryStaysAtZero() {
		assertEquals(0, ProbabilityMapper.score("Hmmm...."));
	}
}