package com.mugloar.solver.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;

public final class ProbabilityMapper {

	private static final Logger log = LoggerFactory.getLogger(ProbabilityMapper.class);

	private static final Map<String, Integer> SCORES = Map.ofEntries(
			Map.entry("Piece of cake", 100),
			Map.entry("Sure thing", 90),
			Map.entry("Walk in the park", 80),
			Map.entry("Quite likely", 70),
			Map.entry("Risky", 60),
			Map.entry("Gamble", 50),
			Map.entry("Rather detrimental", 40),
			Map.entry("Playing with fire", 30),
			Map.entry("Suicide mission", 20),
			Map.entry("Impossible", 10)
	);

	private ProbabilityMapper() {}

	public static int score(String probability) {
		if (probability == null) return 0;

		String resolved = resolveCategory(probability);
		Integer known = SCORES.get(resolved);
		if (known == null) {
			log.warn("Unresolvable probability category: '{}' (tried plain/base64/rot13) — defaulting to 0", probability);
			return 0;
		}
		return known;
	}

	private static String resolveCategory(String raw) {
		if (SCORES.containsKey(raw)) {
			return raw;
		}

		String base64Decoded = tryBase64Decode(raw);
		if (base64Decoded != null && SCORES.containsKey(base64Decoded)) {
			return base64Decoded;
		}

		String rot13Decoded = rot13(raw);
		if (SCORES.containsKey(rot13Decoded)) {
			return rot13Decoded;
		}

		return raw;
	}

	private static String tryBase64Decode(String input) {
		try {
			byte[] decoded = Base64.getDecoder().decode(input);
			return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String rot13(String input) {
		StringBuilder sb = new StringBuilder(input.length());
		for (char c : input.toCharArray()) {
			if (c >= 'a' && c <= 'z') {
				sb.append((char) ('a' + (c - 'a' + 13) % 26));
			} else if (c >= 'A' && c <= 'Z') {
				sb.append((char) ('A' + (c - 'A' + 13) % 26));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}