package biz.aQute.francais.appl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import aQute.lib.io.IO;
import aQute.lib.strings.Strings;

public class Lexique {
	public enum NOMBRE {
		s, p
	}

	public enum GENRE {
		f, m
	}

	public enum CGRAM {
		NOM, VER, ADJ, ADV, AUX, PRE, ONO, CON, ART_def, ART_ind, ADJ_num, PRO_per, ADJ_ind, PRO_ind, PRO_int, PRO_rel, PRO_dem, ADJ_dem, LIA, PRO_pos, ADJ_pos, NONE, ADJ_int
	}

	class Record {
		// ortho phon lemme cgram genre nombre freqlemfilms2 freqlemlivres
		// freqfilms2 freqlivres infover nbhomogr nbhomoph islem nblettres
		// nbphons cvcv p_cvcv voisorth voisphon puorth puphon syll nbsyll cv-cv
		// orthrenv phonrenv orthosyll cgramortho deflem defobs old20 pld20
		// morphoder nbmorph

		public String	ortho;
		public String	phon;
		public String	lemme;
		public CGRAM	cgram;
		public GENRE	genre;
		public NOMBRE	nombre;
		public String	infover;

		public String toString() {
			switch (cgram) {
			case NOM:
				return ortho + " " + genre + nombre;

			default:
				return ortho + " " + cgram + genre + nombre;
			}
		}
	}

	final List<Record> records = new ArrayList<>();
	final char[] charset;

	public Lexique(BufferedReader reader) throws Exception {
		reader.readLine().split("\t");
		Set<Character> charset = new TreeSet<>();
		String line;
		while ((line = reader.readLine()) != null) {
			String[] array = line.split("\t");
			
			for ( int i=0; i<array[0].length();i++) {
				charset.add(array[0].charAt(i));
			}
			Record r = record(array);
			records.add(r);
		}
		this.charset=new char[charset.size()];
		Character[] array = charset.toArray(new Character[0]);
		for(int i=0; i<array.length; i++) {
			this.charset[i]=array[i];
		}
	}

	public Lexique(InputStream in) throws Exception {
		this(IO.reader(in));
	}

	public Lexique(String in) throws Exception {
		this(IO.reader(IO.getFile(in)));
	}

	public Lexique() throws Exception {
		this("Lexique383.tsv");
	}

	Record record(String[] array) {
		Record r = new Record();
		r.ortho = array[0];
		r.lemme = array[2];
		String cgram = array[3].replace(':', '_');
		if (!Strings.nonNullOrEmpty(cgram))
			cgram = "NONE";
		r.cgram = CGRAM.valueOf(cgram);
		if (Strings.nonNullOrEmpty(array[4]))
			r.genre = GENRE.valueOf(array[4]);
		if (Strings.nonNullOrEmpty(array[5]))
			r.nombre = NOMBRE.valueOf(array[5]);
		r.infover = array[10];
		return r;
	}

	public int size() {
		return records.size();
	}

	public Stream<Record> filter(Predicate<Record> pred) {
		return records.stream().filter(pred);
	}

	public char[] getCharset() {
		return charset;
	}
}
