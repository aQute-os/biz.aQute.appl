package biz.aQute.discourse;

import org.osgi.dto.DTO;

// See https://docs.discourse.org/#tag/Posts/paths/~1posts.json/post

public class PostTopic extends DTO {

	// required if creating a new topic or new private message
	public String title;	


	// required if creating a new post
	public String topic_id;	

	public String raw;

	// optional if creating a new topic, ignored if creating a new post
	public String category;	


	// required for private message, comma separated
	public String target_recipients;	

	// required for private message  	Value: "private_message"
	public String archetype;	

	
	// pick a date other than the default current time
	public String created_at;	

}
