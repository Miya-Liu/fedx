package com.fluidops.fedx;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.common.io.IOUtil;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.dawg.DAWGTestResultSetUtil;
import org.eclipse.rdf4j.query.impl.MutableTupleQueryResult;
import org.eclipse.rdf4j.query.impl.TupleQueryResultBuilder;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultParserRegistry;
import org.eclipse.rdf4j.query.resultio.QueryResultFormat;
import org.eclipse.rdf4j.query.resultio.QueryResultIO;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultParser;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.junit.Assert;
import org.junit.BeforeClass;

import com.fluidops.fedx.exception.FedXException;
import com.fluidops.fedx.exception.FedXRuntimeException;

public class FedXBaseTest {

	public static Logger log;
	
	
	@BeforeClass
	public static void initLogging() throws Exception	{
		
		if (System.getProperty("log4j.configuration")==null)
			System.setProperty("log4j.configuration", "file:build/test/log4j-debug.properties");
		
		log = Logger.getLogger(FedXBaseTest.class);
	}
	
	
	/**
	 * Execute a testcase, both queryFile and expectedResultFile must be files 
	 * 
	 * @param queryFile
	 * @param expectedResultFile
	 * @param checkOrder
	 * @throws Exception
	 */
	protected void execute(RepositoryConnection conn, String queryFile, String expectedResultFile, boolean checkOrder) throws Exception {
		
		String queryString = readQueryString(queryFile);
		
		try {
			Query query = QueryManager.prepareQuery(queryString);
			
			if (query instanceof TupleQuery) {
				TupleQueryResult queryResult = ((TupleQuery)query).evaluate();
				
				TupleQueryResult expectedResult = readExpectedTupleQueryResult(expectedResultFile);
				
				compareTupleQueryResults(queryResult, expectedResult, checkOrder);
	
			} else if (query instanceof GraphQuery) {
				GraphQueryResult gqr = ((GraphQuery)query).evaluate();
				Set<Statement> queryResult = Iterations.asSet(gqr);
	
				Set<Statement> expectedResult = readExpectedGraphQueryResult(expectedResultFile);
	
				compareGraphs(queryResult, expectedResult);
				
			} else if (query instanceof BooleanQuery) {
				
				boolean queryResult = ((BooleanQuery)query).evaluate();
				boolean expectedResult = readExpectedBooleanQueryResult(expectedResultFile);
				Assert.assertEquals(expectedResult, queryResult);
			}
			else {
				throw new RuntimeException("Unexpected query type: " + query.getClass());
			}
		} finally {
			conn.close();
		}
	}
	
	protected TupleQueryResult runSelectQueryFile(String queryFile) throws Exception {
		String queryString = readQueryString(queryFile);

		Query query = QueryManager.prepareQuery(queryString);

		if (query instanceof TupleQuery) {
			return ((TupleQuery) query).evaluate();
		}

		throw new Exception("unexpected query: " + queryString);
	}
	
	protected void evaluateQueryPlan(String queryFile, String expectedPlanFile) throws Exception {
		
		String actualQueryPlan = QueryManager.getQueryPlan(readQueryString(queryFile));
		String expectedQueryPlan = readResourceAsString(expectedPlanFile);
		
		// make sure the comparison works cross operating system
		expectedQueryPlan = expectedQueryPlan.replace("\r\n", "\n");
		actualQueryPlan = actualQueryPlan.replace("\r\n", "\n");
		
		actualQueryPlan = actualQueryPlan.replace("sparql_localhost:18080_repositories_", "");
		actualQueryPlan = actualQueryPlan.replace("remote_", "");
		Assert.assertEquals(expectedQueryPlan, actualQueryPlan);
		
	}
	
	protected void applyConfig(String key, String value) {
		Config.getConfig();
	}
	
	protected void resetConfig(String ...fedxConfig) {
		Config.reset();
		try	{
			Config.initialize(fedxConfig);
		} catch (FedXException e) {
			throw new FedXRuntimeException(e);
		}
	}
	
	protected void prepareTest() throws RepositoryException {
		// reset fedx
		FederationManager.getInstance().getCache().clear();
	}
	
	/**
	 * Read the query string from the specified resource
	 * 
	 * @param queryResource
	 * @return
	 * @throws RepositoryException
	 * @throws IOException
	 */
	protected String readQueryString(String queryFile) throws RepositoryException, IOException {
		return readResourceAsString(queryFile);
	}
	
	/**
	 * Read resource from classpath as string, e.g. /tests/basic/data01endpoint1.ttl
	 * 
	 * @param resource
	 * @return
	 * @throws IOException
	 */
	protected String readResourceAsString(String resource) throws IOException {
		InputStream stream = FedXBaseTest.class.getResourceAsStream(resource);
		try {
			return IOUtil.readString(new InputStreamReader(stream, "UTF-8"));
		} finally {
			stream.close();
		}
	}
	
	/**
	 * Read the expected tuple query result from the specified resource
	 * 
	 * @param queryResource
	 * @return
	 * @throws RepositoryException
	 * @throws IOException
	 */
	protected TupleQueryResult readExpectedTupleQueryResult(String resultFile)	throws Exception
	{
		QueryResultFormat tqrFormat = QueryResultIO.getParserFormatForFileName(resultFile).get();
	
		if (tqrFormat != null) {
			InputStream in = SPARQLBaseTest.class.getResourceAsStream(resultFile);
			if (in==null)
				throw new IOException("File could not be opened: " + resultFile);
			
			try {
				TupleQueryResultParser parser = QueryResultIO.createTupleParser(tqrFormat);
	
				TupleQueryResultBuilder qrBuilder = new TupleQueryResultBuilder();
				parser.setQueryResultHandler(qrBuilder);
	
				parser.parseQueryResult(in);
				return qrBuilder.getQueryResult();
			}
			finally {
				in.close();
			}
		}
		else {
			Set<Statement> resultGraph = readExpectedGraphQueryResult(resultFile);
			return DAWGTestResultSetUtil.toTupleQueryResult(resultGraph);
		}
	}
	
	/**
	 * Read the expected graph query result from the specified resource
	 * 
	 * @param resultFile
	 * @return
	 * @throws Exception
	 */
	protected Set<Statement> readExpectedGraphQueryResult(String resultFile) throws Exception
	{
		RDFFormat rdfFormat = Rio.getParserFormatForFileName(resultFile).get();
	
		if (rdfFormat != null) {
			RDFParser parser = Rio.createParser(rdfFormat);
			parser.setPreserveBNodeIDs(true);
			parser.setValueFactory(SimpleValueFactory.getInstance());
	
			Set<Statement> result = new LinkedHashSet<Statement>();
			parser.setRDFHandler(new StatementCollector(result));
	
			InputStream in = SPARQLBaseTest.class.getResourceAsStream(resultFile);
			try {
				parser.parse(in, resultFile);		
			}
			finally {
				in.close();
			}
	
			return result;
		}
		else {
			throw new RuntimeException("Unable to determine file type of results file");
		}
	}
	
	protected boolean readExpectedBooleanQueryResult(String resultFile) throws Exception 	{
		QueryResultFormat bqrFormat = BooleanQueryResultParserRegistry.getInstance().getFileFormatForFileName(
				resultFile).get();
	
		if (bqrFormat != null) {
			InputStream in = SPARQLBaseTest.class.getResourceAsStream(resultFile);
			try {
				return QueryResultIO.parseBoolean(in, bqrFormat);
			}
			finally {
				in.close();
			}
		}
		else {
			Set<Statement> resultGraph = readExpectedGraphQueryResult(resultFile);
			return DAWGTestResultSetUtil.toBooleanQueryResult(resultGraph);
		}
	}

	/**
	 * Compare two tuple query results
	 * 
	 * @param queryResult
	 * @param expectedResult
	 * @param checkOrder
	 * @throws Exception
	 */
	protected void compareTupleQueryResults(TupleQueryResult queryResult, TupleQueryResult expectedResult, boolean checkOrder)
		throws Exception
	{
		// Create MutableTupleQueryResult to be able to re-iterate over the
		// results
		MutableTupleQueryResult queryResultTable = new MutableTupleQueryResult(queryResult);
		MutableTupleQueryResult expectedResultTable = new MutableTupleQueryResult(expectedResult);
	
		boolean resultsEqual;
		
		resultsEqual = QueryResults.equals(queryResultTable, expectedResultTable);
		
		if (checkOrder) {
			// also check the order in which solutions occur.
			queryResultTable.beforeFirst();
			expectedResultTable.beforeFirst();

			while (queryResultTable.hasNext()) {
				BindingSet bs = queryResultTable.next();
				BindingSet expectedBs = expectedResultTable.next();
				
				if (! bs.equals(expectedBs)) {
					resultsEqual = false;
					break;
				}
			}
		}
		
	
		if (!resultsEqual) {
			queryResultTable.beforeFirst();
			expectedResultTable.beforeFirst();
	
			List<BindingSet> queryBindings = Iterations.asList(queryResultTable);
			
			List<BindingSet> expectedBindings = Iterations.asList(expectedResultTable);
	
			List<BindingSet> missingBindings = new ArrayList<BindingSet>(expectedBindings);
			missingBindings.removeAll(queryBindings);
	
			List<BindingSet> unexpectedBindings = new ArrayList<BindingSet>(queryBindings);
			unexpectedBindings.removeAll(expectedBindings);
	
			StringBuilder message = new StringBuilder(128);
	
			if (!missingBindings.isEmpty()) {
	
				message.append("Missing bindings: \n");
				for (BindingSet bs : missingBindings) {
					message.append(bs);
					message.append("\n");
				}	
			}
	
			if (!unexpectedBindings.isEmpty()) {
				message.append("Unexpected bindings: \n");
				for (BindingSet bs : unexpectedBindings) {
					message.append(bs);
					message.append("\n");
				}
			}
			
			if (checkOrder && missingBindings.isEmpty() && unexpectedBindings.isEmpty()) {
				message.append("Results are not in expected order.\n");
				message.append(" =======================\n");
				message.append("query result: \n");
				for (BindingSet bs: queryBindings) {
					message.append(bs);
					message.append("\n");
				}
				message.append(" =======================\n");
				message.append("expected result: \n");
				for (BindingSet bs: expectedBindings) {
					message.append(bs);
					message.append("\n");
				}
				message.append(" =======================\n");
	
				System.out.print(message.toString());
			}
	
			log.error(message.toString());
			Assert.fail(message.toString());
		}
		
	}
	
	/**
	 * Compare two graphs
	 * 
	 * @param queryResult
	 * @param expectedResult
	 * @throws Exception
	 */
	protected void compareGraphs(Set<Statement> queryResult, Set<Statement> expectedResult)
		throws Exception
	{
		if (!Models.isomorphic(expectedResult, queryResult))
		{
			StringBuilder message = new StringBuilder(128);
			message.append("Expected result: \n");
			for (Statement st : expectedResult) {
				message.append(st.toString());
				message.append("\n");
			}
	
			message.append("Query result: \n");
			for (Statement st : queryResult) {
				message.append(st.toString());
				message.append("\n");
			}
	
			log.error(message.toString());
			Assert.fail(message.toString());
		}
	}	

}
