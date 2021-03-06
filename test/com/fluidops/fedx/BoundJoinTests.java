package com.fluidops.fedx;

import java.util.Arrays;

import org.junit.Test;

import com.fluidops.fedx.evaluation.SparqlFederationEvalStrategy;
import com.fluidops.fedx.evaluation.SparqlFederationEvalStrategyWithValues;

public class BoundJoinTests extends SPARQLBaseTest {

	
	@Test
	public void testSimpleUnion() throws Exception {
		/* test a simple bound join */
		fedxRule.setConfig("sparqlEvaluationStrategy", SparqlFederationEvalStrategy.class.getName());
		prepareTest(Arrays.asList("/tests/data/data1.ttl", "/tests/data/data2.ttl"));
		execute("/tests/boundjoin/query01.rq", "/tests/boundjoin/query01.srx", false);			
	}
	
	@Test
	public void testSimpleValues() throws Exception {
		/* test with VALUES clause based bound join */
		fedxRule.setConfig("sparqlEvaluationStrategy", SparqlFederationEvalStrategyWithValues.class.getName());
		prepareTest(Arrays.asList("/tests/data/data1.ttl", "/tests/data/data2.ttl"));
		execute("/tests/boundjoin/query01.rq", "/tests/boundjoin/query01.srx", false);			
	}
}
