package demos;

import java.util.Arrays;

import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;

import com.fluidops.fedx.Config;
import com.fluidops.fedx.FedXFactory;
import com.fluidops.fedx.FederationManager;
import com.fluidops.fedx.QueryManager;

public class Demo4
{

	public static void main(String[] args) throws Exception
	{

		Config.initialize();
		FedXFactory.initializeSparqlFederation(Arrays.asList(
				"http://dbpedia.org/sparql",
				"http://data.semanticweb.org/sparql"));

		String q = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>\n"
				+ "SELECT ?President ?Party WHERE {\n"
				+ "?President rdf:type dbpedia-owl:President .\n"
				+ "?President dbpedia-owl:party ?Party . }";

		TupleQuery query = QueryManager.prepareTupleQuery(q);
		TupleQueryResult res = query.evaluate();

		while (res.hasNext())
		{
			System.out.println(res.next());
		}

		FederationManager.getInstance().shutDown();
		System.out.println("Done.");
		System.exit(0);

	}
}