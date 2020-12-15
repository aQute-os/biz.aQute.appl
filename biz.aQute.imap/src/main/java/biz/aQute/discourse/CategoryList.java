package biz.aQute.discourse;

import org.osgi.dto.DTO;

public class CategoryList extends DTO {
	public boolean can_create_category;
	public boolean can_create_topic;
	public String draft;
	public String draft_key;
	public int draft_sequence;
	public Category[] categories;
	
}
