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
package com.fluidops.fedx.algebra;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;

import com.fluidops.fedx.EndpointManager;
import com.fluidops.fedx.FederationManager;
import com.fluidops.fedx.evaluation.TripleSource;
import com.fluidops.fedx.evaluation.iterator.SingleBindingSetIteration;
import com.fluidops.fedx.evaluation.union.ParallelPreparedUnionTask;
import com.fluidops.fedx.evaluation.union.ParallelUnionTask;
import com.fluidops.fedx.evaluation.union.WorkerUnionBase;
import com.fluidops.fedx.exception.IllegalQueryException;
import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.structures.QueryInfo;
import com.fluidops.fedx.util.QueryStringUtil;



/**
 * Represents statements that can produce results at a some particular endpoints, the statement sources.
 * 
 * @author Andreas Schwarte
 * 
 * @see StatementSource
 */
public class StatementSourcePattern extends FedXStatementPattern {
		
	private static final long serialVersionUID = 7548505818766482715L;
	
	protected boolean usePreparedQuery = false;
	
	
	public StatementSourcePattern(StatementPattern node, QueryInfo queryInfo) {
		super(node, queryInfo);	
	}			
	
	public void addStatementSource(StatementSource statementSource) {
		statementSources.add(statementSource);		
	}	

	@Override
	public CloseableIteration<BindingSet, QueryEvaluationException> evaluate(BindingSet bindings) throws QueryEvaluationException {
					
		try {
			
			Boolean isEvaluated = false;	// is filter evaluated in prepared query
			String preparedQuery = null;	// used for some triple sources
			WorkerUnionBase<BindingSet> union = FederationManager.getInstance().createWorkerUnion(queryInfo);
			
			for (StatementSource source : statementSources) {
				
				Endpoint ownedEndpoint = EndpointManager.getEndpointManager().getEndpoint(source.getEndpointID());
				RepositoryConnection conn = ownedEndpoint.getConn();
				TripleSource t = ownedEndpoint.getTripleSource();
				
				/*
				 * Implementation note: for some endpoint types it is much more efficient to use prepared queries
				 * as there might be some overhead (obsolete optimization) in the native implementation. This
				 * is for instance the case for SPARQL connections. In contrast for NativeRepositories it is
				 * much more efficient to use getStatements(subj, pred, obj) instead of evaluating a prepared query.
				 */
				
				if (t.usePreparedQuery()) {
					
					// queryString needs to be constructed only once for a given bindingset
					if (preparedQuery==null) {
						try {
							preparedQuery = QueryStringUtil.selectQueryString(this, bindings, filterExpr, isEvaluated);
						} catch (IllegalQueryException e1) {
							/* all vars are bound, this must be handled as a check query, can occur in joins */
							return handleStatementSourcePatternCheck(bindings);
						}
					}
					 
					union.addTask(new ParallelPreparedUnionTask(union, preparedQuery, t, conn, bindings, (isEvaluated ? null : filterExpr)));
					
				} else {
					union.addTask(new ParallelUnionTask(union, this, t, conn, bindings, filterExpr));
				}
				
			}
			
			union.run();	// execute the union in this thread
			
			return union;
			
		} catch (RepositoryException e) {
			throw new QueryEvaluationException(e);
		} catch (MalformedQueryException e) {
			throw new QueryEvaluationException(e);
		}		
	}
	
	
	protected CloseableIteration<BindingSet, QueryEvaluationException> handleStatementSourcePatternCheck(BindingSet bindings) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		
		// if at least one source has statements, we can return this binding set as result
		
		// XXX do this in parallel for the number of endpoints ?
		for (StatementSource source : statementSources) {
			Endpoint ownedEndpoint = EndpointManager.getEndpointManager().getEndpoint(source.getEndpointID());
			RepositoryConnection ownedConnection = ownedEndpoint.getConn();
			TripleSource t = ownedEndpoint.getTripleSource();
			if (t.hasStatements(this, ownedConnection, bindings))
				return new SingleBindingSetIteration(bindings);
		}
		
		return new EmptyIteration<BindingSet, QueryEvaluationException>();
	}
}
