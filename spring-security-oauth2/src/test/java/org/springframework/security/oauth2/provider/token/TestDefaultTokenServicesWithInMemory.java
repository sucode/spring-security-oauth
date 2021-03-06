package org.springframework.security.oauth2.provider.token;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.security.oauth2.provider.BaseClientDetails;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.DefaultAuthorizationRequest;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Ryan Heaton
 * @author Dave Syer
 * 
 */
public class TestDefaultTokenServicesWithInMemory extends AbstractTestDefaultTokenServices {

	private InMemoryTokenStore tokenStore;

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void testExpiredToken() throws Exception {
		OAuth2Authentication expectedAuthentication = new OAuth2Authentication(new DefaultAuthorizationRequest("id",
				Collections.singleton("read")), new TestAuthentication("test2", false));
		DefaultOAuth2AccessToken firstAccessToken = (DefaultOAuth2AccessToken) getTokenServices().createAccessToken(
				expectedAuthentication);
		// Make it expire (and rely on mutable state in volatile token store)
		firstAccessToken.setExpiration(new Date(System.currentTimeMillis() - 1000));
		expected.expect(InvalidTokenException.class);
		expected.expectMessage("expired");
		getTokenServices().loadAuthentication(firstAccessToken.getValue());
	}

	@Test
	public void testExpiredRefreshToken() throws Exception {
		OAuth2Authentication expectedAuthentication = new OAuth2Authentication(new DefaultAuthorizationRequest("id",
				Collections.singleton("read")), new TestAuthentication("test2", false));
		DefaultOAuth2AccessToken firstAccessToken = (DefaultOAuth2AccessToken) getTokenServices().createAccessToken(
				expectedAuthentication);
		assertNotNull(firstAccessToken.getRefreshToken());
		// Make it expire (and rely on mutable state in volatile token store)
		ReflectionTestUtils.setField(firstAccessToken.getRefreshToken(), "expiration",
				new Date(System.currentTimeMillis() - 1000));
		firstAccessToken.setExpiration(new Date(System.currentTimeMillis() - 1000));
		expected.expect(InvalidTokenException.class);
		expected.expectMessage("refresh token (expired)");
		getTokenServices().refreshAccessToken(firstAccessToken.getRefreshToken().getValue(), new DefaultAuthorizationRequest("id", null));
	}

	@Test
	public void testDifferentRefreshTokenMaintainsState() throws Exception {
		// create access token
		getTokenServices().setAccessTokenValiditySeconds(1);
		getTokenServices().setClientDetailsService(new ClientDetailsService() {
			public ClientDetails loadClientByClientId(String clientId) throws OAuth2Exception {
				BaseClientDetails client = new BaseClientDetails();
				client.setAccessTokenValiditySeconds(1);
				client.setAuthorizedGrantTypes(Arrays.asList("authorization_code", "refresh_token"));
				return client;
			}
		});
		OAuth2Authentication expectedAuthentication = new OAuth2Authentication(new DefaultAuthorizationRequest("id",
				Collections.singleton("read")), new TestAuthentication("test2", false));
		DefaultOAuth2AccessToken firstAccessToken = (DefaultOAuth2AccessToken) getTokenServices().createAccessToken(
				expectedAuthentication);
		OAuth2RefreshToken expectedExpiringRefreshToken = firstAccessToken.getRefreshToken();
		// Make it expire (and rely on mutable state in volatile token store)
		firstAccessToken.setExpiration(new Date(System.currentTimeMillis() - 1000));
		// create another access token
		OAuth2AccessToken secondAccessToken = getTokenServices().createAccessToken(expectedAuthentication);
		assertFalse("The new access token should be different",
				firstAccessToken.getValue().equals(secondAccessToken.getValue()));
		assertEquals("The new access token should have the same refresh token",
				expectedExpiringRefreshToken.getValue(), secondAccessToken.getRefreshToken().getValue());
		// refresh access token with refresh token
		getTokenServices().refreshAccessToken(expectedExpiringRefreshToken.getValue(),
				expectedAuthentication.getAuthorizationRequest());
		assertEquals(1, getAccessTokenCount());
	}

	@Test
	public void testNoRefreshTokenIfNotAuthorized() throws Exception {
		// create access token
		getTokenServices().setAccessTokenValiditySeconds(1);
		getTokenServices().setClientDetailsService(new ClientDetailsService() {
			public ClientDetails loadClientByClientId(String clientId) throws OAuth2Exception {
				BaseClientDetails client = new BaseClientDetails();
				client.setAccessTokenValiditySeconds(1);
				client.setAuthorizedGrantTypes(Arrays.asList("authorization_code"));
				return client;
			}
		});
		OAuth2Authentication expectedAuthentication = new OAuth2Authentication(new DefaultAuthorizationRequest("id",
				Collections.singleton("read")), new TestAuthentication("test2", false));
		DefaultOAuth2AccessToken token = (DefaultOAuth2AccessToken) getTokenServices().createAccessToken(
				expectedAuthentication);
		assertNull(token.getRefreshToken());
	}

	@Override
	protected TokenStore createTokenStore() {
		tokenStore = new InMemoryTokenStore();
		return tokenStore;
	}

	@Override
	protected int getAccessTokenCount() {
		return tokenStore.getAccessTokenCount();
	}

	@Override
	protected int getRefreshTokenCount() {
		return tokenStore.getRefreshTokenCount();
	}

}
