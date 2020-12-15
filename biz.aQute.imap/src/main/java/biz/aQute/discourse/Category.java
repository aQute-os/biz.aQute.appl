package biz.aQute.discourse;

import java.net.URI;

import org.osgi.dto.DTO;


public class Category extends DTO {

	public int id;
	public String name;
	public String color;
	public String text_color;
	public String slug;
	public int post_count;
	public int position;
	public String description;
	public String description_text;
	public String description_excerpt;
	public URI topic_url;
	public boolean read_restricted;
	public int permission;
	public int notification_level;;
	public boolean can_edit;
	public String topic_template;
	public boolean has_children;
	public String sort_order;
	public boolean sort_ascending;
	public boolean show_subcategory_list;
	public int num_featured_topics;
	public String default_view;
	public String subcategory_list_style;
	public String default_top_period;
	public String default_list_filter;
	public int minimum_required_tags;
	public boolean navigate_to_first_post_after_read;
	public int topics_day;
	public int topics_week;
	public int topics_month;
	public int topics_year;
	public int topics_all_time;
	public String uploaded_logo;
	public String uploaded_background;
}
