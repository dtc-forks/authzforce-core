/**
 * Copyright (C) 2011-2013 Thales Services - ThereSIS - All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.sun.xacml.support.finder;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.xacml.ParsingException;
import com.sun.xacml.PolicyMetaData;
import com.sun.xacml.VersionConstraints;
import com.sun.xacml.finder.PolicyFinder;
import com.sun.xacml.finder.PolicyFinderModule;
import com.sun.xacml.finder.PolicyFinderResult;
import com.sun.xacml.xacmlv3.Policy;


/**
 * This is a simple implementation of <code>PolicyFinderModule</code> that
 * supports retrieval based on reference, and is designed for use with a
 * run-time configuration. Its constructor accepts a <code>List</code> of
 * <code>String</code>s that represent URLs or files, and these are resolved
 * to policies when the module is initialized. Beyond this, there is no
 * modifying or re-loading the policies represented by this class. The
 * policy's identifiers are used for reference resolution.
 * <p>
 * Note that this class is designed to complement
 * <code>StaticPolicyFinderModule</code>. It would be easy to support both
 * kinds of policy retrieval in a single class, but the functionality is
 * instead split between two classes. The reason is that when you define a
 * configuration for your PDP, it's easier to specify the two sets of policies
 * by using two different finder modules. Typically, there aren't many
 * policies that exist in both sets, so loading the sets separately isn't
 * a problem. If this is a concern to you, simply create your own class and
 * merge the two existing classes.
 * <p>
 * This module is provided as an example, but is still fully functional, and
 * should be useful for many simple applications. This is provided in the
 * <code>support</code> package rather than the core codebase because it
 * implements non-standard behavior.
 *
 * @since 2.0
 * @author Seth Proctor
 */
public class StaticRefPolicyFinderModule extends PolicyFinderModule
{

    // the list of policy URLs passed to the constructor
    private List policyList;

    // the map of policies
    private PolicyCollection policies;

    // the optional schema file
    private File schemaFile = null;

    // the LOGGER we'll use for all messages
    private static final Logger LOGGER =
        LoggerFactory.getLogger(StaticRefPolicyFinderModule.class.getName());

    /**
     * Creates a <code>StaticRefPolicyFinderModule</code> that provides
     * access to the given collection of policies. Any policy that cannot
     * be loaded will be noted in the log, but will not cause an error. The
     * schema file used to validate policies is defined by the property
     * <code>PolicyReader.POLICY_SCHEMA_PROPERTY</code>. If the retrieved
     * property is null, then no schema validation will occur.
     *
     * @param policyList a <code>List</code> of <code>String</code>s that
     *                   represent URLs or files pointing to XACML policies
     */
    public StaticRefPolicyFinderModule(List policyList) {
        this.policyList = policyList;
        this.policies = new PolicyCollection();

        String schemaName =
            System.getProperty(PolicyReader.POLICY_SCHEMA_PROPERTY);
        if (schemaName != null)
            schemaFile = new File(schemaName);
    }

    /**
     * Creates a <code>StaticRefPolicyFinderModule</code> that provides
     * access to the given collection of policyList.
     *
     * @param policyList a <code>List</code> of <code>String</code>s that
     *                   represent URLs or files pointing to XACML policies
     * @param schemaFile the schema file to validate policies against,
     *                   or null if schema validation is not desired
     */
    public StaticRefPolicyFinderModule(List policyList, String schemaFile) {
        this.policyList = policyList;
        this.policies = new PolicyCollection();
        
        if (schemaFile != null)
            this.schemaFile = new File(schemaFile);
    }

    /**
     * Always returns <code>true</code> since this module does support
     * finding policies based on reference.
     *
     * @return true
     */
    public boolean isIdReferenceSupported() {
        return true;
    }

    /**
     * Initialize this module. Typically this is called by
     * <code>PolicyFinder</code> when a PDP is created. This method is
     * where the policies are actually loaded.
     *
     * @param finder the <code>PolicyFinder</code> using this module
     */
    public void init(PolicyFinder finder) {
        // now that we have the PolicyFinder, we can load the policies
        PolicyReader reader = new PolicyReader(finder, LOGGER, schemaFile);

        Iterator it = policyList.iterator();
        while (it.hasNext()) {
            String str = (String)(it.next());
            Policy policy = null;

            try {
                try {
                    // first try to load it as a URL
                    URL url = new URL(str);
                    policy = reader.readPolicy(url);
                } catch (MalformedURLException murle) {
                    // assume that this is a filename, and try again
                    policy = reader.readPolicy(new File(str));
                }

                // we loaded the policy, so try putting it in the collection
                if (! policies.addPolicy(policy))
                        LOGGER.warn("tried to load the same policy multiple times: {}", str);
            } catch (ParsingException pe) {
                    LOGGER.warn("Error reading policy: {}", str,
                               pe);
            }
        }
    }

    /**
     * Attempts to find a policy by reference, based on the provided
     * parameters.
     *
     * @param idReference an identifier specifying some policy
     * @param type type of reference (policy or policySet) as identified by
     *             the fields in <code>PolicyReference</code>
     * @param constraints any optional constraints on the version of the
     *                    referenced policy (this will never be null, but
     *                    it may impose no constraints, and in fact will
     *                    never impose constraints when used from a pre-2.0
     *                    XACML policy)
     * @param parentMetaData the meta-data from the parent policy, which
     *                       provides XACML version, factories, etc.
     *
     * @return the result of looking for a matching policy
     */
    public PolicyFinderResult findPolicy(URI idReference, int type,
                                         VersionConstraints constraints,
                                         PolicyMetaData parentMetaData) {
        Policy policy = policies.getPolicy(idReference.toString(),
                                                   type, constraints);

        if (policy == null)
            return new PolicyFinderResult();
        else
            return new PolicyFinderResult(policy);
    }

}
