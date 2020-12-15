package biz.aQute.imap;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.URLName;

import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import biz.aQute.discourse.Post;
import biz.aQute.discourse.Topic;

public class MBoxFileReader {
	final File path; // File to .mbox file

	public MBoxFileReader(File path) {
		this.path = path;
	}

	public Message[] readMessages() {
		Message[] messages = new Message[0];
		URLName server = new URLName("mbox:" + path.toString());
		Properties props = new Properties();
		props.setProperty("mail.mime.address.strict", "false");
		Session session = Session.getDefaultInstance(props);
		try {
			Folder folder = session.getFolder(server);
			folder.open(Folder.READ_ONLY);
			messages = folder.getMessages();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return messages;
	}

	public static void main(String[] args) throws MessagingException, IOException {
		MBoxFileReader mbfr = new MBoxFileReader(IO.getFile("/Users/aqute/Desktop/bndtools-mails/bndtools 2.mbox/mbox"));
		Message[] messages = mbfr.readMessages();
		
		Map<String, Message> hierarchy = parseHierarchy(messages);
		Map<String, Topic> topics = post(hierarchy,messages);
		
		for ( Topic topic : topics.values()) {
			if ( topic.title== null) {
				Post first = topic.posts.first();
				topic.title = hierarchy.get(first.id).getSubject(); 
			}
			System.out.printf("%-4d %s\n",topic.posts.size(), topic.title);
		}
		
	}

	private static Map<String, Topic> post(Map<String, Message> hierarchy, Message[] messages)
			throws MessagingException, IOException {
		Map<String, Topic> topics = new HashMap<>();

		for (Message msg : hierarchy.values()) {
			String messageId = getMessageId(msg);
			String topId = getTop(hierarchy, messageId);

			Post post = new Post();
			post.author = getFrom(msg);
			post.raw = getContent(msg);
			post.createdAt = msg.getSentDate().toInstant();
			post.id = getMessageId(msg);

			Topic topic = topics.computeIfAbsent(topId, Topic::new);
			if (messageId.equals(topId)) {
				topic.title = msg.getSubject();
			}
			topic.posts.add(post);
		}
		return topics;
	}

	private static Map<String, Message> parseHierarchy(Message[] messages) throws MessagingException {
		Map<String, Message> hierarchy = new HashMap<>();
		for (int i = messages.length - 1; i >= 0; i--) {
			Message msg = messages[i];
			String array[] = msg.getHeader("Message-ID");
			if (array == null) {
				System.err.println("failing because no id " + msg);
				continue;
			}
			String id = array[0];
			hierarchy.put(id, msg);
		}
		return hierarchy;
	}

	private static String getTop(Map<String, Message> hierarchy, String id) throws MessagingException {
		Set<String> visited = new HashSet<>();
		while (true) {
			if (visited.contains(id))
				return null;

			visited.add(id);

			Message m = hierarchy.get(id);
			if (m == null)
				return id;

			String[] replyto = m.getHeader("In-Reply-To");
			if (replyto == null || replyto.length == 0)
				return getMessageId(m);

			id = replyto[0];
		}
	}

	final static Pattern WROTE = Pattern.compile("On .+ wrote:\\s*");

	final static String GOOGLE_S = "--\\s*\n"
			+ "\\s*You received this message because you are subscribed to the Google Groups";
	final static Pattern GOOGLE_P = Pattern.compile(GOOGLE_S,Pattern.CASE_INSENSITIVE);
	
	private static String cleanup(String content) {
		
			
		StringBuilder sb = new StringBuilder(content.length());
		Matcher m = GOOGLE_P.matcher(content);
		if (m.find()) {
			content = content.substring(0, m.start());
		}
		
		String[] split = content.split("\r?\n");
		int i;
		for (i = split.length - 1; i >= 0; i--) {
			String s = split[i];

			split[i] = Strings.trim(split[i]);
			if (split[i].isEmpty())
				continue;

			if (split[i].startsWith(">"))
				continue;
			m = WROTE.matcher(s);
			if (m.matches())
				continue;

			i++;
			break;
		}
		for (int j = 0; j < i; j++) {
			sb.append(split[j]).append("\n");
		}
		return sb.toString();
	}

	private static String getContent(Part part) throws MessagingException, IOException {
		String type = part.getContentType().toLowerCase();
		if (type.startsWith("multipart/")) {
			Multipart mp = (Multipart) part.getContent();
			for (int p = 0; p < mp.getCount(); p++) {
				String s = getContent(mp.getBodyPart(p));
				if (s != null)
					return cleanup(s);
			}
			return null;
		} else if (type.startsWith("text/plain")) {
			return (String) part.getContent();
		} else
			return null;
	}

	static void print(String mimeType, Object content, String indent) throws MessagingException, IOException {
		System.out.println(indent + "MIME: " + mimeType + " " + content.getClass().getSimpleName());
		if (mimeType.startsWith("multipart/")) {
			Multipart mp = (Multipart) content;
			for (int pi = 0; pi < mp.getCount(); pi++) {
				BodyPart bodyPart = mp.getBodyPart(pi);
				String subtype = bodyPart.getContentType().toLowerCase();
				print(subtype, bodyPart.getContent(), indent + "  ");
			}
		} else
			;
//			System.out.println(indent + "Body: \n" + content);
	}

	private static String getFrom(Message msg) throws MessagingException {
		return msg.getFrom()[0].toString();
	}

	private static String getMessageId(Message msg) throws MessagingException {
		return msg.getHeader("Message-ID")[0];
	}

}