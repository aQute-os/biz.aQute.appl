package biz.aQute.discourse;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import aQute.bnd.http.HttpClient;
import aQute.bnd.service.url.TaggedData;
import aQute.bnd.service.url.URLConnectionHandler;
import aQute.lib.json.JSONCodec;

public class DiscourseAPI {
	static final JSONCodec codec = new JSONCodec();
	final HttpClient client = new HttpClient();
	final URI url;

	public DiscourseAPI(String userId, String apiKey, URI url) {
		this.url = url;
		client.addURLConnectionHandler(new URLConnectionHandler() {

			@Override
			public void handle(URLConnection connection) throws Exception {
				HttpsURLConnection h = (HttpsURLConnection) connection;
				h.setRequestProperty("Api-Key", "2b58e21b9a26a5acad104d2b14ad5bb80228e82b2612ae7dfcf7fad804bba448");
				h.setRequestProperty("Api-Username", "system");
			}

			@Override
			public boolean matches(URL url) {
				return true;
			}
		});

	}
	public List<Category>		categories() throws URISyntaxException, Exception {
		Categories go = client.build().asString().get(Categories.class)
				.go(url.resolve("categories.json"));
		
		return Arrays.asList(go.category_list.categories);
	}
	
	
	public void createTopic(Topic topic, String category) throws Exception {
		PostTopic p = new PostTopic();
		p.title = topic.title;
		Post first = topic.posts.first();
		p.raw = first.raw + "\n\n" + first.author + "\n";
		p.category = category;
		p.created_at = first.createdAt.toString();

		PostTopicResponse response = client.build().post().headers("Content-Type", "application/json").upload(topic).get(PostTopicResponse.class)
				.go(new URI("https://bnd.discourse.group/posts.json"));
		System.out.println(response);

		int topic_id = response.topic_id;
		for ( Post post : topic.posts) {
			if ( post != first) {
				p = new PostTopic();
				p.topic_id=Integer.toString(topic_id);
				p.raw = post.raw + "\n\n" + post.author + "\n";
				p.category = category;
				p.created_at = post.createdAt.toString();
				response = client.build().post().headers("Content-Type", "application/json").upload(topic).get(PostTopicResponse.class)
						.go(new URI("https://bnd.discourse.group/posts.json"));
				System.out.println(response);
			}
		}
	}

	public static void main(String[] args) throws URISyntaxException, Exception {
		try (HttpClient client = new HttpClient()) {
			client.addURLConnectionHandler(new URLConnectionHandler() {

				@Override
				public void handle(URLConnection connection) throws Exception {
					HttpsURLConnection h = (HttpsURLConnection) connection;
					h.setRequestProperty("Api-Key", "2b58e21b9a26a5acad104d2b14ad5bb80228e82b2612ae7dfcf7fad804bba448");
					h.setRequestProperty("Api-Username", "system");
				}

				@Override
				public boolean matches(URL url) {
					return true;
				}
			});

			Categories go = client.build().asString().get(Categories.class)
					.go(new URI("https://bnd.discourse.group/categories.json"));
			for (Category c : go.category_list.categories) {
				System.out.printf("%40s %s\n", c.name, c.id);
			}

			PostTopic topic = new PostTopic();
			topic.topic_id = "41";
			topic.raw = "And this is a reply it must be at least 20 characters xxx";

			String s = codec.enc().put(topic).toString();

			System.out.println(s);
			TaggedData go2 = client.build().post().headers("Content-Type", "application/json").upload(topic).asTag()
					.go(new URI("https://bnd.discourse.group/posts.json"));
			System.out.println(go2);

			System.out.println(new JSONCodec().dec().from(go2.getInputStream()).get(PostTopicResponse.class));
		}

	}

}
