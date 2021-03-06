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
package com.fluidops.fedx.evaluation.join;

import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.TupleExpr;

import com.fluidops.fedx.evaluation.FederationEvalStrategy;
import com.fluidops.fedx.evaluation.concurrent.ControlledWorkerScheduler;
import com.fluidops.fedx.structures.QueryInfo;


/**
 * Execute the nested loop join in an asynchronous fashion, i.e. one binding after the other (but
 * concurrently)
 *  
 * The number of concurrent threads is controlled by a {@link ControlledWorkerScheduler} which
 * works according to the FIFO principle.
 * 
 * This join cursor blocks until all scheduled tasks are finished, however the result iteration
 * can be accessed from different threads to allow for pipelining.
 * 
 * @author Andreas Schwarte
 */
public class ControlledWorkerJoin extends JoinExecutorBase<BindingSet> {

	public static Logger log = Logger.getLogger(ControlledWorkerJoin.class);
	
	protected final ControlledWorkerScheduler<BindingSet> scheduler;

	protected final Phaser phaser = new Phaser(1);
	
	public ControlledWorkerJoin(ControlledWorkerScheduler<BindingSet> scheduler, FederationEvalStrategy strategy,
			CloseableIteration<BindingSet, QueryEvaluationException> leftIter,
			TupleExpr rightArg, BindingSet bindings, QueryInfo queryInfo)
			throws QueryEvaluationException {
		super(strategy, leftIter, rightArg, bindings, queryInfo);
		this.scheduler = scheduler;
	}

	
	@Override
	protected void handleBindings() throws Exception {
		
		int totalBindings = 0;		// the total number of bindings
		
		while (!closed && leftIter.hasNext()) {
			ParallelJoinTask task = new ParallelJoinTask(this, strategy, rightArg, leftIter.next());
			totalBindings++;
			phaser.register();
			scheduler.schedule(task);
		}
		
		scheduler.informFinish(this);
		
		// XXX remove output if not needed anymore
		log.debug("JoinStats: left iter of join #" + this.joinId + " had " + totalBindings + " results.");
		
		// wait until all tasks are executed
		phaser.awaitAdvanceInterruptibly(phaser.arrive(), 30, TimeUnit.SECONDS);

	}

	@Override
	public void done()
	{
		phaser.arriveAndDeregister();
		super.done();
	}

	@Override
	public void toss(Exception e)
	{
		phaser.arriveAndDeregister();
		super.toss(e);
	}
}
