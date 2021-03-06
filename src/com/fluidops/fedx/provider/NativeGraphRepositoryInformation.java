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
package com.fluidops.fedx.provider;

import java.io.File;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;

import com.fluidops.fedx.Config;
import com.fluidops.fedx.structures.Endpoint.EndpointType;
import com.fluidops.fedx.util.FedXUtil;


/**
 * Graph information for Sesame NativeStore initialization.<p>
 * 
 * Format:<p>
 * 
 * <code>
 * <%name%> fluid:store "NativeStore";
 * fluid:RepositoryLocation "%location%".
 * 
 * relative path (to {@link Config#getBaseDir()})
 * <http://DBpedia> fluid:store "NativeStore";
 * fluid:RepositoryLocation "data\\repositories\\native-storage.dbpedia".
 *  
 * absolute Path
 * <http://DBpedia> fluid:store "NativeStore";
 * fluid:RepositoryLocation "D:\\data\\repositories\\native-storage.dbpedia".
 * </code>
 * 
 * Note: the id is constructed from the location: repositories\\native-storage.dbpedia => native-storage.dbpedia
 * 
 * 
 * @author Andreas Schwarte
 *
 */
public class NativeGraphRepositoryInformation extends RepositoryInformation {

	public NativeGraphRepositoryInformation(Model graph, Resource repNode) {
		super(EndpointType.NativeStore);
		initialize(graph, repNode);
	}

	protected void initialize(Model graph, Resource repNode) {
		
		// name: the node's value
		setProperty("name", repNode.stringValue());
				
		// location
		Model location = graph.filter(repNode, FedXUtil.iri("http://fluidops.org/config#RepositoryLocation"), null);
		String repoLocation = location.iterator().next().getObject().stringValue();
		setProperty("location", repoLocation);
		
		// id: the name of the location
		setProperty("id", new File(repoLocation).getName());
	}
}
