package biz.aQute.discourse;

import java.time.Instant;

import org.osgi.dto.DTO;

public class Post extends DTO implements Comparable<Post> {
	public String id;
	public String author;
	public String raw;
	public Instant createdAt;

	@Override
	public int compareTo(Post o) {
		return createdAt.compareTo(o.createdAt);
	}
}