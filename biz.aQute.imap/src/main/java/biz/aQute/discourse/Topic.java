package biz.aQute.discourse;

import java.util.SortedSet;
import java.util.TreeSet;

import org.osgi.dto.DTO;

public class Topic  extends DTO implements Comparable<Topic> {
		public int topic;
		public String title;
		public final SortedSet<Post> posts = new TreeSet<>();
		public String id;

		public Topic(String id) {
			this.id = id;

		}

		@Override
		public int compareTo(Topic o) {
			return posts.first().compareTo(o.posts.first());
		}
	}