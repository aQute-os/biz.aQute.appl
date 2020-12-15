package aQute.jpm.service;

import java.security.AllPermission;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TraceSecurityManager extends SecurityManager {
	final HashSet<Permission> had = new HashSet<>();

	public TraceSecurityManager() {

		Runtime.getRuntime()
			.addShutdownHook(new Thread() {
				@Override
				public void run() {
					Set<Permission> hadx;
					synchronized (TraceSecurityManager.this) {
						hadx = new HashSet<>(TraceSecurityManager.this.had);
					}
					Set<Permission> implied = new HashSet<>();

					for (Permission smaller : hadx) {
						for (Permission larger : hadx) {
							if (smaller != larger && larger.implies(smaller))
								implied.add(smaller);
						}
					}
					hadx.removeAll(implied);

					ArrayList<Permission> sorted = new ArrayList<>(hadx);
					Collections.sort(sorted, (a, b) -> {
						if (a.getClass() == b.getClass()) {
							if (a.getName()
								.equals(b.getName())) {
								return a.getActions()
									.compareTo(b.getActions());
							} else
								return a.getName()
									.compareTo(b.getName());
						} else
							return shorten(a.getClass()
								.getName()).compareTo(shorten(
									b.getClass()
										.getName()));
					});
					for (Permission p : sorted) {
						System.err.println(shorten(p.getClass()
							.getName()) + ":" + p.getName() + ":" + p.getActions());
					}
				}

				String shorten(String name) {
					int n = name.lastIndexOf('.');
					if (n < 0)
						return name;

					return name.substring(n + 1);
				}
			});
	}

	@Override
	public synchronized void checkPermission(Permission perm) {
		if (perm.getClass() == AllPermission.class)
			throw new SecurityException();

		if (had.contains(perm))
			return;

		had.add(perm);
	}

	@Override
	public void checkPermission(Permission perm, Object o) {
		checkPermission(perm);
	}
}
