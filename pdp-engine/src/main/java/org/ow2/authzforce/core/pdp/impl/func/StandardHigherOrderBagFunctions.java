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
import org.ow2.authzforce.core.pdp.api.func.FirstOrderFunctionCall;
import org.ow2.authzforce.core.pdp.api.func.FunctionCall;
import org.ow2.authzforce.core.pdp.api.func.HigherOrderBagFunction;
import org.ow2.authzforce.core.pdp.api.value.*;

import java.util.*;

/**
 * Set of higher-order bag functions
 *
 * @version $Id: $
 */
final class StandardHigherOrderBagFunctions
{

    private static abstract class BooleanHigherOrderBagFunction extends HigherOrderBagFunction<BooleanValue, BooleanValue>
    {

        protected final String subFunctionCallErrorMessagePrefix;

        private BooleanHigherOrderBagFunction(final String functionId)
        {
            super(functionId, StandardDatatypes.BOOLEAN, StandardDatatypes.BOOLEAN);
            this.subFunctionCallErrorMessagePrefix = "Function '" + functionId + "': Error evaluating sub-function with arguments (evaluated to): ";
        }
    }

    /**
     * Higher-order boolean function taking three arguments: sub-function and two bags
     */
    private static abstract class BooleanHigherOrderTwoBagFunction extends BooleanHigherOrderBagFunction
    {
        private final IllegalArgumentException invalidLastArgTypeException = new IllegalArgumentException("Function" + this + ": Invalid last argument type: primitive (not a bag). Required: a bag");

        private BooleanHigherOrderTwoBagFunction(final String functionId)
        {
            super(functionId);
        }

        @Override
        protected final void checkNumberOfArgs(final int numInputs)
        {
            if (numInputs != 3)
            {
                throw new IllegalArgumentException("Function " + this + ": Invalid number of arguments (" + numInputs + "). Required: 3");
            }
        }

        private static abstract class Call implements FunctionCall<BooleanValue>
        {
            protected final FirstOrderFunctionCall<BooleanValue> subFuncCall;
            private final String errorEvalArg1Message;
            private final String errorEvalArg2Message;
            private final Expression<? extends Bag<?>> bagArgExpr0;
            private final Expression<? extends Bag<?>> bagArgExpr1;

            private Call(final String functionId, final FirstOrderFunction<BooleanValue> subFunc, final Expression<? extends Bag<?>> input0, final Expression<? extends Bag<?>> input1)
            {
                final Optional<? extends Datatype<?>> bagElementType0 = input0.getReturnType().getTypeParameter();
                final Optional<? extends Datatype<?>> bagElementType1 = input1.getReturnType().getTypeParameter();
                assert bagElementType0.isPresent() && bagElementType1.isPresent();

                final Datatype<?>[] subFuncArgTypes = {bagElementType0.get(), bagElementType1.get()};
                this.subFuncCall = subFunc.newCall(Collections.emptyList(), subFuncArgTypes);
                this.bagArgExpr0 = input0;
                this.bagArgExpr1 = input1;
                this.errorEvalArg1Message = "Function '" + functionId + "': Error evaluating second arg #1";
                this.errorEvalArg2Message = "Function '" + functionId + "': Error evaluating arg #2";
            }

            protected abstract BooleanValue evaluate(final Bag<?> bag0, final Bag<?> bag1, final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException;

            @Override
            public final BooleanValue evaluate(final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException
            {
                final Bag<?> bag0;
                try
                {
                    bag0 = bagArgExpr0.evaluate(context, mdpContext);
                } catch (final IndeterminateEvaluationException e)
                {
                    throw new IndeterminateEvaluationException(errorEvalArg1Message, e.getTopLevelStatus().getStatusCode().getValue());
                }

                /*
                 * If result bag empty, returns False as there will be no possibility for a Predicate that is "True". AttributeDesignator/AttributeSelector with MustBePresent=False may evaluate to
                 * empty bags (Indeterminate Exception if MustBePresent=True). empty bag.
                 */
                if (bag0.isEmpty())
                {
                    return BooleanValue.FALSE;
                }

                final Bag<?> bag1;
                try
                {
                    bag1 = bagArgExpr1.evaluate(context, mdpContext);
                } catch (final IndeterminateEvaluationException e)
                {
                    throw new IndeterminateEvaluationException(errorEvalArg2Message, e.getTopLevelStatus().getStatusCode().getValue());
                }

                if (bag1.isEmpty())
                {
                    return BooleanValue.FALSE;
                }

                return evaluate(bag0, bag1, context, mdpContext);
            }

            @Override
            public final Datatype<BooleanValue> getReturnType()
            {
                return StandardDatatypes.BOOLEAN;
            }

        }

        protected abstract BooleanValue evaluate(final FirstOrderFunctionCall<BooleanValue> subFunctionCall, final Bag<?> bag0, final Bag<?> bag1, final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException;

        @Override
        protected final FunctionCall<BooleanValue> createFunctionCallFromSubFunction(final FirstOrderFunction<BooleanValue> subFunc, final List<Expression<?>> inputsAfterSubFunc)
        {

            for (Expression<?> expression : inputsAfterSubFunc)
            {
                // all must be bags
                if (expression.getReturnType().getTypeParameter().isEmpty())
                {
                    throw invalidLastArgTypeException;
                }
            }

            final Expression<? extends Bag<?>> input0 = (Expression<? extends Bag<?>>) inputsAfterSubFunc.get(0);
            final Expression<? extends Bag<?>> input1 = (Expression<? extends Bag<?>>) inputsAfterSubFunc.get(1);
            return new BooleanHigherOrderTwoBagFunction.Call(this.getId(), subFunc, input0, input1)
            {

                @Override
                protected BooleanValue evaluate(final Bag<?> bag0, final Bag<?> bag1, final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException
                {
                    return BooleanHigherOrderTwoBagFunction.this.evaluate(subFuncCall, bag0, bag1, context, mdpContext);
                }
            };
        }
    }

    /**
     * one-bag-only functions (one and only one arg is bag): any-of, all-of, map. For the map function, the (only) bag argument can be any arg following the sub-function.
     */
    static abstract class OneBagOnlyHigherOrderFunction<RETURN_T extends Value, SUB_RETURN_T extends AttributeValue> extends HigherOrderBagFunction<RETURN_T, SUB_RETURN_T>
    {
        private final String invalidArityMsgPrefix = "Function '" + this + "': Invalid number of arguments: expected: >= 2; actual: ";
        private final IllegalArgumentException missingBagInputErrorMsg = new IllegalArgumentException("Function '" + this + "': Invalid args (expected: one and only one bag; actual: none)");
        private final String invalidArgTypeErrorMsg = "Function '" + this + "': Invalid argument type (expected: primitive; actual: bag) for arg #";

        static abstract class Call<RETURN extends Value, SUB_RETURN extends AttributeValue> implements FunctionCall<RETURN>
        {
            private final String errorEvalBagArgMsg;
            private final Expression<?> bagArgExpr;
            private final BagDatatype<?> bagArgDatatype;
            private final Datatype<RETURN> returnType;
            protected final int bagArgIndex;
            protected final List<Expression<?>> primitiveArgExprsAfterBag;
            protected final int numOfArgsAfterBagInclusive;
            protected final FirstOrderFunctionCall<SUB_RETURN> subFuncCall;

            protected Call(final String functionId, final Datatype<RETURN> returnType, final FirstOrderFunction<SUB_RETURN> subFunction, final List<Expression<?>> primitiveInputsBeforeBag,
                           final Expression<? extends Bag<?>> bagInput, final List<Expression<?>> primitiveInputsAfterBag)
            {
                final Datatype<? extends Bag<?>> bagInputType = bagInput.getReturnType();
                /*
                 * BagDatatype is expected to be the only Datatype implementation for Datatype<Bag<?>>
                 */
                assert bagInputType instanceof BagDatatype;

                bagArgDatatype = (BagDatatype<?>) bagInputType;
                bagArgExpr = bagInput;
                bagArgIndex = 1 + primitiveInputsBeforeBag.size();
                primitiveArgExprsAfterBag = primitiveInputsAfterBag;
                /*
                 * Number of args starting with the bag (inclusive)
                 */
                numOfArgsAfterBagInclusive = 1 + primitiveInputsAfterBag.size();

                /*
                 * Prepare args to sub-function Each actual value that comes from the bag and that is passed to the sub-function is not yet known; but we know the expected datatype that is the type of
                 * each element in the bag.
                 */
                final Datatype<?>[] typesOfArgsAfterBagInclusive = new Datatype<?>[numOfArgsAfterBagInclusive];
                typesOfArgsAfterBagInclusive[0] = bagArgDatatype.getElementType();
                int i = 1;
                for (final Expression<?> primitiveInputAfterBag : primitiveInputsAfterBag)
                {
                    typesOfArgsAfterBagInclusive[i] = primitiveInputAfterBag.getReturnType();
                    i++;
                }

                this.subFuncCall = subFunction.newCall(primitiveInputsBeforeBag, typesOfArgsAfterBagInclusive);
                this.errorEvalBagArgMsg = "Function '" + functionId + "': Error evaluating bag arg (arg #" + bagArgIndex + ")";
                this.returnType = returnType;
            }

            /**
             * Evaluates the function call. The evaluation combines the results of all <i>eval<sub>i</sub></i> for <i>i</i> in <pre>[0..p-1]</pre>, where <i>eval<sub>i</sub></i> is the evaluation of the
             * sub-function (in this higher-order function call, i.e. first arg) with the following arguments, except for the one that is a bag, we take the i-th value in the bag as actual
             * sub-function arg, with p the size of the bag.
             *
             * @param bagArg  the bag of which each value is used successively as the primitive argument to the sub-function in the same position
             * @param context evaluation context in which arguments are evaluated
             * @return result combined result (depending on the implementation)
             * @throws IndeterminateEvaluationException if any error occurred during evaluation
             */
            protected abstract RETURN evaluate(final Bag<?> bagArg, final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException;

            @Override
            public final RETURN evaluate(final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException
            {
                final Bag<?> bagArg;
                try
                {
                    bagArg = Expressions.eval(bagArgExpr, context, mdpContext, bagArgDatatype);
                } catch (final IndeterminateEvaluationException e)
                {
                    throw new IndeterminateEvaluationException(errorEvalBagArgMsg, e);
                }

                return evaluate(bagArg, context, mdpContext);
            }

            @Override
            public final Datatype<RETURN> getReturnType()
            {
                return returnType;
            }
        }

        /*
         * 'protected' because used by separate MapFunction class
         */
        protected OneBagOnlyHigherOrderFunction(final String functionName, final Datatype<RETURN_T> returnType, final Datatype<SUB_RETURN_T> subFunctionReturnType)
        {
            super(functionName, returnType, subFunctionReturnType);
        }

        @Override
        protected final void checkNumberOfArgs(final int numInputs)
        {
            if (numInputs < 2)
            {
                throw new IllegalArgumentException(invalidArityMsgPrefix + numInputs);
            }
        }

        /**
         * Creates function call
         *
         * @param subFunc                  sub-function
         * @param primitiveInputsBeforeBag all primitive arguments before bag (datatype already checked).
         * @param bagInput                 bag argument (datatype already checked)
         * @return function call
         */
        protected abstract Call<RETURN_T, SUB_RETURN_T> newFunctionCall(FirstOrderFunction<SUB_RETURN_T> subFunc, List<Expression<?>> primitiveInputsBeforeBag, Expression<? extends Bag<?>> bagInput,
                                                                        final List<Expression<?>> primitiveInputsAfterBag);

        @Override
        protected final FunctionCall<RETURN_T> createFunctionCallFromSubFunction(final FirstOrderFunction<SUB_RETURN_T> subFunc, final List<Expression<?>> inputsAfterSubFunc)
        {
            /*
             * Inputs that we can parse/validate for the sub-function are the primitive inputs, i.e. all except last one which is a bag
             */
            final List<Expression<?>> primitiveInputsBeforeBag = new ArrayList<>();
            Expression<? extends Bag<?>> bagInput = null;
            final List<Expression<?>> primitiveInputsAfterBag = new ArrayList<>();
            /*
             * Arg index of input after sub-function (= arg #0 of this higher-order function)
             */
            int argIndex = 1;
            for (final Expression<?> input : inputsAfterSubFunc)
            {
                final Datatype<?> inputType = input.getReturnType();
                final Optional<? extends Datatype<?>> typeParam = inputType.getTypeParameter();
                if (bagInput == null)
                {
                    /*
                     * No bag arg found yet
                     */
                    if (typeParam.isPresent())
                    {
                        /*
                         * Not primitive but bag
                         */
                        bagInput = (Expression<? extends Bag<?>>) input;
                    } else
                    {
                        /*
                         * Primitive before the bag
                         */
                        primitiveInputsBeforeBag.add(input);
                    }
                } else
                {
                    /*
                     * Bag arg already found -> this arg must be a primitive as there can be only one bag arg
                     */
                    if (typeParam.isPresent())
                    {
                        /*
                         * Bag again!
                         */
                        throw new IllegalArgumentException(invalidArgTypeErrorMsg + argIndex);
                    }

                    /*
                     * Primitive after the bag
                     */
                    primitiveInputsAfterBag.add(input);

                }

                argIndex++;
            }

            if (bagInput == null)
            {
                throw missingBagInputErrorMsg;
            }

            return newFunctionCall(subFunc, primitiveInputsBeforeBag, bagInput, primitiveInputsAfterBag);
        }

    }

    static final class BooleanOneBagOnlyFunction extends OneBagOnlyHigherOrderFunction<BooleanValue, BooleanValue>
    {
        private interface CallFactory
        {

            /**
             * Get the final result of this higher-order function, based on one of the sub-function evaluation result
             *
             * @param subFunctionResult result of evaluating sub-function one arg in the bag
             * @return final result if the subFunctionResult is sufficient to know the final result, else null
             */
            BooleanValue getFinalResult(BooleanValue subFunctionResult);

            /**
             * Get default final result of this higher-order function, i.e. the final result when {@link #getFinalResult(BooleanValue)} returned null for all sub-function evaluations (one per arg in
             * the bag)
             *
             * @return default final result
             */
            BooleanValue defaultFinalResult();
        }

        private final CallFactory funcCallFactory;
        private final String subFuncCallWithLastArgErrMsgPrefix;

        protected BooleanOneBagOnlyFunction(final String functionId, final CallFactory functionCallFactory)
        {
            super(functionId, StandardDatatypes.BOOLEAN, StandardDatatypes.BOOLEAN);
            this.funcCallFactory = functionCallFactory;
            this.subFuncCallWithLastArgErrMsgPrefix = "Function '" + functionId + "': Error calling sub-function (specified as first argument) with last arg=";
        }

        @Override
        protected OneBagOnlyHigherOrderFunction.Call<BooleanValue, BooleanValue> newFunctionCall(FirstOrderFunction<BooleanValue> subFunc, List<Expression<?>> primitiveInputsBeforeBag,
                                                                                                 Expression<? extends Bag<?>> bagInput, final List<Expression<?>> primitiveInputsAfterBag)
        {
            return new OneBagOnlyHigherOrderFunction.Call<>(getId(), StandardDatatypes.BOOLEAN, subFunc, primitiveInputsBeforeBag, bagInput, Collections.emptyList())
            {

                @Override
                protected BooleanValue evaluate(final Bag<?> lastArgBag, final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException
                {
                    for (final AttributeValue attrVal : lastArgBag)
                    {
                        final BooleanValue subResult;
                        try
                        {
                            subResult = subFuncCall.evaluate(context, mdpContext, attrVal);
                        } catch (final IndeterminateEvaluationException e)
                        {
                            throw new IndeterminateEvaluationException(subFuncCallWithLastArgErrMsgPrefix + attrVal, e);
                        }

                        final BooleanValue finalResult = funcCallFactory.getFinalResult(subResult);
                        if (finalResult != null)
                        {
                            return finalResult;
                        }
                    }

                    return funcCallFactory.defaultFinalResult();
                }

            };
        }

    }

    /**
     * any-of function
     */
    static final BooleanOneBagOnlyFunction.CallFactory ANY_OF_CALL_FACTORY = new BooleanOneBagOnlyFunction.CallFactory()
    {

        @Override
        public BooleanValue getFinalResult(final BooleanValue subFunctionResult)
        {
            if (subFunctionResult.getUnderlyingValue())
            {
                return BooleanValue.TRUE;
            }

            return null;
        }

        @Override
        public BooleanValue defaultFinalResult()
        {
            return BooleanValue.FALSE;
        }

    };

    /**
     * all-of function
     */
    static final BooleanOneBagOnlyFunction.CallFactory ALL_OF_CALL_FACTORY = new BooleanOneBagOnlyFunction.CallFactory()
    {

        @Override
        public BooleanValue getFinalResult(final BooleanValue subFunctionResult)
        {
            if (!subFunctionResult.getUnderlyingValue())
            {
                return BooleanValue.FALSE;
            }

            return null;
        }

        @Override
        public BooleanValue defaultFinalResult()
        {
            return BooleanValue.TRUE;
        }

    };

    /**
     * any-of-any function
     */
    static final class AnyOfAny extends BooleanHigherOrderBagFunction
    {
        private final String subFuncArgEvalErrMsg;

        /**
         * Default constructor
         *
         * @param functionId function identifier
         */
        AnyOfAny(final String functionId)
        {
            super(functionId);
            this.subFuncArgEvalErrMsg = "Function '" + functionId + "': Error evaluating one of the arguments after sub-function";
        }

        @Override
        protected void checkNumberOfArgs(final int numInputs)
        {
            if (numInputs < 2)
            {
                throw new IllegalArgumentException("Function " + this + ": Invalid number of arguments (" + numInputs + "). Required: >= 2");
            }
        }

        @Override
        protected FunctionCall<BooleanValue> createFunctionCallFromSubFunction(final FirstOrderFunction<BooleanValue> subFunc, final List<Expression<?>> inputsAfterSubFunc)
        {
            return new AnyOfAnyFunctionCall(subFunc, inputsAfterSubFunc);
        }

        private final class AnyOfAnyFunctionCall implements FunctionCall<BooleanValue>
        {
            private final FirstOrderFunctionCall<BooleanValue> subFuncCall;
            private final int subFuncArity;
            private final List<Expression<?>> inputsAfterSubFunc;

            protected AnyOfAnyFunctionCall(final FirstOrderFunction<BooleanValue> subFunc, final List<Expression<?>> inputsAfterSubFunc)
            {
                /*
                 * According to spec of an-of-any function, the remaining arguments (inputsAfterSubFunc here) are either primitive data types or bags of primitive types. The expression SHALL be
                 * evaluated as if the function named in the <Function> argument (subFunc here) was applied between every tuple of the cross product on all bags and the primitive values.
                 */
                this.subFuncArity = inputsAfterSubFunc.size();
                final Datatype<?>[] subFuncArgTypes = new Datatype<?>[subFuncArity];
                int i = 0;
                for (final Expression<?> input : inputsAfterSubFunc)
                {
                    final Datatype<?> inputDatatype = input.getReturnType();
                    /*
                     * Always primitive datatype are used in the sub-function call (typeParameter of the datatype if it is a generic/bag datatype, else the datatype itself (already primitive))
                     */
                    final Optional<? extends Datatype<?>> typeParam = inputDatatype.getTypeParameter();
                    subFuncArgTypes[i] = typeParam.isPresent() ? typeParam.get() : inputDatatype;
                    i++;
                }

                this.subFuncCall = subFunc.newCall(Collections.emptyList(), subFuncArgTypes);
                this.inputsAfterSubFunc = inputsAfterSubFunc;
            }

            private BooleanValue eval(final Iterator<Expression<?>> argExpressionsAfterSubFuncIterator, final ListIterator<Value> argValuesAfterSubFuncIterator,
                                      final Deque<AttributeValue> subFuncArgsStack, final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException
            {
                final Value argVal;
                if (argExpressionsAfterSubFuncIterator.hasNext())
                {
                    // we are still evaluating argument expressions for the first time
                    try
                    {
                        argVal = argExpressionsAfterSubFuncIterator.next().evaluate(context, mdpContext);

                    } catch (final IndeterminateEvaluationException e)
                    {
                        throw new IndeterminateEvaluationException(subFuncArgEvalErrMsg, e);
                    }
                    // save the result for reuse when building the next list of sub-function
                    // arguments to avoid re-evaluation
                    argValuesAfterSubFuncIterator.add(argVal);
                } else
                {
                    /*
                     * No more arg expression to evaluate, but we may have evaluated them all with results put in argValuesAfterSubFuncIterator, then started a new combination of arguments from the
                     * start, working with argValuesAfterSubFuncIterator only after that. So check where we are with argValuesAfterSubFuncIterator
                     */
                    if (argValuesAfterSubFuncIterator.hasNext())
                    {
                        argVal = argValuesAfterSubFuncIterator.next();
                    } else
                    {
                        // no more argument to add to the list of sub-function arguments
                        argVal = null;
                    }
                }

                if (argVal == null)
                {
                    // we finished a list of sub-function arguments, so we can call the sub-function
                    // with it
                    final AttributeValue[] subFuncArgValues = subFuncArgsStack.toArray(new AttributeValue[subFuncArity]);
                    try
                    {
                        return subFuncCall.evaluate(context, mdpContext, subFuncArgValues);
                    } catch (final IndeterminateEvaluationException e)
                    {
                        throw new IndeterminateEvaluationException(subFunctionCallErrorMessagePrefix + subFuncArgsStack, e);
                    }
                }

                // argVal != null
                if (argVal instanceof Bag)
                {
                    // arg value is a bag
                    /*
                     * If bag empty, returns False as there will be no possibility for a predicate to be "True"; in particular if AttributeDesignator/AttributeSelector with MustBePresent=False
                     * evaluates to empty bag.
                     */
                    final Bag<?> argBag = (Bag<?>) argVal;
                    if (argBag.isEmpty())
                    {
                        return BooleanValue.FALSE;
                    }
                    /*
                     * For each value in the arg bag, add it to the sub-function argument stack and call eval()
                     */

                    for (final AttributeValue argBagVal : argBag)
                    {
                        subFuncArgsStack.add(argBagVal);
                        final BooleanValue subResult = eval(argExpressionsAfterSubFuncIterator, argValuesAfterSubFuncIterator, subFuncArgsStack, context, mdpContext);
                        if (subResult.getUnderlyingValue())
                        {
                            return BooleanValue.TRUE;
                        }

                        /*
                         * Remove the arg we just added at the start of the iteration, to leave the place for the new arg in the next iteration
                         */
                        subFuncArgsStack.removeLast();
                    }

                } else
                {
                    // arg value is primitive
                    // add it to the sub-function call's argument stack
                    subFuncArgsStack.add((AttributeValue) argVal);
                    // evaluate with the new arg stack
                    final BooleanValue subResult = eval(argExpressionsAfterSubFuncIterator, argValuesAfterSubFuncIterator, subFuncArgsStack, context, mdpContext);
                    if (subResult.getUnderlyingValue())
                    {
                        return BooleanValue.TRUE;
                    }

                    /*
                     * Remove the arg we just added at the start of the iteration, to leave the place for the new arg in the next iteration
                     */
                    subFuncArgsStack.removeLast();
                }

                /*
                 * argVal != null and either argValuesAfterSubFuncIterator.next() or argValuesAfterSubFuncIterator.add(...) was called so we need to go backwards now to prepare next eval().
                 */
                argValuesAfterSubFuncIterator.previous();
                return BooleanValue.FALSE;

            }

            @Override
            public BooleanValue evaluate(final EvaluationContext context, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException
            {
                /*
                 * For each input expression coming from inputsAfterSubFunc, the evaluation result will be added to the following list, to avoid evaluating the same expression again as each one will
                 * be reused in multiple combination of arguments:
                 */
                final List<Value> inputsAfterSubFuncEvalResults = new ArrayList<>(inputsAfterSubFunc.size());

                /*
                 * We build the stack (Deque) of sub-function argument values (extracted progressively from inputsAfterSubFuncEvalResults). Deque provides LIFO stack which is convenient for managing
                 * the sub-function arguments because we will be pushing/popping for each value in each bag argument, to make the list of the sub-function's arguments before we can make each
                 * sub-function call.
                 */
                final Deque<AttributeValue> subFuncArgsStack = new ArrayDeque<>(subFuncArity);

                // the subsequent logic is put in separated method because we need to call it
                // recursively over nonFirstArgExpsIterator
                return eval(inputsAfterSubFunc.iterator(), inputsAfterSubFuncEvalResults.listIterator(), subFuncArgsStack, context, mdpContext);
            }

            @Override
            public Datatype<BooleanValue> getReturnType()
            {
                return StandardDatatypes.BOOLEAN;
            }

        }

    }

    /**
     * Class for common behavior between all-of-any and any-of-all
     */
    private static abstract class BooleanHigherOrderTwoBagAnyFunction extends BooleanHigherOrderTwoBagFunction
    {
        private interface ArgSelector
        {
            /**
             * Get bag used for the "all" part
             *
             * @param arg0 first arg
             * @param arg1 second arg
             * @return bag for "all"
             */
            Bag<?> getBagForAll(Bag<?> arg0, Bag<?> arg1);

            /**
             * Get bag used for the "any" part
             *
             * @param arg0 first arg
             * @param arg1 second arg
             * @return bag for "any"
             */
            Bag<?> getBagForAny(Bag<?> arg0, Bag<?> arg1);
        }

        private static final ArgSelector FIRST_BAG_FOR_ALL_ARGSELECTOR = new ArgSelector()
        {

            @Override
            public Bag<?> getBagForAll(final Bag<?> bag0, final Bag<?> bag1)
            {
                return bag0;
            }

            @Override
            public Bag<?> getBagForAny(final Bag<?> bag0, final Bag<?> bag1)
            {
                return bag1;
            }

        };

        private static final ArgSelector SECOND_BAG_FOR_ALL_ARGSELECTOR = new ArgSelector()
        {

            @Override
            public Bag<?> getBagForAll(final Bag<?> bag0, final Bag<?> bag1)
            {
                return bag1;
            }

            @Override
            public Bag<?> getBagForAny(final Bag<?> bag0, final Bag<?> bag1)
            {
                return bag0;
            }

        };

        private final int bagForAllArgIndex;
        private final int bagForAnyArgIndex;
        private final ArgSelector argSelector;

        private BooleanHigherOrderTwoBagAnyFunction(final String functionId, final boolean useAllFirstBag)
        {
            super(functionId);
            if (useAllFirstBag)
            {
                // the bag for "all" part is the first arg to the sub-function
                bagForAllArgIndex = 0;
                bagForAnyArgIndex = 1;
                argSelector = FIRST_BAG_FOR_ALL_ARGSELECTOR;
            } else
            {
                /*
                 * the bag for "all" part is the second arg to the sub-function, so reverse
                 */
                bagForAllArgIndex = 1;
                bagForAnyArgIndex = 0;
                argSelector = SECOND_BAG_FOR_ALL_ARGSELECTOR;
            }
        }

        @Override
        protected BooleanValue evaluate(final FirstOrderFunctionCall<BooleanValue> subFunctionCall, final Bag<?> bag0, final Bag<?> bag1, final EvaluationContext context, final Optional<EvaluationContext> mdpContext)
                throws IndeterminateEvaluationException
        {
            final AttributeValue[] subFuncArgs = new AttributeValue[2];
            for (final AttributeValue bagAllUsed : argSelector.getBagForAll(bag0, bag1))
            {
                boolean isAnyTrue = false;
                subFuncArgs[bagForAllArgIndex] = bagAllUsed;
                for (final AttributeValue otherBag : argSelector.getBagForAny(bag0, bag1))
                {
                    subFuncArgs[bagForAnyArgIndex] = otherBag;
                    final BooleanValue subResult;
                    try
                    {
                        subResult = subFunctionCall.evaluate(context, mdpContext, subFuncArgs);
                    } catch (final IndeterminateEvaluationException e)
                    {
                        throw new IndeterminateEvaluationException(subFunctionCallErrorMessagePrefix + Arrays.toString(subFuncArgs), e);
                    }

                    if (subResult.getUnderlyingValue())
                    {
                        isAnyTrue = true;
                        break;
                    }
                }

                if (!isAnyTrue)
                {
                    return BooleanValue.FALSE;
                }
            }

            return BooleanValue.TRUE;
        }
    }

    /**
     * all-of-any function
     */
    static final class AllOfAny extends BooleanHigherOrderTwoBagAnyFunction
    {
        /**
         * Default constructor
         *
         * @param functionId function identifier
         */
        AllOfAny(final String functionId)
        {
            super(functionId, true);
        }
    }

    /**
     * any-of-all function
     */
    static final class AnyOfAll extends BooleanHigherOrderTwoBagAnyFunction
    {
        /**
         * Default constructor
         */
        AnyOfAll(final String functionId)
        {
            super(functionId, false);
        }
    }

    /**
     * any-of-all function
     */
    static final class AllOfAll extends BooleanHigherOrderTwoBagFunction
    {

        /**
         * Default constructor
         */
        AllOfAll(final String functionId)
        {
            super(functionId);
        }

        @Override
        protected BooleanValue evaluate(final FirstOrderFunctionCall<BooleanValue> subFunctionCall, final Bag<?> bag0, final Bag<?> bag1, final EvaluationContext context, final Optional<EvaluationContext> mdpContext)
                throws IndeterminateEvaluationException
        {
            final AttributeValue[] subFuncArgs = new AttributeValue[2];
            for (final AttributeValue bag0Val : bag0)
            {
                subFuncArgs[0] = bag0Val;
                boolean areAllTrue = true;
                for (final AttributeValue bag1Val : bag1)
                {
                    subFuncArgs[1] = bag1Val;
                    final BooleanValue subResult;
                    try
                    {
                        subResult = subFunctionCall.evaluate(context, mdpContext, subFuncArgs);
                    } catch (final IndeterminateEvaluationException e)
                    {
                        throw new IndeterminateEvaluationException(subFunctionCallErrorMessagePrefix + Arrays.toString(subFuncArgs), e);
                    }

                    if (!subResult.getUnderlyingValue())
                    {
                        areAllTrue = false;
                        break;
                    }
                }

                if (!areAllTrue)
                {
                    return BooleanValue.FALSE;
                }
            }

            return BooleanValue.TRUE;
        }

    }

    private StandardHigherOrderBagFunctions()
    {
        // empty private constructor to prevent instantiation
    }

}
