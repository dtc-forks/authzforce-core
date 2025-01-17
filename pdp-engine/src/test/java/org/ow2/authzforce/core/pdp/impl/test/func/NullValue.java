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
package org.ow2.authzforce.core.pdp.impl.test.func;

import net.sf.saxon.s9api.XdmValue;
import org.ow2.authzforce.core.pdp.api.value.Value;

/**
 * Special value to be interpreted as Indeterminate. (Mapped to IndeterminateExpression in FunctionTest class.) For testing only.
 *
 */
public class NullValue implements Value
{
	private final String datatypeId;
	private final boolean isBag;

	public NullValue(String datatype)
	{
		this(datatype, false);
	}

	public NullValue(String datatypeId, boolean isBag)
	{
		this.datatypeId = datatypeId;
		this.isBag = isBag;
	}

	public String getDatatypeId()
	{
		return this.datatypeId;
	}

	public boolean isBag()
	{
		return this.isBag;
	}

	@Override
	public XdmValue getXdmValue()
	{
		return null;
	}
}
