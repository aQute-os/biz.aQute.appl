package aQute.jpm.main;

/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Sun Microsystems nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * http://blogs.sun.com/andreas/resource/InstallCert.java
 * Use:
 * java InstallCert hostname
 * Example:
 *% java InstallCert ecc.fedora.redhat.com
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.lib.io.IO;
import aQute.service.reporter.Reporter;

/**
 * Class used to add the server's certificate to the KeyStore with your trusted
 * certificates.
 */
public class InstallCert {
	private final static Logger logger = LoggerFactory.getLogger(InstallCert.class);

	public static void installCert(Reporter reporter, String host, int port, String passphrase, File file,
		boolean install) throws Exception {

		KeyStore ks = getKeystore(passphrase, file);

		SSLContext context = SSLContext.getInstance("TLS");
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);
		X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
		SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
		context.init(null, new TrustManager[] {
			tm
		}, null);
		SSLSocketFactory factory = context.getSocketFactory();

		logger.debug("Opening connection to {}:{}", host, port);
		SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
		socket.setSoTimeout(10000);
		try {
			logger.debug("Starting SSL handshake...");
			socket.startHandshake();
			socket.close();
			logger.debug("No errors, certificate is already trusted");
		} catch (SSLException e) {
			logger.debug("expected exception");
		}

		X509Certificate[] chain = tm.chain;
		if (chain == null) {
			logger.debug("Could not obtain server certificate chain");
			return;
		}

		logger.debug("Server sent {} certificate(s):", chain.length);

		System.out.println("Chain");
		String trusted = null;

		for (int i = 0; i < chain.length; i++) {
			X509Certificate cert = chain[i];
			String alias = ks.getCertificateAlias(cert);
			if (alias != null && trusted == null)
				trusted = alias;

			System.out.printf("%-40s %s%n", alias, cert.getSubjectDN());
		}

		if (trusted != null) {
			System.out
				.println("This server is trusted, certificate in the chain was found as alias \"" + trusted + "\"");
			return;
		}

		if (!install)
			return;

		X509Certificate cert = chain[0];
		String alias = host;
		ks.setCertificateEntry(alias, cert);

		saveKeystore(passphrase, file, ks);

		logger.debug("Added certificate to keystore '{}' using alias '{}'", file, alias);
	}

	private static void saveKeystore(String passphrase, File file, KeyStore ks)
		throws FileNotFoundException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		file = getCacertFile(file);
		try (OutputStream out = IO.outputStream(file)) {
			ks.store(out, passphrase.toCharArray());
		}
	}

	private static KeyStore getKeystore(String passphrase, File file)
		throws FileNotFoundException, KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
		file = getCacertFile(file);
		KeyStore ks;
		try (InputStream in = IO.stream(file)) {
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(in, passphrase.toCharArray());
		}

		System.out.println("In key store");
		for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
			String alias = e.nextElement();
			Certificate certificate = ks.getCertificate(alias);
			if (certificate instanceof X509Certificate) {
				X509Certificate x509 = (X509Certificate) certificate;
				System.out.printf("%-40s %s%n", alias, x509.getSubjectDN());
			}
		}
		System.out.println("---------------");
		return ks;
	}

	private static File getCacertFile(File file) {
		if (file == null) {
			File java = new File(System.getProperty("java.home"));
			file = IO.getFile(java, "lib/security/jssecacerts");
			if (!file.isFile())
				file = IO.getFile(java, "lib/security/cacerts");
			if (!file.isFile())
				file = IO.getFile(java, "jre/lib/security/cacerts");
			if (!file.isFile())
				throw new IllegalArgumentException(
					"Cannot find certifcate file in $JAVA_HOME/lib/security/(jsse)?cacerts");
			System.out.printf("using cacerts %s%n", file);
		}
		return file;
	}

	public static void deleteCert(String alias, String password, File cacerts)
		throws FileNotFoundException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		KeyStore ks = getKeystore(password, cacerts);

		Certificate certificate = ks.getCertificate(alias);
		if (certificate == null) {
			System.out.println("No such alias " + alias);
			return;
		}

		ks.deleteEntry(alias);

		saveKeystore(password, cacerts, ks);
	}

	private static class SavingTrustManager implements X509TrustManager {

		private final X509TrustManager	tm;
		X509Certificate[]				chain;

		SavingTrustManager(X509TrustManager tm) {
			this.tm = tm;
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			this.chain = chain;
			tm.checkServerTrusted(chain, authType);
		}
	}

}
