package biz.aQute.francais.appl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import aQute.lib.strings.Strings;
import biz.aQute.francais.appl.Lexique.CGRAM;
import biz.aQute.francais.appl.Lexique.NOMBRE;
import biz.aQute.francais.appl.Lexique.Record;

public class LexiqueTest {

	@Test
	public void testSimple() throws Exception {
		Lexique l = new Lexique();

		assertThat(l.size()).isEqualTo(142_694);

	}

	@Test
	public void getNOM() throws Exception {
		Lexique l = new Lexique();
		TrieNode trie = new TrieNode();
		Set<Record> noms = l.filter(r -> r.cgram == CGRAM.NOM && r.nombre == NOMBRE.s && r.genre != null)
				.collect(Collectors.toSet());

		noms.forEach(r -> trie.add(reverse(r.ortho), r));

		System.out.println(Strings.join("\n", noms));

	}

	private CharSequence reverse(String ortho) {
		return new StringBuilder(ortho).reverse();
	}
}
