/*
 * Copyright 2012-2023 THALES.
 *
 * This file is part of AuthzForce CE.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ow2.authzforce.core.pdp.impl.combining;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.DecisionType;
import org.ow2.authzforce.core.pdp.api.*;
import org.ow2.authzforce.core.pdp.api.combining.BaseCombiningAlg;
import org.ow2.authzforce.core.pdp.api.combining.CombiningAlg;
import org.ow2.authzforce.core.pdp.api.combining.CombiningAlgParameter;
import org.ow2.authzforce.core.pdp.api.policy.PrimaryPolicyMetadata;
import org.ow2.authzforce.core.pdp.impl.rule.RuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

/**
 * This is the standard First-Applicable policy/rule combining algorithm. It looks through the set of policies/rules, finds the first one that applies, and returns that evaluation result.
 *
 * @version $Id: $
 */
final class FirstApplicableCombiningAlg<T extends Decidable> extends BaseCombiningAlg<T>
{

	private static final class Evaluator extends BaseCombiningAlg.Evaluator<Decidable>
	{

		private Evaluator(final Iterable<? extends Decidable> combinedElements)
		{
			super(combinedElements);
		}

		@Override
		public ExtendedDecision evaluate(final EvaluationContext context, final Optional<EvaluationContext> mdpContext, final UpdatableList<PepAction> outPepActions, final UpdatableList<PrimaryPolicyMetadata> outApplicablePolicyIdList)
		{
			for (final Decidable combinedElement : getCombinedElements())
			{
				// evaluate the policy
				final DecisionResult result = combinedElement.evaluate(context, mdpContext);
				final DecisionType decision = result.getDecision();

				/*
				 * In case of PERMIT, DENY, or INDETERMINATE, we always just return that decision, else we keep going...
				 */
				switch (decision)
				{
					case PERMIT:
						if (outApplicablePolicyIdList != null)
						{
							outApplicablePolicyIdList.addAll(result.getApplicablePolicies());
						}

						outPepActions.addAll(result.getPepActions());
						return ExtendedDecisions.SIMPLE_PERMIT;
					case DENY:
						if (outApplicablePolicyIdList != null)
						{
							outApplicablePolicyIdList.addAll(result.getApplicablePolicies());
						}

						outPepActions.addAll(result.getPepActions());
						return ExtendedDecisions.SIMPLE_DENY;
					case INDETERMINATE:
						if (outApplicablePolicyIdList != null)
						{
							outApplicablePolicyIdList.addAll(result.getApplicablePolicies());
						}

						return result;
					default:
						break;
				}

			}

			// if we got here, then none of the rules applied
			return ExtendedDecisions.SIMPLE_NOT_APPLICABLE;
		}

	}

	private static final Logger LOGGER = LoggerFactory.getLogger(FirstApplicableCombiningAlg.class);

	/** {@inheritDoc} */
	@Override
	public CombiningAlg.Evaluator getInstance(final Iterable<CombiningAlgParameter<? extends T>> params, final Iterable<? extends T> combinedElements)
	        throws UnsupportedOperationException, IllegalArgumentException
	{
		// if no element combined -> decision is overridden Effect
		if (combinedElements == null)
		{
			LOGGER.warn("{}: no element to combine -> optimization: replacing with equivalent evaluator returning constant decision NotApplicable", this);
			return CombiningAlgEvaluators.NOT_APPLICABLE_CONSTANT_EVALUATOR;
		}

		final Iterator<? extends Decidable> combinedEltIterator = combinedElements.iterator();
		if (!combinedEltIterator.hasNext())
		{
			// empty (no element to combine)
			LOGGER.warn("{}: no element to combine -> optimization: replacing with equivalent evaluator returning constant decision NotApplicable", this);
			return CombiningAlgEvaluators.NOT_APPLICABLE_CONSTANT_EVALUATOR;
		}

		if (!RuleEvaluator.class.isAssignableFrom(getCombinedElementType()))
		{
			// combined elements are not rules but policies
			return new Evaluator(combinedElements);
		}

		// combined elements are Rules, we can optimize
		/*
		 * There is at least one Rule. Prepare to iterate over Rules.
		 */
		/*
		 * If we found any empty rule
		 */
		final Deque<RuleEvaluator> finalRules = new ArrayDeque<>();
		while (combinedEltIterator.hasNext())
		{
			final RuleEvaluator rule = (RuleEvaluator) combinedEltIterator.next();
			finalRules.add(rule);
			if (rule.isAlwaysApplicable())
			{
				/*
				 * The algorithm won't go further than that
				 */
				break;
			}
		}

		/*
		 * if(combinedEltIterator.hasNext()), combinedElements has more elements than finalRules, so finalRules is a subset of combinedElements; else they have the same elements
		 */
		return new Evaluator(combinedEltIterator.hasNext() ? finalRules : combinedElements);
	}

	FirstApplicableCombiningAlg(final String algId, final Class<T> combinedType)
	{
		super(algId, combinedType);
	}

}
