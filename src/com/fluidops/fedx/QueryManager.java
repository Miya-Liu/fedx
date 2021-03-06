/*
 * Copyright (C) 2018 Veritas Technologies LLC.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fluidops.fedx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.impl.EmptyBindingSet;
import org.eclipse.rdf4j.query.impl.SimpleDataset;
import org.eclipse.rdf4j.query.parser.ParsedOperation;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.sail.SailException;

import com.fluidops.fedx.exception.FedXException;
import com.fluidops.fedx.exception.FedXRuntimeException;
import com.fluidops.fedx.optimizer.Optimizer;
import com.fluidops.fedx.structures.QueryInfo;
import com.fluidops.fedx.structures.QueryType;


/**
 * QueryManager to manage queries. 
 * 
 * a) Management of running queries (abort, finish)
 * b) Factory to create queries
 * 
 * @author Andreas Schwarte
 */
public class QueryManager {

	
	public static Logger log = Logger.getLogger(QueryManager.class);
	
	
	// singleton behavior: initialized in constructor of FederationManager
	protected static QueryManager instance = null;
	protected static QueryManager getInstance() {
		return instance;
	}
	
	protected final FederationManager federationManager;
	protected final Repository repo;
	protected final RepositoryConnection conn;
	protected Set<QueryInfo> runningQueries = new ConcurrentSkipListSet<QueryInfo>();
	protected HashMap<String, String> prefixDeclarations = new HashMap<String, String>();
	
	protected QueryManager(FederationManager federationManager, Repository repo) {
		this.federationManager = federationManager;
		this.repo = repo;
		try	{
			this.conn = repo.getConnection();
		} catch (RepositoryException e)	{
			throw new FedXRuntimeException(e);		// should never occur
		}
	}
	
	public void shutdown()
	{
		if (conn != null)
		{
			try
			{
				conn.close();
			}
			catch (RepositoryException e)
			{
				throw new FedXRuntimeException(e);
			}
		}
	}
	
	/**
	 * Add the query to the set of running queries, queries are identified via a unique id
	 * @param queryInfo
	 */
	public void registerQuery(QueryInfo queryInfo) {
		assert runningQueries.contains(queryInfo) : "Duplicate query: query " + queryInfo.getQueryID() + " is already registered.";
		runningQueries.add(queryInfo);
	}
	
	public Set<QueryInfo> getRunningQueries() {
		return new HashSet<QueryInfo>(runningQueries);
	}
	
	public int getNumberOfRunningQueries() {
		return runningQueries.size();
	}
	
	public void abortQuery(QueryInfo queryInfo) {
		synchronized (queryInfo) {
			if (!runningQueries.contains(queryInfo))
				return;		
			log.info("Aborting query " + queryInfo.getQueryID());
			federationManager.getJoinScheduler().abort(queryInfo.getQueryID());
			federationManager.getUnionScheduler().abort(queryInfo.getQueryID());
			runningQueries.remove(queryInfo);
		}
	}
	
	public void finishQuery(QueryInfo queryInfo) {
		runningQueries.remove(queryInfo);
	}
	
	public boolean isRunning(QueryInfo queryInfo) {
		return runningQueries.contains(queryInfo);
	}
	
	/**
	 * Register a prefix declaration to be used during query evaluation. If a 
	 * known prefix is used in a query, it is substituted in the parsing step.
	 * 
	 * If prefix is null, the corresponding entry is removed.
	 * 
	 * @param prefix
	 * 				a common prefix, e.g. rdf
	 * @param namespace
	 * 				the corresponding namespace, e.g. "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
	 */
	public void addPrefixDeclaration(String prefix, String namespace) {
		if (prefix==null) {
			prefixDeclarations.remove(prefix);
			return;
		}
		
		prefixDeclarations.put(prefix, namespace);
	}
	
	
	/**
	 * Prepare a tuple query which uses the underlying federation to evaluate the query.<p>
	 * 
	 * The queryString is modified to use the declared PREFIX declarations, see 
	 * {@link Config#getPrefixDeclarations()} for details.
	 * 
	 * @param queryString
	 * @return the prepared tuple query
	 * @throws MalformedQueryException
	 */
	public static TupleQuery prepareTupleQuery(String queryString) throws MalformedQueryException {
				
		Query q = prepareQuery(queryString);
		if (!(q instanceof TupleQuery))
			throw new FedXRuntimeException("Query is not a tuple query: " + q.getClass());
		return (TupleQuery)q;
	}
	
	/**
	 * Prepare a tuple query which uses the underlying federation to evaluate the query.<p>
	 * 
	 * The queryString is modified to use the declared PREFIX declarations, see 
	 * {@link Config#getPrefixDeclarations()} for details.
	 * 
	 * @param queryString
	 * @return the prepared graph query
	 * @throws MalformedQueryException
	 */
	public static GraphQuery prepareGraphQuery(String queryString) throws MalformedQueryException {
				
		Query q = prepareQuery(queryString);
		if (!(q instanceof GraphQuery))
			throw new FedXRuntimeException("Query is not a graph query: " + q.getClass());
		return (GraphQuery)q;
	}
	
	/**
	 * Prepare a boolean query which uses the underlying federation to evaluate the query.<p>
	 * 
	 * The queryString is modified to use the declared PREFIX declarations, see 
	 * {@link Config#getPrefixDeclarations()} for details.
	 * 
	 * @param queryString
	 * @return the prepared {@link BooleanQuery}
	 * @throws MalformedQueryException
	 */
	public static BooleanQuery prepareBooleanQuery(String queryString) throws MalformedQueryException {
				
		Query q = prepareQuery(queryString);
		if (!(q instanceof BooleanQuery))
			throw new FedXRuntimeException("Unexpected query type: " + q.getClass());
		return (BooleanQuery)q;
	}
	
	
	static Pattern prefixCheck = Pattern.compile(".*PREFIX .*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	static Pattern prefixPattern = Pattern.compile("PREFIX[ ]*(\\w*):[ ]*<(\\S*)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	
	/**
	 * Prepare a {@link Query} which uses the underlying federation to evaluate the SPARQL query.<p>
	 * 
	 * The queryString is modified to use the declared PREFIX declarations, see 
	 * {@link Config#getPrefixDeclarations()} for details.
	 * 
	 * @param queryString
	 * @return the prepared {@link Query}
	 * @throws MalformedQueryException
	 */
	public static Query prepareQuery(String queryString) throws MalformedQueryException {
		QueryManager qm = getInstance();
		if (qm==null)
			throw new FedXRuntimeException("QueryManager not initialized, used FedXFactory methods to initialize FedX correctly.");
		
		
		if (qm.prefixDeclarations.size()>0) {
			
			/* we have to check for prefixes in the query to not add
			 * duplicate entries. In case duplicates are present
			 * Sesame throws a MalformedQueryException
			 */
			if (prefixCheck.matcher(queryString).matches())
				queryString = qm.getPrefixDeclarationsCheck(queryString) + queryString;
			else
				queryString = qm.getPrefixDeclarations() + queryString;
		}
		
		Query q;
		try	{
			q = qm.conn.prepareQuery(QueryLanguage.SPARQL, queryString);
		} catch (RepositoryException e) {
			throw new FedXRuntimeException(e);	// cannot occur
		}
		
		// TODO set query time
		
		return q;
	}
	
	/**
	 * Retrieve the query plan for the given query string.
	 * 
	 * @return the query plan
	 */
	public static String getQueryPlan(String queryString) throws MalformedQueryException, FedXException {
		
		QueryManager qm = getInstance();
		if (qm==null)
			throw new FedXRuntimeException("QueryManager not initialized, used FedXFactory methods to initialize FedX correctly.");
				
		if (qm.prefixDeclarations.size()>0) {
			
			/* we have to check for prefixes in the query to not add
			 * duplicate entries. In case duplicates are present
			 * Sesame throws a MalformedQueryException
			 */
			if (prefixCheck.matcher(queryString).matches())
				queryString = qm.getPrefixDeclarationsCheck(queryString) + queryString;
			else
				queryString = qm.getPrefixDeclarations() + queryString;
		}
		
		ParsedOperation query = QueryParserUtil.parseOperation(QueryLanguage.SPARQL, queryString, null);
		if (!(query instanceof ParsedQuery))
			throw new MalformedQueryException("Not a ParsedQuery: " + query.getClass());
		// we use a dummy query info object here
		QueryInfo qInfo = new QueryInfo(queryString, QueryType.SELECT);
		TupleExpr tupleExpr = ((ParsedQuery)query).getTupleExpr();
		try {
			tupleExpr = Optimizer.optimize(tupleExpr, new SimpleDataset(), EmptyBindingSet.getInstance(),
					FederationManager.getInstance().getStrategy(), qInfo);
			return tupleExpr.toString();
		} catch (SailException e) {
			throw new FedXException("Unable to retrieve query plan: " + e.getMessage());
		}		
	}
	
	
	/**
	 * Get the prefix declarations that have to be prepended to the query.
	 * 
	 * @return the prefix declarations
	 */
	protected String getPrefixDeclarations() {
		StringBuilder sb = new StringBuilder();
		for (String namespace : prefixDeclarations.keySet()) {
			sb.append("PREFIX ").append(namespace).append(": <")
					.append(prefixDeclarations.get(namespace)).append(">\r\n");
		}
		return sb.toString();
	}
	
	/**
	 * Get the prefix declarations that have to be added while considering
	 * prefixes that are already declared in the query. The issue here is
	 * that duplicate declaration causes exceptions in Sesame
	 * 
	 * @param queryString
	 * @return the prefix declarations
	 */
	protected String getPrefixDeclarationsCheck(String queryString) {
		
		Set<String> queryPrefixes = findQueryPrefixes(queryString);
		
		StringBuilder sb = new StringBuilder();
		for (String prefix : prefixDeclarations.keySet()) {
			if (queryPrefixes.contains(prefix))
				continue;	// already there, do not add
			sb.append("PREFIX ").append(prefix).append(": <")
					.append(prefixDeclarations.get(prefix)).append(">\r\n");
		}
		return sb.toString();
	}
	
	
	/**
	 * Find all prefixes declared in the query
	 * @param queryString
	 * @return the prefixes
	 */
	protected static Set<String> findQueryPrefixes(String queryString) {
		
		HashSet<String> res = new HashSet<String>();
		
		Scanner sc = new Scanner(queryString);
		while (true) {
			while (sc.findInLine(prefixPattern)!=null) {
				MatchResult m = sc.match();
				res.add(m.group(1));
			}
			if (!sc.hasNextLine())
				break;
			sc.nextLine();
		}
		sc.close();
		return res;
	}
	

}
