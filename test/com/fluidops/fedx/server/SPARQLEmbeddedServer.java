package com.fluidops.fedx.server;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.rdf4j.http.protocol.Protocol;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigUtil;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.repository.manager.SystemRepository;
import org.eclipse.rdf4j.repository.sail.config.SailRepositoryConfig;
import org.eclipse.rdf4j.sail.memory.config.MemoryStoreConfig;

import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.util.EndpointFactory;


/**
 * An embedded http server for SPARQL query testing. Initializes a memory store
 * repository for each specified reposiotoryId.
 * 
 * @author Andreas Schwarte
 */
public class SPARQLEmbeddedServer extends EmbeddedServer implements Server {

		
	protected final List<String> repositoryIds;
	// flag to indicate whether a remote repository or SPARQL repository endpoint shall be used
	private final boolean useRemoteRepositoryEndpoint;
	
	
	/**
	 * @param repositoryIds
	 */
	public SPARQLEmbeddedServer(List<String> repositoryIds, boolean useRemoteRepositoryEndpoint) {
		super();
		this.repositoryIds = repositoryIds;
		this.useRemoteRepositoryEndpoint = useRemoteRepositoryEndpoint;
	}
	

	/**
	 * @return the url to the repository with given id
	 */
	public String getRepositoryUrl(String repoId) {
		return Protocol.getRepositoryLocation(getServerUrl(), repoId);
	}
		
	/**
	 * @return the server url
	 */
	public String getServerUrl() {
		return "http://" + HOST + ":" + PORT + CONTEXT_PATH;
	}
	
	
	@Override
	public void start()
		throws Exception
	{
		File dataDir = new File("./temp/datadir");
		dataDir.mkdirs();
		System.setProperty("info.aduna.platform.appdata.basedir", dataDir.getAbsolutePath());

		super.start();

		createTestRepositories();
	}
	

	@Override
	public void stop()
		throws Exception
	{
		Repository systemRepo = new HTTPRepository(Protocol.getRepositoryLocation(getServerUrl(),
				SystemRepository.ID));
		RepositoryConnection con = systemRepo.getConnection();
		try {
			con.clear();
		}
		finally {
			con.close();
		}

		super.stop();
		
		delete(new File("./temp/datadir"));
	}
	
	protected void delete(File file) throws Exception {
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				delete(f);
			}
		}
		if (!file.delete())
			throw new Exception("Could not delete file: " + file.getAbsolutePath());
	}

	/**
	 * @throws RepositoryException
	 */
	private void createTestRepositories()
		throws RepositoryException, RepositoryConfigException
	{
		Repository systemRep = new HTTPRepository(Protocol.getRepositoryLocation(getServerUrl(),
				SystemRepository.ID));

		// create a memory store for each provided repository id
		for (String repId : repositoryIds) {
			MemoryStoreConfig memStoreConfig = new MemoryStoreConfig();
			SailRepositoryConfig sailRepConfig = new SailRepositoryConfig(memStoreConfig);
			RepositoryConfig repConfig = new RepositoryConfig(repId, sailRepConfig);
	
			RepositoryConfigUtil.updateRepositoryConfigs(systemRep, repConfig);
		}

	}


	@Override
	public List<Repository> initialize(int nRepositories) throws Exception {
		try {
			start();
		} catch (Exception e) {
			stop();
			throw e;
		}
		
		List<Repository> repositories = new ArrayList<Repository>(nRepositories);
		for (int i=1; i<=nRepositories; i++) {
			HTTPRepository r = new HTTPRepository(getRepositoryUrl("endpoint"+i));
			r.initialize();
			repositories.add(r);
		}
		
		return repositories;
	}


	@Override
	public void shutdown() throws Exception {
		stop();		
	}


	@Override
	public Endpoint loadEndpoint(int i) throws Exception {
		return useRemoteRepositoryEndpoint ?
				EndpointFactory.loadRemoteRepository(getServerUrl(), "endpoint"+i) :
				EndpointFactory.loadSPARQLEndpoint("http://endpoint" + i, getRepositoryUrl("endpoint"+i) );
	}
}
