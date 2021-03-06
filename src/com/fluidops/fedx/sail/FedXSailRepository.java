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
package com.fluidops.fedx.sail;

import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailException;

/**
 * A special {@link SailRepository} which performs the actions as
 * defined in {@link FedXSailRepositoryConnection}.
 * 
 * @author as
 */
public class FedXSailRepository extends SailRepository
{

	public FedXSailRepository(Sail sail)
	{
		super(sail);
	}

	@Override
	public SailRepositoryConnection getConnection() throws RepositoryException {
		try {
			return new FedXSailRepositoryConnection(this, this.getSail().getConnection());
		}
		catch (SailException e) {
			throw new RepositoryException(e);
		}
	}	

}
