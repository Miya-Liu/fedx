package com.fluidops.fedx.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;




public class EmbeddedServer {

	public static final String HOST = "localhost";
	public static final int PORT = 18080;
	public static final String CONTEXT_PATH = "/";
	public static final String WAR_PATH = "./build/test/openrdf-sesame.war";

	private final Server jetty;

	public EmbeddedServer() {
		this(HOST, PORT, CONTEXT_PATH, WAR_PATH);
	}

	public EmbeddedServer(String host, int port, String contextPath, String warPath) {

		jetty = new Server();

		ServerConnector conn = new ServerConnector(jetty);
		conn.setHost(host);
		conn.setPort(port);
		jetty.addConnector(conn);

		WebAppContext webapp = new WebAppContext();
		webapp.setContextPath(contextPath);
		webapp.setWar(warPath);
		jetty.setHandler(webapp);
	}

	public void start()	throws Exception {
		jetty.start();
	}

	public void stop() throws Exception	{
		jetty.stop();
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args)
		throws Exception
	{
		EmbeddedServer server = new EmbeddedServer();
		server.start();
	}
}
