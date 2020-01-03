/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017-2018 ForgeRock AS.
 */


package io.daley.am.sqrlNode;

import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import com.sun.identity.authentication.spi.AuthLoginException;
import com.sun.identity.authentication.spi.InvalidPasswordException;
import com.sun.identity.idm.*;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.idrepo.ldap.IdentityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.*;

/**
 * A node that checks to see if zero-page login headers have specified username and whether that username is in a group
 * permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider  = AbstractDecisionNode.OutcomeProvider.class,
               configClass      = sqrlNode.Config.class)
public class sqrlNode extends AbstractDecisionNode {

    private final Pattern DN_PATTERN = Pattern.compile("^[a-zA-Z0-9]=([^,]+),");
    private final Logger logger = LoggerFactory.getLogger(sqrlNode.class);
    private final Config config;
    private final Realm realm;

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        NameCallback nameCallback = new NameCallback("notused");
        nameCallback.setName(context.sharedState.get(USERNAME).asString());

        Callback[] callbacks = new Callback[]{nameCallback};
        Action.ActionBuilder action = goTo(true);

        if (!nameCallback.getName().equalsIgnoreCase("alun")) {
            action = goTo(false).withErrorMessage("Only Alun is allowed in");
        }

        return action.replaceSharedState(context.sharedState.copy())
                .replaceTransientState(context.transientState.copy()).build();
    }

    /**
     * Configuration for the node.
     */
    public interface Config {
        /**
         * Some attribute.
         */
        @Attribute(order = 100)
        default String noDefaultAttribute() {
            return "Alun";
        }
    }


    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of other classes
     * from the plugin.
     *
     * @param config The service config.
     * @param realm The realm the node is in.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public sqrlNode(@Assisted Config config, @Assisted Realm realm) throws NodeProcessException {
        this.config = config;
        this.realm = realm;
    }

    /*
    public Action processOld(TreeContext context) throws NodeProcessException {
        boolean hasUsername = context.request.headers.containsKey(config.usernameHeader());
        boolean hasPassword = context.request.headers.containsKey(config.passwordHeader());

        if (!hasUsername || !hasPassword) {
            return goTo(false).build();
        }

        String username = context.request.headers.get(config.usernameHeader()).get(0);
        String password = context.request.headers.get(config.passwordHeader()).get(0);
        AMIdentity userIdentity = IdUtils.getIdentity(username, realm.asDN());
        try {
            if (userIdentity != null && userIdentity.isExists() && userIdentity.isActive()
                    && isMemberOfGroup(userIdentity, config.groupName())) {
                return goTo(true)
                        .replaceSharedState(context.sharedState.copy().put(USERNAME, username))
                        .replaceTransientState(context.transientState.copy().put(PASSWORD, password))
                        .build();
            }
        } catch (IdRepoException | SSOException e) {
            logger.warn("Error locating user '{}' ", username, e);
        }
        return goTo(false).build();
    }
    */



    private boolean isMemberOfGroup(AMIdentity userIdentity, String groupName) {
        try {
            Set<String> userGroups = userIdentity.getMemberships(IdType.GROUP);
            for (String group : userGroups) {
                if (groupName.equals(group)) {
                    return true;
                }
                Matcher dnMatcher = DN_PATTERN.matcher(group);
                if (dnMatcher.find() && dnMatcher.group(1).equals(groupName)) {
                    return true;
                }
            }
        } catch (IdRepoException | SSOException e) {
            logger.warn("Could not load groups for user {}", userIdentity);
        }
        return false;
    }
}
