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
 * Copyright 2017-2020 ForgeRock AS.
 */
package io.daley.am.sqrl.oauth.node;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.forgerock.oauth.OAuthClientConfiguration;
import org.forgerock.oauth.clients.oauth2.OAuth2ClientConfiguration;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.OutputState;
import org.forgerock.openam.auth.nodes.oauth.ProfileNormalizer;
import org.forgerock.openam.auth.nodes.oauth.SocialOAuth2Helper;
import org.forgerock.openam.identity.idm.IdentityUtils;
import org.forgerock.openam.sm.annotations.adapters.Password;
import org.forgerock.openam.sm.validation.URLValidator;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.*;
import static org.forgerock.openam.auth.nodes.oauth.AbstractSocialAuthLoginNode.SocialAuthOutcome.NO_ACCOUNT;
import static org.forgerock.openam.auth.nodes.oauth.SocialOAuth2Helper.DEFAULT_OAUTH2_SCOPE_DELIMITER;
import static org.forgerock.openam.auth.nodes.oauth.SocialOAuth2Helper.USER_INFO_SHARED_STATE_KEY;

import org.forgerock.openam.auth.nodes.oauth.AbstractSocialAuthLoginNode;

/**
 * SQRL OAUTH Node.
 */
@Node.Metadata(outcomeProvider = AbstractSocialAuthLoginNode.SocialAuthOutcomeProvider.class,
        configClass = SqrlOAuthNode.SQRLOAuth2Config.class, tags = {"social", "federation"})
public class SqrlOAuthNode extends AbstractSocialAuthLoginNode {

    /**
     * The node config with default values for sqrl.
     */
    public interface SQRLOAuth2Config extends AbstractSocialAuthLoginNode.Config {

        /**
         * the client id.
         * @return the client id
         */
        @Attribute(order = 100, validators = {RequiredValueValidator.class})
        String clientId();

        /**
         * The client secret.
         * @return the client secret
         */
        @Attribute(order = 200, validators = {RequiredValueValidator.class})
        @Password
        char[] clientSecret();

        /**
         * The authorization endpoint.
         * @return The authorization endpoint.
         */
        @Attribute(order = 300, validators = {RequiredValueValidator.class, URLValidator.class})
        default String authorizeEndpoint() {
            return "https://sqrloauth.com/OAuth/Auth";
        }

        /**
         * The token endpoint.
         * @return The token endpoint.
         */
        @Attribute(order = 400, validators = {RequiredValueValidator.class, URLValidator.class})
        default String tokenEndpoint() {
            return "https://sqrloauth.com/OAuth/Token";
        }

        /**
         * The userinfo endpoint.
         * @return the userinfo endpoint.
         */
        @Attribute(order = 500, validators = {RequiredValueValidator.class, URLValidator.class})
        default String userInfoEndpoint() {
            return "https://sqrloauth.com/Auth/GetUserInfo";
        }

        /**
         * The scopes to request.
         * @return the scopes.
         */
        @Attribute(order = 600, validators = {RequiredValueValidator.class})
        default String scopeString() {
            return "All";
        }

        /**
         * The URI the AS will redirect to.
         * @return the redirect URI
         */
        @Attribute(order = 700, validators = {RequiredValueValidator.class, URLValidator.class})
        default String redirectURI() {
            return getServerURL();
        }

        /**
         * The provider. (useful if using IDM)
         * @return the provider.
         */
        @Attribute(order = 800)
        default String provider() {
            return "SQRL";
        }

        /**
         * The authentication id key.
         * @return teh authentication id key.
         */
        @Attribute(order = 900, validators = {RequiredValueValidator.class})
        default String authenticationIdKey() {
            return "fullName";
        }

        /**
         * Tells if oauth2 must identify via basic header or not.
         * @return true to authenticate via basic header, false otherwise.
         */
        @Attribute(order = 1000)
        default boolean basicAuth() {
            return true;
        }

        /**
         * The account povider class.
         * @return The account povider class.
         */
        @Attribute(order = 1100, validators = {RequiredValueValidator.class})
        default String cfgAccountProviderClass() {
            return "org.forgerock.openam.authentication.modules.common.mapping.DefaultAccountProvider";
        }

        /**
         * The account mapper class.
         * @return the account mapper class.
         */
        @Attribute(order = 1200, validators = {RequiredValueValidator.class})
        default String cfgAccountMapperClass() {
            return "org.forgerock.openam.authentication.modules.common.mapping.JsonAttributeMapper|*|sqrl-";
        }

        /**
         * The attribute mapping classes.
         * @return the attribute mapping classes.
         */
        @Attribute(order = 1300, validators = {RequiredValueValidator.class})
        default Set<String> cfgAttributeMappingClasses() {
            return singleton("org.forgerock.openam.authentication.modules.common.mapping."
                    + "JsonAttributeMapper|iplanet-am-user-alias-list|sqrl-");
        }

        /**
         * The account mapper configuration.
         * @return the account mapper configuration.
         */
        @Attribute(order = 1400, validators = {RequiredValueValidator.class})
        default Map<String, String> cfgAccountMapperConfiguration() {
            return singletonMap("userID", "iplanet-am-user-alias-list");
        }

        /**
         * The attribute mapping configuration.
         * @return the attribute mapping configuration
         */
        @Attribute(order = 1500, validators = {RequiredValueValidator.class})
        default Map<String, String> cfgAttributeMappingConfiguration() {
            final Map<String, String> attributeMappingConfiguration = new HashMap<>();
            attributeMappingConfiguration.put("userID", "iplanet-am-user-alias-list");
            attributeMappingConfiguration.put("firstName", "givenName");
            attributeMappingConfiguration.put("lastName", "sn");
            attributeMappingConfiguration.put("email", "mail");
            attributeMappingConfiguration.put("fullName", "cn");
            return attributeMappingConfiguration;
        }

        /**
         * Specifies if the user attributes must be saved in session.
         * @return true to save the user attribute into the session, false otherwise.
         */
        @Attribute(order = 1600)
        default boolean saveUserAttributesToSession() {
            return true;
        }

        /**
         * Specify if the mixup mitigation must be activated.
         * The mixup mitigation add an extra level of security by checking the client_id and iss coming from the
         * authorizeEndpoint response.
         *
         * @return true to activate it , false otherwise
         */
        @Attribute(order = 1700)
        default boolean cfgMixUpMitigation() {
            return false;
        }

        /**
         * The issuer. Must be specify to use the mixup mitigation.
         * @return the issuer.
         */
        @Attribute(order = 1800)
        default String issuer() {
            return "";
        }
    }


    @Inject
    public SqrlOAuthNode(@Assisted SqrlOAuthNode.SQRLOAuth2Config config, SocialOAuth2Helper authModuleHelper,
                         ProfileNormalizer profileNormalizer, IdentityUtils identityUtils) throws NodeProcessException {
        super(config, authModuleHelper, authModuleHelper.newOAuthClient(getOAuthClientConfiguration(config)),
                profileNormalizer, identityUtils);
    }

    private static OAuthClientConfiguration getOAuthClientConfiguration(SqrlOAuthNode.SQRLOAuth2Config config) {
        return OAuth2ClientConfiguration.oauth2ClientConfiguration()
                .withClientId(config.clientId())
                .withClientSecret(new String(config.clientSecret()))
                .withAuthorizationEndpoint(config.authorizeEndpoint())
                .withTokenEndpoint(config.tokenEndpoint())
                .withScope(Collections.singletonList(config.scopeString()))
                .withScopeDelimiter(DEFAULT_OAUTH2_SCOPE_DELIMITER)
                .withBasicAuth(config.basicAuth())
                .withUserInfoEndpoint(config.userInfoEndpoint())
                .withRedirectUri(URI.create(config.redirectURI()))
                .withProvider(config.provider())
                .withAuthenticationIdKey(config.authenticationIdKey()).build();
    }

    @Override
    public InputState[] getInputs() {
        return new InputState[] {
                new InputState(REALM)
        };
    }

    @Override
    public OutputState[] getOutputs() {
        return new OutputState[] {
                new OutputState(USERNAME, singletonMap("ACCOUNT_EXISTS", true)),
                new OutputState(USER_INFO_SHARED_STATE_KEY, singletonMap(NO_ACCOUNT.toString(), true)),
                new OutputState(EMAIL_ADDRESS, singletonMap(NO_ACCOUNT.toString(), true))
        };
    }
}
