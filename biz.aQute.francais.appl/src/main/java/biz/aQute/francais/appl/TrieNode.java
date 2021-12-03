package biz.aQute.francais.appl;

import java.util.Map;
import java.util.TreeMap;

import biz.aQute.francais.appl.Lexique.Record;

public class TrieNode {
	public final Map<Character, TrieNode> node=new TreeMap<>();
	public Record r;
	public TrieNode() {
		
	}
	public void add(CharSequence s, Record r) {
		if ( s.length()==0) {
			this.r=r;
			return;
		}
		Character first = s.charAt(0);
		TrieNode next = node.computeIfAbsent(first, x->new TrieNode());
		CharSequence ns = s.subSequence(1, s.length());
		next.add(ns,r);
	}
}
