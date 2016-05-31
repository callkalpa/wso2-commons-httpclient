/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//httpclient/src/java/org/apache/commons/httpclient/protocol/SSLProtocolSocketFactory.java,v 1.10 2004/05/13 04:01:22 mbecke Exp $
 * $Revision: 480424 $
 * $Date: 2006-11-29 06:56:49 +0100 (Wed, 29 Nov 2006) $
 *
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.commons.httpclient.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;

/**
 * A SecureProtocolSocketFactory that uses JSSE to create sockets.
 * 
 * @author Michael Becke
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * 
 * @since 2.0
 */
public class SSLProtocolSocketFactory implements SecureProtocolSocketFactory {

    /**
     * The factory singleton.
     */
    private static final SSLProtocolSocketFactory factory = new SSLProtocolSocketFactory();
    
    // This is a a sorted list, if you insert new elements do it orderdered.
    private final static String[] BAD_COUNTRY_2LDS =
        {"ac", "co", "com", "ed", "edu", "go", "gouv", "gov", "info",
            "lg", "ne", "net", "or", "org"};
    
    /**
     * Gets an singleton instance of the SSLProtocolSocketFactory.
     * @return a SSLProtocolSocketFactory
     */
    static SSLProtocolSocketFactory getSocketFactory() {
        return factory;
    }    
    
    /**
     * Constructor for SSLProtocolSocketFactory.
     */
    public SSLProtocolSocketFactory() {
        super();
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int,java.net.InetAddress,int)
     */
    public Socket createSocket(
        String host,
        int port,
        InetAddress clientHost,
        int clientPort)
        throws IOException, UnknownHostException {
        Socket sslSocket =  SSLSocketFactory.getDefault().createSocket(
            host,
            port,
            clientHost,
            clientPort
        );
        verifyHostName(host, (SSLSocket) sslSocket);
        return sslSocket;
    }

    /**
     * Attempts to get a new socket connection to the given host within the given time limit.
     * <p>
     * This method employs several techniques to circumvent the limitations of older JREs that 
     * do not support connect timeout. When running in JRE 1.4 or above reflection is used to 
     * call Socket#connect(SocketAddress endpoint, int timeout) method. When executing in older 
     * JREs a controller thread is executed. The controller thread attempts to create a new socket
     * within the given limit of time. If socket constructor does not return until the timeout 
     * expires, the controller terminates and throws an {@link ConnectTimeoutException}
     * </p>
     *  
     * @param host the host name/IP
     * @param port the port on the host
     * @param localAddress the local host name/IP to bind the socket to
     * @param localPort the port on the local machine
     * @param params {@link HttpConnectionParams Http connection parameters}
     * 
     * @return Socket a new socket
     * 
     * @throws IOException if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     * determined
     * 
     * @since 3.0
     */
    public Socket createSocket(
        final String host,
        final int port,
        final InetAddress localAddress,
        final int localPort,
        final HttpConnectionParams params
    ) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        if (timeout == 0) {
            Socket sslSocket =  createSocket(host, port, localAddress, localPort);
            verifyHostName(host, (SSLSocket) sslSocket);
            return sslSocket;
        } else {
            // To be eventually deprecated when migrated to Java 1.4 or above
            Socket sslSocket = ReflectionSocketFactory.createSocket(
                "javax.net.ssl.SSLSocketFactory", host, port, localAddress, localPort, timeout);
            if (sslSocket == null) {
            	sslSocket = ControllerThreadSocketFactory.createSocket(
                    this, host, port, localAddress, localPort, timeout);
            }
            verifyHostName(host, (SSLSocket) sslSocket);
            return sslSocket;
        }
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int)
     */
    public Socket createSocket(String host, int port)
        throws IOException, UnknownHostException {
        Socket sslSocket = SSLSocketFactory.getDefault().createSocket(
            host,
            port
        );
        verifyHostName(host, (SSLSocket) sslSocket);
        return sslSocket;
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.net.Socket,java.lang.String,int,boolean)
     */
    public Socket createSocket(
        Socket socket,
        String host,
        int port,
        boolean autoClose)
        throws IOException, UnknownHostException {
        Socket sslSocket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(
            socket,
            host,
            port,
            autoClose
        );
        verifyHostName(host, (SSLSocket) sslSocket);
        return sslSocket;
    }
    

    
    
    /**
     * Verifies that the given hostname in certicifate is the hostname we are trying to connect to
     * http://www.cvedetails.com/cve/CVE-2012-5783/
     * @param host
     * @param ssl
     * @throws IOException
     */
    
	private static void verifyHostName(String host, SSLSocket ssl)
			throws IOException {
		if (host == null) {
			throw new IllegalArgumentException("host to verify was null");
		}

		SSLSession session = ssl.getSession();
		if (session == null) {
            // In our experience this only happens under IBM 1.4.x when
            // spurious (unrelated) certificates show up in the server's chain.
            // Hopefully this will unearth the real problem:
			InputStream in = ssl.getInputStream();
			in.available();
            /*
                 If you're looking at the 2 lines of code above because you're
                 running into a problem, you probably have two options:

                    #1.  Clean up the certificate chain that your server
                         is presenting (e.g. edit "/etc/apache2/server.crt" or
                         wherever it is your server's certificate chain is
                         defined).

                                             OR

                    #2.   Upgrade to an IBM 1.5.x or greater JVM, or switch to a
                          non-IBM JVM.
              */

            // If ssl.getInputStream().available() didn't cause an exception,
            // maybe at least now the session is available?
			session = ssl.getSession();
			if (session == null) {
                // If it's still null, probably a startHandshake() will
                // unearth the real problem.
				ssl.startHandshake();

                // Okay, if we still haven't managed to cause an exception,
                // might as well go for the NPE.  Or maybe we're okay now?
				session = ssl.getSession();
			}
		}

		Certificate[] certs = session.getPeerCertificates();
		verifyHostName(host.trim().toLowerCase(Locale.US),  (X509Certificate) certs[0]);
	}
	/**
	 * Extract the names from the certificate and tests host matches one of them
	 * @param host
	 * @param cert
	 * @throws SSLException
	 */

	private static void verifyHostName(final String host, X509Certificate cert)
			throws SSLException {
        // I'm okay with being case-insensitive when comparing the host we used
        // to establish the socket to the hostname in the certificate.
        // Don't trim the CN, though.
        
		String cn = getCN(cert);
		String[] subjectAlts = getDNSSubjectAlts(cert);
		verifyHostName(host, cn.toLowerCase(Locale.US), subjectAlts);

	}

	/**
	 * Extract all alternative names from a certificate.
	 * @param cert
	 * @return
	 */
	private static String[] getDNSSubjectAlts(X509Certificate cert) {
		LinkedList subjectAltList = new LinkedList();
		Collection c = null;
		try {
			c = cert.getSubjectAlternativeNames();
		} catch (CertificateParsingException cpe) {
			// Should probably log.debug() this?
			cpe.printStackTrace();
		}
		if (c != null) {
			Iterator it = c.iterator();
			while (it.hasNext()) {
				List list = (List) it.next();
				int type = ((Integer) list.get(0)).intValue();
				// If type is 2, then we've got a dNSName
				if (type == 2) {
					String s = (String) list.get(1);
					subjectAltList.add(s);
				}
			}
		}
		if (!subjectAltList.isEmpty()) {
			String[] subjectAlts = new String[subjectAltList.size()];
			subjectAltList.toArray(subjectAlts);
			return subjectAlts;
		} else {
			return new String[0];
		}
	        
	}
	/**
	 * Verifies
	 * @param host
	 * @param cn
	 * @param subjectAlts
	 * @throws SSLException
	 */

	private static void verifyHostName(final String host, String cn, String[] subjectAlts)throws SSLException{
		StringBuffer cnTested = new StringBuffer();

		for (int i = 0; i < subjectAlts.length; i++){
			String name = subjectAlts[i];
			if (name != null) {
				name = name.toLowerCase();
				if (verifyHostName(host, name)){
					return;
				}
				cnTested.append("/").append(name);
			}				
		}
		if (cn != null && verifyHostName(host, cn)){
			return;
		}
		cnTested.append("/").append(cn);
		throw new SSLException("hostname in certificate didn't match: <"
					+ host + "> != <" + cnTested + ">");
		
	}		
	
	private static boolean verifyHostName(final String host, final String cn){
		if (doWildCard(cn) && !isIPAddress(host)) {
			return matchesWildCard(cn, host);
		} 
		return host.equalsIgnoreCase(cn);		
	}
    private static boolean doWildCard(String cn) {
		// Contains a wildcard
		// wildcard in the first block
    	// not an ipaddress (ip addres must explicitily be equal)
    	// not using 2nd level common tld : ex: not for *.co.uk
    	String parts[] = cn.split("\\.");
    	return parts.length >= 3 &&
    			parts[0].endsWith("*") &&
    			acceptableCountryWildcard(cn) &&
    			!isIPAddress(cn);
    }
    
    
	private static final Pattern IPV4_PATTERN = 
			Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

	private static final Pattern IPV6_STD_PATTERN = 
			Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");

	private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = 
			Pattern.compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");


	private static boolean isIPAddress(final String hostname) {
		return hostname != null
				&& (
						IPV4_PATTERN.matcher(hostname).matches()
						|| IPV6_STD_PATTERN.matcher(hostname).matches() 
						|| IPV6_HEX_COMPRESSED_PATTERN.matcher(hostname).matches()
		);

	}

	private static boolean acceptableCountryWildcard(final String cn) {
		// The CN better have at least two dots if it wants wildcard action,
		// but can't be [*.co.uk] or [*.co.jp] or [*.org.uk], etc...
		// The [*.co.uk] problem is an interesting one. Should we just
		// hope that CA's would never foolishly allow such a
		// certificate to happen?
    	
		String[] parts = cn.split("\\.");
		// Only checks for 3 levels, with country code of 2 letters.
		if (parts.length > 3 || parts[parts.length - 1].length() != 2) {
			return true;
		}
		String countryCode = parts[parts.length - 2];
		return Arrays.binarySearch(BAD_COUNTRY_2LDS, countryCode) < 0;
	}

	private static boolean matchesWildCard(final String cn,
			final String hostName) {
		String parts[] = cn.split("\\.");
		boolean match = false;
		String firstpart = parts[0];
		if (firstpart.length() > 1) {
			// server∗
			// e.g. server
			String prefix =  firstpart.substring(0, firstpart.length() - 1);
			// skipwildcard part from cn
			String suffix = cn.substring(firstpart.length()); 
			// skip wildcard part from host
			String hostSuffix = hostName.substring(prefix.length());			
			match = hostName.startsWith(prefix) && hostSuffix.endsWith(suffix);
		} else {
			match = hostName.endsWith(cn.substring(1));
		}
		if (match) {
			// I f we're in strict mode ,
			// [ ∗.foo.com] is not allowed to match [a.b.foo.com]
			match = countDots(hostName) == countDots(cn);
		}
		return match;
	}

	private static int countDots(final String data) {
		int dots = 0;
		for (int i = 0; i < data.length(); i++) {
			if (data.charAt(i) == '.') {
				dots += 1;
			}
		}
		return dots;
	}

	private static String getCN(final X509Certificate cert) {
		final String subjectPrincipal = cert.getSubjectX500Principal().toString();
		try {
			return extractCN(subjectPrincipal);
		} catch (SSLException ex) {
			return null;
		}
	}

	private static String extractCN(final String subjectPrincipal) throws SSLException {
		if (subjectPrincipal == null) {
			return null;
		}
		try {
			final LdapName subjectDN = new LdapName(subjectPrincipal);
			final List<Rdn> rdns = subjectDN.getRdns();
			for (int i = rdns.size() - 1; i >= 0; i--) {
				final Rdn rds = rdns.get(i);
				final Attributes attributes = rds.toAttributes();
				final Attribute cn = attributes.get("cn");
				if (cn != null) {
					try {
						final Object value = cn.get();
						if (value != null) {
							return value.toString();
						}
					} catch (NoSuchElementException ignore) {
					} catch (NamingException ignore) {
					}
				}
			}
		} catch (InvalidNameException e) {
			throw new SSLException(subjectPrincipal + " is not a valid X500 distinguished name");
		}
		return null;
	}

    /**
     * All instances of SSLProtocolSocketFactory are the same.
     */
    public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().equals(getClass()));
    }

    /**
     * All instances of SSLProtocolSocketFactory have the same hash code.
     */
    public int hashCode() {
        return getClass().hashCode();
    }    
    
}
