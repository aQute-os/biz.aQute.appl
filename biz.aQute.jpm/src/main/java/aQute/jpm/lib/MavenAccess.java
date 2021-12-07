package aQute.jpm.lib;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import aQute.bnd.http.HttpClient;
import aQute.lib.io.IO;
import aQute.maven.api.Archive;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.service.reporter.Reporter;
import biz.aQute.result.Result;

class MavenAccess {

	final MavenRepository storage;

	MavenAccess(Reporter reporter, String urls, File localRepo, HttpClient client) throws Exception {
		if (localRepo == null) {
			localRepo = IO.getFile("~/.m2/repository");
		}

		List<MavenBackingRepository> release = MavenBackingRepository.create(urls, reporter, localRepo, client);

		storage = new MavenRepository(localRepo, "jpm", release, release, client.promiseFactory()
			.executor(), reporter);
	}

	Result<List<String>> list(String spec) throws Exception {
		if (Archive.isValid(spec)) {
			Archive a = new Archive(spec);
			return Result.ok(Collections.singletonList(a.toString()));
		}
		if (Program.isValidName(spec)) {
			List<Revision> revisions = storage.getRevisions(Program.valueOf(spec));
			return Result.ok(revisions.stream()
				.map(Object::toString)
				.collect(Collectors.toList()));
		}
		return Result.error("not a valid format: must be either an archive or program: %s", spec);
	}
}
