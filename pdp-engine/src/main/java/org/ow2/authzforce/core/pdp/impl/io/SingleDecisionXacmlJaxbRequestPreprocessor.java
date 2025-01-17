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
package org.ow2.authzforce.core.pdp.impl.io;

import com.google.common.collect.ImmutableList;
import net.sf.saxon.s9api.XdmNode;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Attribute;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Attributes;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import org.ow2.authzforce.core.pdp.api.*;
import org.ow2.authzforce.core.pdp.api.expression.XPathCompilerProxy;
import org.ow2.authzforce.core.pdp.api.io.*;
import org.ow2.authzforce.core.pdp.api.value.AttributeBag;
import org.ow2.authzforce.core.pdp.api.value.AttributeValueFactoryRegistry;
import org.ow2.authzforce.xacml.identifiers.XacmlStatusCode;

import java.util.*;
import java.util.Map.Entry;

/**
 * Default XACML/XML Request preprocessor Individual Decision Requests only (no support of Multiple Decision Profile in particular)
 *
 * @version $Id: $
 */
public final class SingleDecisionXacmlJaxbRequestPreprocessor extends BaseXacmlJaxbRequestPreprocessor
{
	private static final DecisionRequestFactory<ImmutableDecisionRequest> DEFAULT_REQUEST_FACTORY = ImmutableDecisionRequest::getInstance;

	/**
	 *
	 * Factory for this type of request preprocessor that allows duplicate &lt;Attribute&gt; with same meta-data in the same &lt;Attributes&gt; element of a Request (complying with XACML 3.0 core
	 * spec, §7.3.3).
	 *
	 */
	public static final class LaxVariantFactory extends BaseXacmlJaxbRequestPreprocessor.Factory
	{
		/**
		 * Request preprocessor ID, as returned by {@link #getId()}
		 */
		public static final String ID = "urn:ow2:authzforce:feature:pdp:request-preproc:xacml-xml:default-lax";

		/**
		 * Constructor
		 */
		public LaxVariantFactory()
		{
			super(ID);
		}

		@Override
		public DecisionRequestPreprocessor<Request, IndividualXacmlJaxbRequest> getInstance(final AttributeValueFactoryRegistry datatypeFactoryRegistry, final boolean strictAttributeIssuerMatch,
				final boolean requireContentForXPath, final Set<String> extraPdpFeatures)
		{
			return new SingleDecisionXacmlJaxbRequestPreprocessor(datatypeFactoryRegistry, DEFAULT_REQUEST_FACTORY, strictAttributeIssuerMatch, true, requireContentForXPath,
					extraPdpFeatures);
		}

		/**
		 * Singleton instance of Factory used as default request preprocessor
		 * 
		 */
		public static final DecisionRequestPreprocessor.Factory<Request, IndividualXacmlJaxbRequest> INSTANCE = new LaxVariantFactory();
	}

	/**
	 *
	 * Factory for this type of request preprocessor that does NOT allow duplicate &lt;Attribute&gt; with same meta-data in the same &lt;Attributes&gt; element of a Request (NOT complying fully with
	 * XACML 3.0 core spec, §7.3.3).
	 *
	 */
	public static final class StrictVariantFactory extends BaseXacmlJaxbRequestPreprocessor.Factory
	{
		/**
		 * Request preprocessor ID, as returned by {@link #getId()}
		 */
		public static final String ID = "urn:ow2:authzforce:feature:pdp:request-preproc:xacml-xml:default-strict";

		/**
		 * Constructor
		 */
		public StrictVariantFactory()
		{
			super(ID);
		}

		@Override
		public DecisionRequestPreprocessor<Request, IndividualXacmlJaxbRequest> getInstance(final AttributeValueFactoryRegistry datatypeFactoryRegistry, final boolean strictAttributeIssuerMatch,
				final boolean requireContentForXPath, final Set<String> extraPdpFeatures)
		{
			return new SingleDecisionXacmlJaxbRequestPreprocessor(datatypeFactoryRegistry, DEFAULT_REQUEST_FACTORY, strictAttributeIssuerMatch, false, requireContentForXPath,
					extraPdpFeatures);
		}
	}

	private final DecisionRequestFactory<ImmutableDecisionRequest> reqFactory;

	/**
	 * Creates instance of default request preprocessor
	 *
	 * @param datatypeFactoryRegistry
	 *            attribute datatype registry
	 * @param requestFactory
	 *            decision request factory
	 * @param strictAttributeIssuerMatch
	 *            true iff strict attribute Issuer match must be enforced (in particular request attributes with empty Issuer only match corresponding AttributeDesignators with empty Issuer)
	 * @param allowAttributeDuplicates
	 *            true iff duplicate Attribute (with same metadata) elements in Request (for multi-valued attributes) must be allowed
	 * @param requireContentForXPath
	 *            true iff Content elements must be parsed, else ignored
	 * @param extraPdpFeatures
	 *            extra - not mandatory per XACML 3.0 core specification - features supported by the PDP engine. This preprocessor checks whether it is supported by the PDP before processing the request further.
	 * @param namedAttributeParser custom parser of named Attributes, to customize how XACML Attributes are converted into instance of AuthzForce internal Attribute class
	 */
	public SingleDecisionXacmlJaxbRequestPreprocessor(final AttributeValueFactoryRegistry datatypeFactoryRegistry, final DecisionRequestFactory<ImmutableDecisionRequest> requestFactory,
													  final boolean strictAttributeIssuerMatch, final boolean allowAttributeDuplicates, final boolean requireContentForXPath, final Set<String> extraPdpFeatures, Optional<NamedXacmlAttributeParser<Attribute>> namedAttributeParser)
	{
		super(datatypeFactoryRegistry, strictAttributeIssuerMatch, allowAttributeDuplicates, requireContentForXPath, extraPdpFeatures, namedAttributeParser);
		assert requestFactory != null;
		reqFactory = requestFactory;
	}

	/**
	 * Creates instance of default request preprocessor
	 * 
	 * @param datatypeFactoryRegistry
	 *            attribute datatype registry
	 * @param requestFactory
	 *            decision request factory
	 * @param strictAttributeIssuerMatch
	 *            true iff strict attribute Issuer match must be enforced (in particular request attributes with empty Issuer only match corresponding AttributeDesignators with empty Issuer)
	 * @param allowAttributeDuplicates
	 *            true iff duplicate Attribute (with same metadata) elements in Request (for multi-valued attributes) must be allowed
	 * @param requireContentForXPath
	 *            true iff Content elements must be parsed, else ignored
	 * @param extraPdpFeatures
	 *            extra - not mandatory per XACML 3.0 core specification - features supported by the PDP engine. This preprocessor checks whether it is supported by the PDP before processing the
	 *            request further.
	 */
	public SingleDecisionXacmlJaxbRequestPreprocessor(final AttributeValueFactoryRegistry datatypeFactoryRegistry, final DecisionRequestFactory<ImmutableDecisionRequest> requestFactory,
			final boolean strictAttributeIssuerMatch, final boolean allowAttributeDuplicates, final boolean requireContentForXPath, final Set<String> extraPdpFeatures)
	{
		super(datatypeFactoryRegistry, strictAttributeIssuerMatch, allowAttributeDuplicates, requireContentForXPath, extraPdpFeatures);
		assert requestFactory != null;
		reqFactory = requestFactory;
	}

	@Override
	public List<IndividualXacmlJaxbRequest> process(final List<Attributes> attributesList, final SingleCategoryXacmlAttributesParser<Attributes> xacmlAttrsParser,
													final boolean isApplicablePolicyIdListReturned, final boolean combinedDecision, final Optional<XPathCompilerProxy> xPathCompiler, final Map<String, String> namespaceURIsByPrefix)
			throws IndeterminateEvaluationException
	{
		final Map<AttributeFqn, AttributeBag<?>> namedAttributes = HashCollections.newUpdatableMap(attributesList.size());
		final Map<String, XdmNode> extraContentsByCategory = HashCollections.newUpdatableMap(attributesList.size());
		/*
		 * attributesToIncludeInResult.size() <= attributesList.size()
		 */
		final List<Attributes> attributesToIncludeInResult = new ArrayList<>(attributesList.size());

		for (final Attributes jaxbAttributes : attributesList)
		{
			final SingleCategoryAttributes<?, Attributes> categorySpecificAttributes = xacmlAttrsParser.parseAttributes(jaxbAttributes, xPathCompiler);
			if (categorySpecificAttributes == null)
			{
				// skip this empty Attributes
				continue;
			}

			final String categoryId = categorySpecificAttributes.getCategoryId();
			final XdmNode newContentNode = categorySpecificAttributes.getExtraContent();
			if (newContentNode != null)
			{
				final XdmNode duplicate = extraContentsByCategory.putIfAbsent(categoryId, newContentNode);
				/*
				 * No support for Multiple Decision Profile -> no support for repeated categories as specified in Multiple Decision Profile. So we must check duplicate attribute categories.
				 */
				if (duplicate != null)
				{
					throw new IndeterminateEvaluationException("Unsupported repetition of Attributes[@Category='" + categoryId
							+ "'] (feature 'urn:oasis:names:tc:xacml:3.0:profile:multiple:repeated-attribute-categories' is not supported)", XacmlStatusCode.SYNTAX_ERROR.value());
				}
			}

			/*
			 * Convert growable (therefore mutable) bag of attribute values to immutable ones. Indeed, we must guarantee that attribute values remain constant during the evaluation of the request, as
			 * mandated by the XACML spec, section 7.3.5: <p> <i>
			 * "Regardless of any dynamic modifications of the request context during policy evaluation, the PDP SHALL behave as if each bag of attribute values is fully populated in the context before it is first tested, and is thereafter immutable during evaluation. (That is, every subsequent test of that attribute shall use the same bag of values that was initially tested.)"
			 * </i></p>
			 */
			for (final Entry<AttributeFqn, AttributeBag<?>> attrEntry : categorySpecificAttributes)
			{
				namedAttributes.put(attrEntry.getKey(), attrEntry.getValue());
			}

			final Attributes catSpecificAttrsToIncludeInResult = categorySpecificAttributes.getAttributesToIncludeInResult();
			if (catSpecificAttrsToIncludeInResult != null)
			{
				attributesToIncludeInResult.add(catSpecificAttrsToIncludeInResult);
			}
		}

		return Collections.singletonList(new IndividualXacmlJaxbRequest(reqFactory.getInstance(namedAttributes, extraContentsByCategory, isApplicablePolicyIdListReturned), ImmutableList
				.copyOf(attributesToIncludeInResult)));
	}
}
