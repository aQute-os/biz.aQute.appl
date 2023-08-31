package biz.aQute.mbox;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TBBToMboxConverter {

	private static final byte[]	BINARY_PREFIX		= { (byte) 0x21, (byte) 0x09, (byte) 0x70, (byte) 0x19, (byte) 0x30,
			(byte) 0x00, (byte) 0x00, (byte) 0x00 };
	private static final int	HEADER_LENGTH		= 48;
	private static final String	MBOX_FROM_PREFIX	= "From - ";

	public static void main(String[] args) throws IOException {

		Path sourceDir = Paths.get("/Users/aqute/Downloads/pkriens_old_mail/");
		Path outputPath = Paths
				.get("/Users/aqute/Library/Thunderbird/Profiles/h8wnynzf.default/Mail/Local Folders/life.mbox");

		try (OutputStream x = new FileOutputStream(outputPath.toFile());
				OutputStreamWriter fout = new OutputStreamWriter(x, StandardCharsets.UTF_8)) {

			Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".TBB")) {

						convertTBBToMbox(file, fout);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	private static void convertTBBToMbox(Path tbbPath, Writer fout) throws IOException {
		System.out.println("process " + tbbPath);
		byte[] data = Files.readAllBytes(tbbPath);
		List<Integer> positions = getPositionsOfBinaryPrefix(data);
		positions.add(data.length); // Add the end of the file for proper
									// slicing

		System.out.println("make " + tbbPath);

		for (int i = 0; i < positions.size() - 1; i++) {
			int start = positions.get(i);
			int end = positions.get(i + 1);

			String binaryHeader = bytesToHex(Arrays.copyOfRange(data, start, start + HEADER_LENGTH));

			fout.append("\n").append(MBOX_FROM_PREFIX).append("\n");
			fout.append("BinaryHeader: ").append(binaryHeader).append("\n");

			fout.append(new String(data, start + HEADER_LENGTH, end-HEADER_LENGTH, StandardCharsets.ISO_8859_1));
		}

		System.out.println("done " + tbbPath);
	}

	private static List<Integer> getPositionsOfBinaryPrefix(byte[] data) {
		List<Integer> positions = new ArrayList<>();
		int index = 0;
		while (index < data.length) {
			index = indexOf(data, BINARY_PREFIX, index);
			if (index == -1)
				break;
			positions.add(index);
			index += BINARY_PREFIX.length;
		}
		return positions;
	}

	private static int indexOf(byte[] data, byte[] pattern, int start) {
		int[] failure = computeFailure(pattern);
		int j = 0;

		for (int i = start; i < data.length; i++) {
			while (j > 0 && pattern[j] != data[i]) {
				j = failure[j - 1];
			}
			if (pattern[j] == data[i]) {
				j++;
			}
			if (j == pattern.length) {
				return i - pattern.length + 1;
			}
		}
		return -1;
	}

	private static int[] computeFailure(byte[] pattern) {
		int[] failure = new int[pattern.length];
		int j = 0;
		for (int i = 1; i < pattern.length; i++) {
			while (j > 0 && pattern[j] != pattern[i]) {
				j = failure[j - 1];
			}
			if (pattern[j] == pattern[i]) {
				j++;
			}
			failure[i] = j;
		}
		return failure;
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b & 0xFF));
		}
		return sb.toString();
	}
}
