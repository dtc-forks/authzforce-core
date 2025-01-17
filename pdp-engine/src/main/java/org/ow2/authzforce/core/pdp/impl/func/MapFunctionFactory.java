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
package org.ow2.authzforce.core.pdp.impl.func;

import org.ow2.authzforce.core.pdp.api.EvaluationContext;
import org.ow2.authzforce.core.pdp.api.IndeterminateEvaluationException;
import org.ow2.authzforce.core.pdp.api.expression.Expression;
import org.ow2.authzforce.core.pdp.api.expression.Expressions;
import org.ow2.authzforce.core.pdp.api.func.FirstOrderFunction;
import org.ow2.authzforce.core.pdp.api.func.GenericHigherOrderFunctionFactory;
import org.ow2.authzforce.core.pdp.api.func.HigherOrderBagFunction;
import org.ow2.authzforce.core.pdp.api.value.*;
import org.ow2.authzforce.core.pdp.impl.func.StandardHigherOrderBagFunctions.OneBagOnlyHigherOrderFunction;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 *
 * Map function factory
 * 
 * @version $Id: $
 */
final class MapFunctionFactory extends GenericHigherOrderFunctionFactory
{
	private static final IllegalArgumentException NULL_SUB_FUNCTION_RETURN_TYPE_ARG_EXCEPTION = new IllegalArgumentException(
			"Cannot create generic function with null subFunctionReturnTypeFactory (sub-function return type factory) arg");

	/**
	 * 
	 * map function
	 * 
	 * @param <SUB_RETURN_T>
	 *            subfunction return type
	 * 
	 */
	private static final class MapFunction<SUB_RETURN_T extends AttributeValue> extends OneBagOnlyHigherOrderFunction<Bag<SUB_RETURN_T>, SUB_RETURN_T>
	{

		private static final class Call<SUB_RETURN extends AttributeValue> extends OneBagOnlyHigherOrderFunction.Call<Bag<SUB_RETURN>, SUB_RETURN>
		{
			private final Datatype<SUB_RETURN> returnBagElementType;
			private final String indeterminateSubFuncEvalMessagePrefix;

			private Call(final String functionId, final Datatype<Bag<SUB_RETURN>> returnType, final FirstOrderFunction<SUB_RETURN> subFunction, final List<Expression<?>> primitiveInputsBeforeBag,
					final Expression<? extends Bag<?>> bagInput, List<Expression<?>> primitiveInputsAfterBag)
			{
				super(functionId, returnType, subFunction, primitiveInputsBeforeBag, bagInput, primitiveInputsAfterBag);
				this.returnBagElementType = subFunction.getReturnType();
				this.indeterminateSubFuncEvalMessagePrefix = "Function '" + functionId + "': Error calling sub-function (first argument) with bag arg (#" + this.bagArgIndex + ") = ";
			}

			@Override
			protected Bag<SUB_RETURN> evaluate(final Bag<?> bagArg, final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException {
				/*
				 * Prepare sub-function call's remaining args (bag arg and subsequent ones if any)
				 */
				final AttributeValue[] argsAfterBagInclusive = new AttributeValue[this.numOfArgsAfterBagInclusive];
				/*
				 * Index i=0 is for the bag element value, resolved in the second for loop below.
				 */
				int i = 1;
				/*
				 * See BaseFirstOrderFunctionCall#evalPrimitiveArgs(...)
				 */
				for (final Expression<?> primitiveArgExprAfterBag : this.primitiveArgExprsAfterBag)
				{
					// get and evaluate the next parameter
					/*
					 * The types of arguments have already been checked with checkInputs(), so casting to returnType should work.
					 */
					final AttributeValue argVal;
					try
					{
						argVal = Expressions.evalPrimitive(primitiveArgExprAfterBag, context, mdpContext);
					} catch (final IndeterminateEvaluationException e)
					{
						throw new IndeterminateEvaluationException("Indeterminate arg #" + (this.bagArgIndex + i), e);
					}

					argsAfterBagInclusive[i] = argVal;
					i++;
				}

				final Collection<SUB_RETURN> results = new ArrayDeque<>(bagArg.size());
				for (final AttributeValue bagElement : bagArg)
				{
					argsAfterBagInclusive[0] = bagElement;
					final SUB_RETURN subResult;
					try
					{
						subResult = subFuncCall.evaluate(context, mdpContext, argsAfterBagInclusive);
					} catch (final IndeterminateEvaluationException e)
					{
						throw new IndeterminateEvaluationException(indeterminateSubFuncEvalMessagePrefix + bagElement, e);
					}

					results.add(subResult);
				}

				return Bags.newBag(returnBagElementType, results);
			}
		}

		/**
		 * Creates 'Map' function for specific sub-function's return type
		 * 
		 * @param subFunctionReturnType
		 *            sub-function return type
		 */
		private MapFunction(final String functionId, final AttributeDatatype<SUB_RETURN_T> subFunctionReturnType)
		{
			super(functionId, subFunctionReturnType.getBagDatatype(), subFunctionReturnType);
		}

		@Override
		protected OneBagOnlyHigherOrderFunction.Call<Bag<SUB_RETURN_T>, SUB_RETURN_T> newFunctionCall(final FirstOrderFunction<SUB_RETURN_T> subFunc,
				final List<Expression<?>> primitiveInputsBeforeBag, final Expression<? extends Bag<?>> bagInput, final List<Expression<?>> primitiveInputsAfterBag) {
			return new Call<>(this.getId(), this.getReturnType(), subFunc, primitiveInputsBeforeBag, bagInput, primitiveInputsAfterBag);
		}

	}

	private final String functionId;

	MapFunctionFactory(final String functionId)
	{
		this.functionId = functionId;
	}

	@Override
	public String getId() {
		return functionId;
	}

	@Override
	public <SUB_RETURN extends AttributeValue> HigherOrderBagFunction<?, SUB_RETURN> getInstance(final Datatype<SUB_RETURN> subFunctionReturnType) throws IllegalArgumentException {
		if (subFunctionReturnType == null)
		{
			throw NULL_SUB_FUNCTION_RETURN_TYPE_ARG_EXCEPTION;
		}

		if (!(subFunctionReturnType instanceof AttributeDatatype<?>))
		{
			throw new IllegalArgumentException(
					"Invalid sub-function's return type specified for function '" + functionId + "': " + subFunctionReturnType + ". Expected: any primitive attribute datatype.");
		}

		return new MapFunction<>(functionId, (AttributeDatatype<SUB_RETURN>) subFunctionReturnType);
	}

}
